package com.waylo.paymentservice.service;

import com.waylo.paymentservice.config.PayoutSettings;
import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.LedgerAccount;
import com.waylo.paymentservice.domain.PartnerBankAccount;
import com.waylo.paymentservice.domain.PartnerPayout;
import com.waylo.paymentservice.domain.PayoutActorType;
import com.waylo.paymentservice.domain.PayoutEvent;
import com.waylo.paymentservice.domain.PayoutLedgerEntry;
import com.waylo.paymentservice.domain.PayoutLedgerType;
import com.waylo.paymentservice.domain.PayoutStatus;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.event.PayoutFailedEvent;
import com.waylo.paymentservice.event.PayoutPaidEvent;
import com.waylo.paymentservice.event.PaymentEventPublisher;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.BadRequestException;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.exception.PayoutConstraints;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.PartnerBankAccountRepository;
import com.waylo.paymentservice.repository.PartnerPayoutRepository;
import com.waylo.paymentservice.repository.PayoutEventRepository;
import com.waylo.paymentservice.repository.PayoutLedgerRepository;
import com.waylo.paymentservice.repository.PayoutWalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Partner payouts (PAYOUT-CONTRACT.md sec. 3). Every mutation takes the (organization, currency) wallet lock FIRST and the
 * payout row lock second, computes the balance under the lock, and writes the status change, the payout_events row, the
 * ledger rows and the outbox event in one transaction. No money moves: an admin sends the bank transfer outside the system.
 */
@Service
public class PayoutService {

    private final PayoutAccess access;
    private final PartnerPayoutRepository payouts;
    private final PayoutLedgerRepository ledger;
    private final PayoutEventRepository events;
    private final PartnerBankAccountRepository bankAccounts;
    private final PayoutWalletRepository wallets;
    private final PayoutBalanceService balances;
    private final PayoutSettings settings;
    private final PaymentEventPublisher publisher;

    public PayoutService(PayoutAccess access, PartnerPayoutRepository payouts, PayoutLedgerRepository ledger,
                         PayoutEventRepository events, PartnerBankAccountRepository bankAccounts, PayoutWalletRepository wallets,
                         PayoutBalanceService balances, PayoutSettings settings, PaymentEventPublisher publisher) {
        this.access = access;
        this.payouts = payouts;
        this.ledger = ledger;
        this.events = events;
        this.bankAccounts = bankAccounts;
        this.wallets = wallets;
        this.balances = balances;
        this.settings = settings;
        this.publisher = publisher;
    }

    /** created=false means an idempotent replay (HTTP 200 instead of 201). */
    public record RequestResult(PayoutDtos.PayoutResponse payout, boolean created) {}

    // ------------------------------------------------------------------ partner reads

    @Transactional(readOnly = true)
    public PayoutDtos.BalanceResponse balance(UUID userId, UUID organizationId) {
        var decision = access.requireRead(userId, organizationId);
        boolean owner = PayoutAccess.isOwner(decision);
        boolean active = access.isActive(organizationId);
        return balances.balance(organizationId, owner, active, Instant.now());
    }

