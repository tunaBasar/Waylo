package com.waylo.paymentservice.service;

import com.waylo.paymentservice.config.PayoutSettings;
import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.PartnerBankAccount;
import com.waylo.paymentservice.domain.PayoutStatus;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.PartnerBankAccountRepository;
import com.waylo.paymentservice.repository.PartnerPayoutRepository;
import com.waylo.paymentservice.repository.PayoutLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Payable balance per (organization, currency), always derived (PAYOUT-CONTRACT.md sec. 2), never stored:
 * payable = SUM(supplier_amount of RELEASED items past the holdback) + SUM(payout_ledger.payable_delta).
 * supplier_amount is already net of the commission snapshot taken at capture. Currencies are never mixed or converted.
 */
@Service
public class PayoutBalanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final EscrowItemRepository escrowRepository;
    private final PayoutLedgerRepository ledgerRepository;
    private final PartnerPayoutRepository payoutRepository;
    private final PartnerBankAccountRepository bankAccountRepository;
    private final PayoutSettings settings;

    public PayoutBalanceService(EscrowItemRepository escrowRepository, PayoutLedgerRepository ledgerRepository,
                                PartnerPayoutRepository payoutRepository, PartnerBankAccountRepository bankAccountRepository,
                                PayoutSettings settings) {
        this.escrowRepository = escrowRepository;
        this.ledgerRepository = ledgerRepository;
        this.payoutRepository = payoutRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.settings = settings;
    }

    /** Raw numbers of one currency. payableRaw may be negative (owed). */
    public record Raw(String currency, BigDecimal eligible, BigDecimal inHoldback, BigDecimal held, BigDecimal delta,
                      BigDecimal adjustmentsNet, BigDecimal reserved, BigDecimal paidTotal, BigDecimal ledgerPayable) {

        public BigDecimal payableRaw() { return eligible.add(delta); }
        public BigDecimal payable() { return payableRaw().signum() < 0 ? ZERO : payableRaw(); }
        public BigDecimal owed() { return payableRaw().signum() < 0 ? payableRaw().negate() : ZERO; }
        public BigDecimal released() { return eligible.add(inHoldback); }

        /** released = payable + releasedNotYetPayable + reservedOpen + paidTotal - adjustmentsNet, and equals the SUPPLIER_PAYABLE ledger credits. */
        public boolean reconciles() {
            BigDecimal rhs = payableRaw().add(inHoldback).add(reserved).add(paidTotal).subtract(adjustmentsNet);
            return released().compareTo(rhs) == 0 && released().compareTo(ledgerPayable) == 0;
        }
    }

    /** Balance of one currency. Call under the wallet lock when the result decides a payout. */
    @Transactional(readOnly = true)
    public Raw rawFor(UUID organizationId, String currency, Instant now) {
        return rawAll(organizationId, now).getOrDefault(currency, empty(currency));
    }

    @Transactional(readOnly = true)
    public Map<String, Raw> rawAll(UUID organizationId, Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(settings.getHoldbackDays()));
        Map<String, BigDecimal> eligible = toMap(escrowRepository.eligibleReleasedByCurrency(organizationId, cutoff));
        Map<String, BigDecimal> inHoldback = toMap(escrowRepository.releasedInHoldbackByCurrency(organizationId, cutoff));
        Map<String, BigDecimal> held = toMap(escrowRepository.heldByCurrency(organizationId));
        Map<String, BigDecimal> delta = toMap(ledgerRepository.deltaByCurrency(organizationId));
        Map<String, BigDecimal> adjustments = toMap(ledgerRepository.adjustmentsByCurrency(organizationId));
        Map<String, BigDecimal> reserved = toMap(payoutRepository.openAmountByCurrency(organizationId, PayoutStatus.OPEN));
        Map<String, BigDecimal> paid = toMap(payoutRepository.paidTotalByCurrency(organizationId));
        Map<String, BigDecimal> ledgerPayable = toMap(escrowRepository.ledgerSupplierPayableByCurrency(organizationId));

        TreeSet<String> currencies = new TreeSet<>();
        for (Map<String, BigDecimal> m : List.of(eligible, inHoldback, held, delta, adjustments, reserved, paid)) currencies.addAll(m.keySet());
        Map<String, Raw> out = new TreeMap<>();
        for (String c : currencies) {
            out.put(c, new Raw(c, eligible.getOrDefault(c, ZERO), inHoldback.getOrDefault(c, ZERO), held.getOrDefault(c, ZERO),
                    delta.getOrDefault(c, ZERO), adjustments.getOrDefault(c, ZERO), reserved.getOrDefault(c, ZERO),
                    paid.getOrDefault(c, ZERO), ledgerPayable.getOrDefault(c, ZERO)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PayoutDtos.BalanceResponse balance(UUID organizationId, boolean owner, boolean organizationActive, Instant now) {
        Optional<PartnerBankAccount> account = liveAccount(organizationId);
        Map<String, Raw> raw = rawAll(organizationId, now);
        List<PayoutDtos.BalanceItem> items = new ArrayList<>();
        for (Raw r : raw.values()) items.add(item(r, owner, organizationActive, account));
        return new PayoutDtos.BalanceResponse(organizationId, settings.getHoldbackDays(), items, summary(account));
    }

    @Transactional(readOnly = true)
    public PayoutDtos.AdminBalanceResponse adminBalance(UUID organizationId, Instant now) {
        Optional<PartnerBankAccount> account = liveAccount(organizationId);
        List<PayoutDtos.AdminBalanceItem> items = new ArrayList<>();
        for (Raw r : rawAll(organizationId, now).values()) {
            PayoutDtos.BalanceItem b = item(r, true, true, account);
            items.add(new PayoutDtos.AdminBalanceItem(b.currency(), b.payable(), b.releasedNotYetPayable(), b.held(), b.reserved(),
                    b.paidTotal(), b.owed(), b.minimumPayout(), b.canRequest(), b.blockedReason(), r.adjustmentsNet(), r.reconciles()));
        }
        return new PayoutDtos.AdminBalanceResponse(organizationId, settings.getHoldbackDays(), items, summary(account));
    }

    /** Balance of one currency for the admin detail (owner and active are not evaluated for admins). */
    @Transactional(readOnly = true)
    public PayoutDtos.BalanceItem itemFor(UUID organizationId, String currency, Instant now) {
        return item(rawFor(organizationId, currency, now), true, true, liveAccount(organizationId));
    }

    private PayoutDtos.BalanceItem item(Raw r, boolean owner, boolean active, Optional<PartnerBankAccount> account) {
        BigDecimal minimum = settings.minimumFor(r.currency());
        String blocked = blockedReason(r, minimum, owner, active, account);
        return new PayoutDtos.BalanceItem(r.currency(), r.payable(), r.inHoldback(), r.held(), r.reserved(), r.paidTotal(), r.owed(),
                minimum, blocked == null, blocked);
    }

    private String blockedReason(Raw r, BigDecimal minimum, boolean owner, boolean active, Optional<PartnerBankAccount> account) {
        if (!owner) return "NOT_OWNER";
        if (!active) return "ORGANIZATION_NOT_ACTIVE";
        if (minimum == null) return "CURRENCY_NOT_SUPPORTED";
        if (account.isEmpty()) return "NO_BANK_ACCOUNT";
        if (account.get().getStatus() != BankAccountStatus.VERIFIED) return "BANK_NOT_VERIFIED";
        if (r.reserved().signum() > 0) return "OPEN_PAYOUT";
        if (r.payableRaw().signum() < 0) return "NEGATIVE_BALANCE";
        if (r.payable().compareTo(minimum) < 0) return "BELOW_MINIMUM";
        return null;
    }

    private Optional<PartnerBankAccount> liveAccount(UUID organizationId) {
        return bankAccountRepository.findByOrganizationId(organizationId).filter(a -> a.getStatus() != BankAccountStatus.DISABLED);
    }

    private static PayoutDtos.BankSummary summary(Optional<PartnerBankAccount> account) {
        return account.map(a -> new PayoutDtos.BankSummary(a.getStatus(), IbanMask.mask(a.getIbanLast4()))).orElse(null);
    }

    private static Raw empty(String currency) {
        return new Raw(currency, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private static Map<String, BigDecimal> toMap(List<Object[]> rows) {
        Map<String, BigDecimal> m = new TreeMap<>();
        for (Object[] row : rows) {
            String currency = String.valueOf(row[0]).trim();
            m.merge(currency, new BigDecimal(String.valueOf(row[1])).setScale(2, RoundingMode.HALF_EVEN), BigDecimal::add);
        }
        return m;
    }
}
