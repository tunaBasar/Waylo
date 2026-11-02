package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.LedgerAccount;
import com.waylo.paymentservice.domain.LedgerEntry;
import com.waylo.paymentservice.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Writes balanced movements. Every method inserts legs of one group whose debits equal credits. Never updates or deletes. */
@Service
public class LedgerService {

    private final LedgerEntryRepository repository;

    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    /** Customer money enters escrow: DR CUSTOMER_PAYMENT, CR CUSTOMER_FUNDS_HELD. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCapture(UUID orderId, UUID orderItemId, BigDecimal gross, String currency, UUID correlationId) {
        List<Leg> legs = List.of(
                Leg.debit(LedgerAccount.CUSTOMER_PAYMENT, gross),
                Leg.credit(LedgerAccount.CUSTOMER_FUNDS_HELD, gross));
        write(orderId, orderItemId, currency, correlationId, "capture into escrow", legs);
    }

    /** Release without a platform-funded discount (charged = gross). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRelease(UUID orderId, UUID orderItemId, BigDecimal gross, BigDecimal supplier, BigDecimal commission,
                              String currency, UUID correlationId) {
        recordRelease(orderId, orderItemId, gross, supplier, commission, BigDecimal.ZERO, currency, correlationId);
    }

    /**
     * Release: DR CUSTOMER_FUNDS_HELD charged (what the customer paid), DR PLATFORM_DISCOUNT_COST platformCost (PLATFORM-funded promo),
     * CR SUPPLIER_PAYABLE supplier share, CR PLATFORM_COMMISSION commission. Balanced: charged + platformCost = supplier + commission.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRelease(UUID orderId, UUID orderItemId, BigDecimal charged, BigDecimal supplier, BigDecimal commission,
                              BigDecimal platformCost, String currency, UUID correlationId) {
        BigDecimal gross = charged;
        List<Leg> legs = new ArrayList<>();
        legs.add(Leg.debit(LedgerAccount.CUSTOMER_FUNDS_HELD, gross));
        if (platformCost != null && platformCost.signum() > 0) legs.add(Leg.debit(LedgerAccount.PLATFORM_DISCOUNT_COST, platformCost));
        legs.add(Leg.credit(LedgerAccount.SUPPLIER_PAYABLE, supplier));
        if (commission.signum() > 0) legs.add(Leg.credit(LedgerAccount.PLATFORM_COMMISSION, commission));
        write(orderId, orderItemId, currency, correlationId, "release to supplier", legs);
    }

    /** Refund of a held item: DR CUSTOMER_FUNDS_HELD, CR CUSTOMER_REFUND. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefund(UUID orderId, UUID orderItemId, BigDecimal gross, String currency, UUID correlationId) {
        List<Leg> legs = List.of(
                Leg.debit(LedgerAccount.CUSTOMER_FUNDS_HELD, gross),
                Leg.credit(LedgerAccount.CUSTOMER_REFUND, gross));
        write(orderId, orderItemId, currency, correlationId, "refund to customer", legs);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesOfOrder(UUID orderId) {
        return repository.findByOrderIdOrderByOccurredAtAscEntryIdAsc(orderId);
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOfOrder(UUID orderId) {
        return repository.balanceOfOrder(orderId);
    }

    private void write(UUID orderId, UUID orderItemId, String currency, UUID correlationId, String description, List<Leg> legs) {
        BigDecimal debits = legs.stream().map(Leg::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = legs.stream().map(Leg::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Unbalanced ledger movement: debits " + debits + " credits " + credits);
        }
        UUID groupId = UUID.randomUUID();
        Instant now = Instant.now();
        for (Leg leg : legs) {
            repository.save(new LedgerEntry(groupId, now, orderId, orderItemId, leg.account(), leg.debit(), leg.credit(),
                    currency, correlationId, description));
        }
    }

    private record Leg(LedgerAccount account, BigDecimal debit, BigDecimal credit) {
        static Leg debit(LedgerAccount a, BigDecimal v) { return new Leg(a, v, BigDecimal.ZERO); }
        static Leg credit(LedgerAccount a, BigDecimal v) { return new Leg(a, BigDecimal.ZERO, v); }
    }
}