    @Transactional(readOnly = true)
    public PayoutDtos.PageResponse<PayoutDtos.PayoutResponse> list(UUID userId, UUID organizationId, PayoutStatus status, int page, int size) {
        access.requireRead(userId, organizationId);
        Page<PartnerPayout> p = payouts.findForOrganization(organizationId, status, pageRequest(page, size));
        return new PayoutDtos.PageResponse<>(p.getContent().stream().map(PayoutService::toResponse).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PayoutDtos.PayoutResponse get(UUID userId, UUID organizationId, UUID payoutId) {
        access.requireRead(userId, organizationId);
        return toResponse(payouts.findByIdAndOrganizationId(payoutId, organizationId).orElseThrow(() -> notFound(payoutId)));
    }

    // ------------------------------------------------------------------ P1 request

    @Transactional
    public RequestResult request(UUID userId, String userEmail, UUID organizationId, String idempotencyKey, String currency, BigDecimal amount) {
        access.requireOwner(userId, organizationId);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 150) {
            throw new BadRequestException("VALIDATION_ERROR", "Idempotency-Key header is required (1..150 chars)");
        }
        BigDecimal amt = exactMoney(amount);
        BigDecimal minimum = settings.minimumFor(currency);
        if (minimum == null) throw new BusinessRuleException("PAYOUT_CURRENCY_NOT_SUPPORTED", "Payouts are not available in " + currency);
        if (amt.compareTo(minimum) < 0) {
            throw new BusinessRuleException("PAYOUT_BELOW_MINIMUM", "Amount is below the minimum payout", Map.of("minimum", minimum));
        }

        Optional<PartnerBankAccount> accountRow = bankAccounts.findByOrganizationId(organizationId);
        String hash = accountRow.map(a -> requestHash(currency, amt, a.getId())).orElse(null);
        if (hash != null) {
            Optional<RequestResult> replay = replay(organizationId, idempotencyKey, hash);
            if (replay.isPresent()) return replay.get();
        }

        access.requireActive(organizationId);
        PartnerBankAccount account = accountRow.filter(a -> a.getStatus() != BankAccountStatus.DISABLED)
                .orElseThrow(() -> new BusinessRuleException("BANK_ACCOUNT_REQUIRED", "A verified bank account is required"));
        if (account.getStatus() != BankAccountStatus.VERIFIED) {
            throw new BusinessRuleException("BANK_ACCOUNT_NOT_VERIFIED", "The bank account is not verified yet");
        }

        wallets.lock(organizationId, currency);
        Optional<RequestResult> replay = replay(organizationId, idempotencyKey, hash);
        if (replay.isPresent()) return replay.get();

        Optional<PartnerPayout> open = payouts.findFirstByOrganizationIdAndCurrencyAndStatusIn(organizationId, currency, PayoutStatus.OPEN);
        if (open.isPresent()) {
            throw new ConflictException("OPEN_PAYOUT_EXISTS", "An open payout already exists for this currency",
                    Map.of("openPayoutId", open.get().getId().toString()));
        }
        PayoutBalanceService.Raw raw = balances.rawFor(organizationId, currency, Instant.now());
        BigDecimal payable = raw.payable();
        if (amt.compareTo(payable) > 0) {
            throw new BusinessRuleException("INSUFFICIENT_PAYABLE_BALANCE", "Amount exceeds the payable balance", Map.of("payable", payable));
        }

        Instant now = Instant.now();
        PartnerPayout p = new PartnerPayout();
        p.setId(UUID.randomUUID());
        p.setOrganizationId(organizationId);
        p.setCurrency(currency);
        p.setAmount(amt);
        p.setStatus(PayoutStatus.REQUESTED);
        p.setIdempotencyKey(idempotencyKey);
        p.setRequestHash(hash);
        p.setBankAccountId(account.getId());
        p.setBankAccountVersion(account.getVersion());
        p.setIbanCiphertext(account.getIbanCiphertext());
        p.setIbanLast4(account.getIbanLast4());
        p.setAccountHolderName(account.getAccountHolderName());
        p.setRequestedBy(userId);
        p.setRequestedByEmail(userEmail);
        p.setRequestedAt(now);
        p.setUpdatedAt(now);
        flush(() -> payouts.saveAndFlush(p));
        flush(() -> ledger.saveAndFlush(entry(organizationId, p.getId(), PayoutLedgerType.RESERVE, amt.negate(), null,
                BigDecimal.ZERO, BigDecimal.ZERO, currency, null, null, null, userId, now)));
        events.save(new PayoutEvent(p.getId(), null, PayoutStatus.REQUESTED, userId, PayoutActorType.PARTNER, null, null, now));
        return new RequestResult(toResponse(p), true);
    }

    private Optional<RequestResult> replay(UUID organizationId, String key, String hash) {
        Optional<PartnerPayout> existing = payouts.findByOrganizationIdAndIdempotencyKey(organizationId, key);
        if (existing.isEmpty()) return Optional.empty();
        if (!existing.get().getRequestHash().trim().equals(hash)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used with a different request");
        }
        return Optional.of(new RequestResult(toResponse(existing.get()), false));
    }

    // ------------------------------------------------------------------ partner cancel (P6)

    @Transactional
    public PayoutDtos.PayoutResponse partnerCancel(UUID userId, UUID organizationId, UUID payoutId) {
        access.requireOwner(userId, organizationId);
        return toResponse(cancel(payoutId, organizationId, userId, PayoutActorType.PARTNER, null, null));
    }

    // ------------------------------------------------------------------ admin transitions

    @Transactional
    public PayoutDtos.PayoutResponse approve(UUID adminId, String role, UUID payoutId, Long expectedVersion) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerPayout p = lock(payoutId, null);
        if (p.getStatus() == PayoutStatus.APPROVED) return toResponse(p);
        guard(p, PayoutStatus.APPROVED, PayoutActorType.ADMIN, expectedVersion);
        requireBankAccountUnchanged(p);
        Instant now = Instant.now();
        PayoutStatus from = p.getStatus();
        p.setStatus(PayoutStatus.APPROVED);
        p.setApprovedBy(adminId);
        p.setApprovedAt(now);
        finish(p, from, adminId, PayoutActorType.ADMIN, null, null, now);
        return toResponse(p);
    }

