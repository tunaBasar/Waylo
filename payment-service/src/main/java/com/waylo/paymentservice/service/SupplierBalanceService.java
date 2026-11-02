package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.SupplierAccount;
import com.waylo.paymentservice.domain.SupplierLedgerEntry;
import com.waylo.paymentservice.domain.SupplierLedgerEntry.Type;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.SupplierAccountRepository;
import com.waylo.paymentservice.repository.SupplierLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Prepaid supplier balance. Every change = one row lock on the account + one append-only ledger row, in one transaction.
 * Balance is topped up only by an explicit admin call, never automatically.
 */
@Service
public class SupplierBalanceService {

    public static final String INSUFFICIENT = "SUPPLIER_BALANCE_INSUFFICIENT";
    public static final String ACCOUNT_NOT_FOUND = "SUPPLIER_ACCOUNT_NOT_FOUND";

    private final SupplierAccountRepository accounts;
    private final SupplierLedgerRepository ledger;

    public SupplierBalanceService(SupplierAccountRepository accounts, SupplierLedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    private static String normSupplier(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("supplier is required");
        String v = s.trim().toUpperCase(Locale.ROOT);
        if (v.equals("INTERNAL")) throw new BusinessRuleException("INTERNAL_SUPPLIER_HAS_NO_ACCOUNT", "The internal supplier has no balance account");
        return v;
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN); }

    /** Admin top-up. Creates the account on first use. Replaying an idempotency key returns the account unchanged. */
    @Transactional
    public SupplierAccount topUp(String rawSupplier, BigDecimal amount, String currency, String reference,
                                 String idempotencyKey, UUID adminId) {
        String supplier = normSupplier(rawSupplier);
        BigDecimal amt = money(amount);
        if (amt.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        var replay = ledger.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return accounts.findById(supplier).orElseThrow();
        }
        SupplierAccount acc = accounts.findForUpdate(supplier).orElse(null);
        if (acc == null) {
            acc = accounts.saveAndFlush(new SupplierAccount(supplier, currency));
            acc = accounts.findForUpdate(supplier).orElseThrow();
        }
        if (!acc.getCurrency().equals(currency)) {
            throw new BusinessRuleException("SUPPLIER_CURRENCY_MISMATCH", "Supplier account currency is " + acc.getCurrency());
        }
        return apply(acc, Type.TOPUP, amt, null, null, reference, idempotencyKey, adminId);
    }

    /** Ticketing payout: debit the net fare. Insufficient balance writes nothing and fails with 422. */
    @Transactional
    public SupplierAccount payout(String rawSupplier, UUID orderId, UUID orderItemId, BigDecimal amount, String currency,
                                  String idempotencyKey) {
        String supplier = normSupplier(rawSupplier);
        BigDecimal amt = money(amount);
        if (ledger.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return accounts.findById(supplier).orElseThrow();
        }
        SupplierAccount acc = accounts.findForUpdate(supplier)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierAccount", "supplier", supplier));
        if (!acc.getCurrency().equals(currency)) {
            throw new BusinessRuleException("SUPPLIER_CURRENCY_MISMATCH", "Supplier account currency is " + acc.getCurrency());
        }
        if (acc.getBalance().compareTo(amt) < 0) {
            throw new BusinessRuleException(INSUFFICIENT, "Supplier balance " + acc.getBalance() + " " + currency
                    + " is below the required " + amt + " " + currency + ", ticket was not issued");
        }
        return apply(acc, Type.TICKET_PAYOUT, amt.negate(), orderId, orderItemId, "ticket payout", idempotencyKey, null);
    }

    /** Money coming back from the supplier after an airline-rule refund. */
    @Transactional
    public SupplierAccount credit(String rawSupplier, UUID orderId, UUID orderItemId, BigDecimal amount, String currency,
                                  String idempotencyKey) {
        String supplier = normSupplier(rawSupplier);
        BigDecimal amt = money(amount);
        if (ledger.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return accounts.findById(supplier).orElseThrow();
        }
        SupplierAccount acc = accounts.findForUpdate(supplier)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierAccount", "supplier", supplier));
        if (!acc.getCurrency().equals(currency)) {
            throw new BusinessRuleException("SUPPLIER_CURRENCY_MISMATCH", "Supplier account currency is " + acc.getCurrency());
        }
        return apply(acc, Type.REFUND_CREDIT, amt, orderId, orderItemId, "supplier refund", idempotencyKey, null);
    }

    private SupplierAccount apply(SupplierAccount acc, Type type, BigDecimal delta, UUID orderId, UUID orderItemId,
                                  String reference, String key, UUID by) {
        acc.apply(delta);
        accounts.saveAndFlush(acc);
        ledger.saveAndFlush(new SupplierLedgerEntry(acc.getSupplier(), type, delta, acc.getBalance(), acc.getCurrency(),
                orderId, orderItemId, reference, key, by));
        return acc;
    }

    @Transactional(readOnly = true)
    public List<SupplierAccount> list() { return accounts.findAllByOrderBySupplierAsc(); }

    @Transactional(readOnly = true)
    public Page<SupplierLedgerEntry> ledger(String rawSupplier, int page, int size) {
        String supplier = normSupplier(rawSupplier);
        return ledger.findBySupplierOrderByCreatedAtDesc(supplier, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
