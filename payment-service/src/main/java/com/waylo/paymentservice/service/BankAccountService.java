package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.BankAccountChange;
import com.waylo.paymentservice.domain.BankAccountChangeType;
import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.PartnerBankAccount;
import com.waylo.paymentservice.domain.PartnerPayout;
import com.waylo.paymentservice.domain.PayoutActorType;
import com.waylo.paymentservice.domain.PayoutStatus;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.BadRequestException;
import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.exception.PayoutConstraints;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.BankAccountChangeRepository;
import com.waylo.paymentservice.repository.PartnerBankAccountRepository;
import com.waylo.paymentservice.repository.PartnerPayoutRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partner bank account (IBAN) lifecycle (PAYOUT-CONTRACT.md sec. 4). The IBAN is stored AES-GCM encrypted, shown only as a
 * mask, and no log line, audit row or event ever carries it. The single exception is the audited admin reveal.
 */
@Service
public class BankAccountService {

    private final PayoutAccess access;
    private final PartnerBankAccountRepository accounts;
    private final BankAccountChangeRepository changes;
    private final PartnerPayoutRepository payouts;
    private final IbanCrypto crypto;

    public BankAccountService(PayoutAccess access, PartnerBankAccountRepository accounts, BankAccountChangeRepository changes,
                              PartnerPayoutRepository payouts, IbanCrypto crypto) {
        this.access = access;
        this.accounts = accounts;
        this.changes = changes;
        this.payouts = payouts;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public PayoutDtos.BankAccountResponse get(UUID userId, UUID organizationId) {
        access.requireRead(userId, organizationId);
        PartnerBankAccount a = accounts.findByOrganizationId(organizationId).filter(x -> x.getStatus() != BankAccountStatus.DISABLED)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "organizationId", organizationId));
        return toResponse(a);
    }

    /** Creates or replaces in place. A change always sends the account back to PENDING_VERIFICATION. */
    @Transactional
    public PayoutDtos.BankAccountResponse put(UUID userId, UUID organizationId, PayoutDtos.BankAccountRequest request) {
        access.requireOwner(userId, organizationId);
        String holder = request.accountHolderName() == null ? "" : request.accountHolderName().trim();
        if (holder.length() < 2 || holder.length() > 140) throw new BadRequestException("VALIDATION_ERROR", "accountHolderName must be 2..140 characters");
        String iban;
        try {
            iban = IbanValidator.validate(request.iban());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_IBAN", "The IBAN is not valid");
        }
        String hash = crypto.hash(iban);
        if (accounts.existsByIbanHashAndStatusNotAndOrganizationIdNot(hash, BankAccountStatus.DISABLED, organizationId)) {
            throw new ConflictException("IBAN_ALREADY_IN_USE", "This IBAN is already registered");
        }
        Instant now = Instant.now();
        PartnerBankAccount existing = accounts.findByOrganizationIdForUpdate(organizationId).orElse(null);
        PartnerBankAccount a = existing != null ? existing : new PartnerBankAccount();
        boolean replaced = existing != null && existing.getStatus() != BankAccountStatus.DISABLED;
        String oldLast4 = replaced ? existing.getIbanLast4() : null;
        String oldHash = replaced ? existing.getIbanHash() : null;
        if (existing == null) {
            a.setId(UUID.randomUUID());
            a.setOrganizationId(organizationId);
            a.setCreatedBy(userId);
            a.setCreatedAt(now);
        }
        a.setAccountHolderName(holder);
        a.setIbanCiphertext(crypto.encrypt(iban));
        a.setIbanLast4(IbanMask.last4(iban));
        a.setIbanHash(hash);
        a.setStatus(BankAccountStatus.PENDING_VERIFICATION);
        a.setVerifiedAt(null);
        a.setVerifiedBy(null);
        a.setRejectReason(null);
        a.setUpdatedBy(userId);
        a.setUpdatedAt(now);
        try {
            accounts.saveAndFlush(a);
        } catch (DataIntegrityViolationException ex) {
            throw PayoutConstraints.translate(ex);
        }
        changes.save(new BankAccountChange(a.getId(), organizationId, replaced ? BankAccountChangeType.REPLACED : BankAccountChangeType.CREATED,
                oldLast4, a.getIbanLast4(), oldHash, hash, userId, PayoutActorType.PARTNER, null, now));
        return toResponse(a);
    }

    @Transactional
    public PayoutDtos.BankAccountResponse delete(UUID userId, UUID organizationId) {
        access.requireOwner(userId, organizationId);
        PartnerBankAccount a = accounts.findByOrganizationIdForUpdate(organizationId).filter(x -> x.getStatus() != BankAccountStatus.DISABLED)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "organizationId", organizationId));
        if (payouts.existsByOrganizationIdAndStatusIn(organizationId, PayoutStatus.OPEN)) {
            throw new ConflictException("OPEN_PAYOUT_EXISTS", "An open payout exists, finish or cancel it first");
        }
        Instant now = Instant.now();
        String last4 = a.getIbanLast4();
        String hash = a.getIbanHash();
        a.setStatus(BankAccountStatus.DISABLED);
        a.setVerifiedAt(null);
        a.setVerifiedBy(null);
        a.setUpdatedBy(userId);
        a.setUpdatedAt(now);
        accounts.saveAndFlush(a);
        changes.save(new BankAccountChange(a.getId(), organizationId, BankAccountChangeType.DISABLED, last4, null, hash, null,
                userId, PayoutActorType.PARTNER, null, now));
        return toResponse(a);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<PayoutDtos.AdminBankAccountResponse> adminList(UUID adminId, String role, BankAccountStatus status, int size) {
        PayoutAccess.requireAdmin(adminId, role);
        BankAccountStatus s = status == null ? BankAccountStatus.PENDING_VERIFICATION : status;
        return accounts.findByStatusOrderByUpdatedAtAsc(s, PageRequest.of(0, Math.min(Math.max(size, 1), 100))).stream()
                .map(BankAccountService::toAdminResponse).toList();
    }

    @Transactional
    public PayoutDtos.AdminBankAccountResponse verify(UUID adminId, String role, UUID accountId) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerBankAccount a = lockPending(accountId);
        if (adminId.equals(a.getUpdatedBy())) {
            throw new AccessDeniedException("SELF_VERIFICATION_NOT_ALLOWED", "You cannot verify a bank account you changed yourself");
        }
        Instant now = Instant.now();
        a.setStatus(BankAccountStatus.VERIFIED);
        a.setVerifiedBy(adminId);
        a.setVerifiedAt(now);
        a.setRejectReason(null);
        a.setUpdatedAt(now);
        accounts.saveAndFlush(a);
        changes.save(new BankAccountChange(a.getId(), a.getOrganizationId(), BankAccountChangeType.VERIFIED, a.getIbanLast4(), a.getIbanLast4(),
                a.getIbanHash(), a.getIbanHash(), adminId, PayoutActorType.ADMIN, null, now));
        return toAdminResponse(a);
    }

    @Transactional
    public PayoutDtos.AdminBankAccountResponse reject(UUID adminId, String role, UUID accountId, String reason) {
        PayoutAccess.requireAdmin(adminId, role);
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5 || why.length() > 500) throw new BadRequestException("VALIDATION_ERROR", "reason is required (5..500 chars)");
        PartnerBankAccount a = lockPending(accountId);
        Instant now = Instant.now();
        a.setStatus(BankAccountStatus.REJECTED);
        a.setRejectReason(why);
        a.setVerifiedAt(null);
        a.setVerifiedBy(null);
        a.setUpdatedAt(now);
        accounts.saveAndFlush(a);
        changes.save(new BankAccountChange(a.getId(), a.getOrganizationId(), BankAccountChangeType.REJECTED, a.getIbanLast4(), a.getIbanLast4(),
                a.getIbanHash(), a.getIbanHash(), adminId, PayoutActorType.ADMIN, why, now));
        return toAdminResponse(a);
    }

    /** The one place the full IBAN leaves the service. Only for APPROVED or PROCESSING payouts, always audited with a REVEALED row. */
    @Transactional
    public PayoutDtos.BankDetailsResponse reveal(UUID adminId, String role, UUID payoutId) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerPayout p = payouts.findById(payoutId).orElseThrow(() -> new ResourceNotFoundException("Payout", "id", payoutId));
        if (p.getStatus() != PayoutStatus.APPROVED && p.getStatus() != PayoutStatus.PROCESSING) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "Bank details are only available while the payout is APPROVED or PROCESSING",
                    java.util.Map.of("from", p.getStatus().name(), "to", PayoutStatus.PROCESSING.name()));
        }
        String iban = crypto.decrypt(p.getIbanCiphertext());
        changes.save(new BankAccountChange(p.getBankAccountId(), p.getOrganizationId(), BankAccountChangeType.REVEALED, p.getIbanLast4(), null,
                null, null, adminId, PayoutActorType.ADMIN, "payout:" + p.getId(), Instant.now()));
        return new PayoutDtos.BankDetailsResponse(p.getId(), p.getAccountHolderName(), iban);
    }

    /** Audited admin reveal of a bank account IBAN so it can be checked against the organization's documents. Any status. */
    @Transactional
    public PayoutDtos.BankAccountIbanResponse revealAccount(UUID adminId, String role, UUID accountId) {
        PayoutAccess.requireAdmin(adminId, role);
        PartnerBankAccount a = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", accountId));
        String iban = crypto.decrypt(a.getIbanCiphertext());
        changes.save(new BankAccountChange(a.getId(), a.getOrganizationId(), BankAccountChangeType.REVEALED, a.getIbanLast4(), null,
                null, null, adminId, PayoutActorType.ADMIN, "bank-account:" + a.getId(), Instant.now()));
        return new PayoutDtos.BankAccountIbanResponse(a.getId(), a.getAccountHolderName(), iban);
    }

    private PartnerBankAccount lockPending(UUID accountId) {
        PartnerBankAccount a = accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", accountId));
        if (a.getStatus() != BankAccountStatus.PENDING_VERIFICATION) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "Bank account is not pending verification",
                    java.util.Map.of("from", a.getStatus().name(), "to", BankAccountStatus.VERIFIED.name()));
        }
        return a;
    }

    static PayoutDtos.BankAccountResponse toResponse(PartnerBankAccount a) {
        return new PayoutDtos.BankAccountResponse(a.getId(), a.getAccountHolderName(), a.getIbanLast4(), IbanMask.mask(a.getIbanLast4()),
                a.getStatus(), a.getVerifiedAt(), a.getRejectReason(), a.getUpdatedAt(), a.getVersion() == null ? 0L : a.getVersion());
    }

    static PayoutDtos.AdminBankAccountResponse toAdminResponse(PartnerBankAccount a) {
        return new PayoutDtos.AdminBankAccountResponse(a.getId(), a.getOrganizationId(), a.getAccountHolderName(), a.getIbanLast4(),
                IbanMask.mask(a.getIbanLast4()), a.getStatus(), a.getVerifiedAt(), a.getRejectReason(), a.getUpdatedAt(),
                a.getVersion() == null ? 0L : a.getVersion());
    }
}