    @Transactional
    public PayoutDtos.PayoutResponse startProcessing(UUID adminId, String role, UUID payoutId, Long expectedVersion) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerPayout p = lock(payoutId, null);
        if (p.getStatus() == PayoutStatus.PROCESSING) return toResponse(p);
        guard(p, PayoutStatus.PROCESSING, PayoutActorType.ADMIN, expectedVersion);
        Instant now = Instant.now();
        PayoutStatus from = p.getStatus();
        p.setStatus(PayoutStatus.PROCESSING);
        p.setProcessingAt(now);
        finish(p, from, adminId, PayoutActorType.ADMIN, null, null, now);
        return toResponse(p);
    }

    @Transactional
    public PayoutDtos.PayoutResponse markPaid(UUID adminId, String role, UUID payoutId, String reference, Instant paidAt, Long expectedVersion) {
        PayoutAccess.requireAdmin(adminId, role);
        if (reference == null || reference.trim().length() < 3 || reference.trim().length() > 100) {
            throw new BadRequestException("VALIDATION_ERROR", "reference is required (3..100 chars)");
        }
        String ref = reference.trim();
        Instant now = Instant.now();
        if (paidAt != null && paidAt.isAfter(now)) throw new BadRequestException("VALIDATION_ERROR", "paidAt cannot be in the future");
        PartnerPayout p = lock(payoutId, null);
        if (p.getStatus() == PayoutStatus.PAID) return toResponse(p);
        guard(p, PayoutStatus.PAID, PayoutActorType.ADMIN, expectedVersion);
        requireBankAccountUnchanged(p);
        if (settings.isRequireDifferentAdmins() && adminId.equals(p.getApprovedBy())) {
            throw new AccessDeniedException("SAME_ADMIN_NOT_ALLOWED", "A different administrator must mark this payout as paid");
        }
        if (payouts.existsByPaymentReference(ref)) {
            throw new ConflictException("PAYMENT_REFERENCE_IN_USE", "This bank reference is already used by another payout");
        }
        PayoutStatus from = p.getStatus();
        Instant when = paidAt == null ? now : paidAt;
        p.setStatus(PayoutStatus.PAID);
        p.setPaidBy(adminId);
        p.setPaidAt(when);
        p.setPaymentReference(ref);
        UUID group = UUID.randomUUID();
        flush(() -> ledger.save(new PayoutLedgerEntry(group, p.getOrganizationId(), p.getId(), PayoutLedgerType.PAID, BigDecimal.ZERO.setScale(2),
                LedgerAccount.SUPPLIER_PAYABLE, p.getAmount(), BigDecimal.ZERO.setScale(2), p.getCurrency(), null, null, ref, adminId, now)));
        flush(() -> ledger.save(new PayoutLedgerEntry(group, p.getOrganizationId(), p.getId(), PayoutLedgerType.PAID, BigDecimal.ZERO.setScale(2),
                LedgerAccount.SUPPLIER_PAID, BigDecimal.ZERO.setScale(2), p.getAmount(), p.getCurrency(), null, null, ref, adminId, now)));
        finish(p, from, adminId, PayoutActorType.ADMIN, null, ref, now);
        publisher.publishPayoutPaid(new PayoutPaidEvent(p.getId(), p.getOrganizationId(), p.getRequestedBy(), p.getRequestedByEmail(),
                p.getAmount(), p.getCurrency(), p.getIbanLast4(), ref, when));
        return toResponse(p);
    }

    @Transactional
    public PayoutDtos.PayoutResponse markFailed(UUID adminId, String role, UUID payoutId, String reason, Long expectedVersion) {
        PayoutAccess.requireAdmin(adminId, role);
        String why = requireReason(reason);
        PartnerPayout p = lock(payoutId, null);
        if (p.getStatus() == PayoutStatus.FAILED) return toResponse(p);
        guard(p, PayoutStatus.FAILED, PayoutActorType.ADMIN, expectedVersion);
        Instant now = Instant.now();
        PayoutStatus from = p.getStatus();
        p.setStatus(PayoutStatus.FAILED);
        p.setFailureReason(why);
        releaseReserve(p, adminId, now);
        finish(p, from, adminId, PayoutActorType.ADMIN, why, null, now);
        publisher.publishPayoutFailed(new PayoutFailedEvent(p.getId(), p.getOrganizationId(), p.getRequestedBy(), p.getRequestedByEmail(),
                p.getAmount(), p.getCurrency(), p.getIbanLast4(), why));
        return toResponse(p);
    }

    /** P6 (REQUESTED) and P7 (APPROVED), the admin "reject" action. A reason is required. */
    @Transactional
    public PayoutDtos.PayoutResponse adminCancel(UUID adminId, String role, UUID payoutId, String reason, Long expectedVersion) {
        PayoutAccess.requireAdmin(adminId, role);
        return toResponse(cancel(payoutId, null, adminId, PayoutActorType.ADMIN, requireReason(reason), expectedVersion));
    }

    private PartnerPayout cancel(UUID payoutId, UUID organizationScope, UUID actorId, PayoutActorType actor, String reason, Long expectedVersion) {
        PartnerPayout p = lock(payoutId, organizationScope);
        if (p.getStatus() == PayoutStatus.CANCELLED) return p;
        guard(p, PayoutStatus.CANCELLED, actor, expectedVersion);
        Instant now = Instant.now();
        PayoutStatus from = p.getStatus();
        p.setStatus(PayoutStatus.CANCELLED);
        p.setCancelledBy(actorId);
        p.setCancelledByType(actor);
        p.setCancelledAt(now);
        p.setCancelReason(reason);
        releaseReserve(p, actorId, now);
        finish(p, from, actorId, actor, reason, null, now);
        return p;
    }

    // ------------------------------------------------------------------ admin reads and adjustments

    @Transactional(readOnly = true)
    public PayoutDtos.PageResponse<PayoutDtos.PayoutResponse> adminQueue(UUID adminId, String role, List<PayoutStatus> statuses,
                                                                         UUID organizationId, String currency, int page, int size) {
        PayoutAccess.requireAdmin(adminId, role);
        List<PayoutStatus> filter = statuses == null || statuses.isEmpty() ? PayoutStatus.OPEN : statuses;
        Page<PartnerPayout> p = payouts.findQueue(filter, organizationId, currency, pageRequest(page, size));
        return new PayoutDtos.PageResponse<>(p.getContent().stream().map(PayoutService::toResponse).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PayoutDtos.AdminPayoutDetail adminDetail(UUID adminId, String role, UUID payoutId) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerPayout p = payouts.findById(payoutId).orElseThrow(() -> notFound(payoutId));
        List<PayoutDtos.PayoutEventView> history = events.findByPayoutIdOrderByOccurredAtAscIdAsc(payoutId).stream()
                .map(e -> new PayoutDtos.PayoutEventView(e.getFromStatus(), e.getToStatus(), e.getActorType(), e.getReason(), e.getReference(), e.getOccurredAt())).toList();
        return new PayoutDtos.AdminPayoutDetail(toResponse(p), history, balances.itemFor(p.getOrganizationId(), p.getCurrency(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public PayoutDtos.AdminBalanceResponse adminBalance(UUID adminId, String role, UUID organizationId) {
        PayoutAccess.requireAdmin(adminId, role);
        return balances.adminBalance(organizationId, Instant.now());
    }

    /** Admin DEBIT or CREDIT of the payable balance (refund or chargeback after release). Idempotent per (organization, key). */
    @Transactional
    public PayoutDtos.AdjustmentResponse adjust(UUID adminId, String role, UUID organizationId, PayoutDtos.AdjustmentRequest r) {
        PayoutAccess.requireAdmin(adminId, role);
        BigDecimal amt = exactMoney(r.amount());
        String reason = requireReason(r.reason());
        wallets.lock(organizationId, r.currency());
        boolean debit = r.direction() == PayoutDtos.AdjustmentRequest.Direction.DEBIT;
        Optional<PayoutLedgerEntry> existing = ledger.findFirstByOrganizationIdAndIdempotencyKey(organizationId, r.idempotencyKey());
        if (existing.isPresent()) {
            PayoutLedgerEntry e = existing.get();
            boolean same = e.getCurrency().trim().equals(r.currency()) && e.getPayableDelta().abs().compareTo(amt) == 0
                    && (e.getEntryType() == PayoutLedgerType.ADJUSTMENT_DEBIT) == debit;
            if (!same) throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used with a different request");
            return adjustmentResponse(e, r.direction(), amt);
        }
        Instant now = Instant.now();
        PayoutLedgerEntry e = entry(organizationId, null, debit ? PayoutLedgerType.ADJUSTMENT_DEBIT : PayoutLedgerType.ADJUSTMENT_CREDIT,
                debit ? amt.negate() : amt, null, BigDecimal.ZERO, BigDecimal.ZERO, r.currency(), r.orderItemId(), r.idempotencyKey(), reason, adminId, now);
        flush(() -> ledger.saveAndFlush(e));
        return adjustmentResponse(e, r.direction(), amt);
    }

    // ------------------------------------------------------------------ internals

    /** Wallet lock first, then the payout row lock. A payout of another organization is a 404 on partner paths. */
    private PartnerPayout lock(UUID payoutId, UUID organizationScope) {
        List<Object[]> key = payouts.findWalletKey(payoutId);
        if (key.isEmpty()) throw notFound(payoutId);
        UUID org = (UUID) key.get(0)[0];
        String currency = String.valueOf(key.get(0)[1]).trim();
        if (organizationScope != null && !organizationScope.equals(org)) throw notFound(payoutId);
        wallets.lock(org, currency);
        return payouts.findByIdForUpdate(payoutId).orElseThrow(() -> notFound(payoutId));
    }

    private void guard(PartnerPayout p, PayoutStatus to, PayoutActorType actor, Long expectedVersion) {
        if (!PayoutStateMachine.isAllowed(p.getStatus(), to, actor)) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "Payout cannot move from " + p.getStatus() + " to " + to,
                    Map.of("from", p.getStatus().name(), "to", to.name()));
        }
        if (expectedVersion != null && expectedVersion != p.getVersion().longValue()) {
            throw new ConflictException("VERSION_CONFLICT", "The payout was modified, reload and retry");
        }
    }

    private void requireBankAccountUnchanged(PartnerPayout p) {
        PartnerBankAccount a = bankAccounts.findById(p.getBankAccountId()).orElse(null);
        if (a == null || !a.getVersion().equals(p.getBankAccountVersion())) {
            throw new ConflictException("BANK_ACCOUNT_CHANGED", "The bank account changed after the payout was requested, cancel and request again");
        }
    }

    private void releaseReserve(PartnerPayout p, UUID actor, Instant now) {
        flush(() -> ledger.save(entry(p.getOrganizationId(), p.getId(), PayoutLedgerType.RESERVE_RELEASE, p.getAmount(), null,
                BigDecimal.ZERO, BigDecimal.ZERO, p.getCurrency(), null, null, null, actor, now)));
    }

    private void finish(PartnerPayout p, PayoutStatus from, UUID actor, PayoutActorType actorType, String reason, String reference, Instant now) {
        p.setUpdatedAt(now);
        flush(() -> payouts.saveAndFlush(p));
        events.save(new PayoutEvent(p.getId(), from, p.getStatus(), actor, actorType, reason, reference, now));
    }

    private static PayoutLedgerEntry entry(UUID org, UUID payoutId, PayoutLedgerType type, BigDecimal delta, LedgerAccount account,
                                           BigDecimal debit, BigDecimal credit, String currency, UUID orderItemId, String key,
                                           String reference, UUID by, Instant now) {
        return new PayoutLedgerEntry(UUID.randomUUID(), org, payoutId, type, delta, account, debit, credit, currency, orderItemId, key, reference, by, now);
    }

    /** Runs a write and turns known unique-constraint violations into business conflicts (never leaks SQL text). */
    private static void flush(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException ex) {
            throw PayoutConstraints.translate(ex);
        }
    }

    private static BigDecimal exactMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new BadRequestException("VALIDATION_ERROR", "amount must be positive");
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BadRequestException("VALIDATION_ERROR", "amount can have at most 2 decimals");
        }
    }

    private static String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5 || r.length() > 500) throw new BadRequestException("VALIDATION_ERROR", "reason is required (5..500 chars)");
        return r;
    }

    static String requestHash(String currency, BigDecimal amount, UUID bankAccountId) {
        try {
            String canonical = currency + "|" + amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "|" + bankAccountId;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static ResourceNotFoundException notFound(UUID payoutId) {
        return new ResourceNotFoundException("Payout", "id", payoutId);
    }

    private static PayoutDtos.AdjustmentResponse adjustmentResponse(PayoutLedgerEntry e, PayoutDtos.AdjustmentRequest.Direction d, BigDecimal amt) {
        return new PayoutDtos.AdjustmentResponse(e.getId(), e.getOrganizationId(), e.getCurrency().trim(), amt, d, e.getReference(), e.getCreatedAt());
    }

    public static PayoutDtos.PayoutResponse toResponse(PartnerPayout p) {
        return new PayoutDtos.PayoutResponse(p.getId(), p.getOrganizationId(), p.getCurrency().trim(), p.getAmount(), p.getStatus(),
                IbanMask.mask(p.getIbanLast4()), p.getAccountHolderName(), p.getRequestedAt(), p.getApprovedAt(), p.getProcessingAt(),
                p.getPaidAt(), p.getPaymentReference(), p.getFailureReason(), p.getCancelReason(), p.getCancelledByType(),
                p.getVersion() == null ? 0L : p.getVersion());
    }
}
