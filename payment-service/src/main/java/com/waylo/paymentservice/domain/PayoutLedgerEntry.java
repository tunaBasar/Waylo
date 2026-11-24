package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only payout ledger row. payableDelta is the only input to the payable balance (PAYOUT-CONTRACT.md sec. 2).
 * PAID rows are the accounting legs (DR SUPPLIER_PAYABLE, CR SUPPLIER_PAID) with a zero delta. No setters, the database
 * rejects UPDATE, DELETE and TRUNCATE (V9 trigger).
 */
@Entity
@Table(name = "payout_ledger")
public class PayoutLedgerEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "payout_id", updatable = false)
    private UUID payoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
    private PayoutLedgerType entryType;

    @Column(name = "payable_delta", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal payableDelta;

    @Enumerated(EnumType.STRING)
    @Column(name = "account", length = 30, updatable = false)
    private LedgerAccount account;

    @Column(name = "debit_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal creditAmount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)", updatable = false)
    private String currency;

    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;

    @Column(name = "idempotency_key", length = 150, updatable = false)
    private String idempotencyKey;

    @Column(name = "reference", length = 500, updatable = false)
    private String reference;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PayoutLedgerEntry() {}

    public PayoutLedgerEntry(UUID groupId, UUID organizationId, UUID payoutId, PayoutLedgerType entryType,
                             BigDecimal payableDelta, LedgerAccount account, BigDecimal debitAmount, BigDecimal creditAmount,
                             String currency, UUID orderItemId, String idempotencyKey, String reference,
                             UUID createdBy, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.organizationId = organizationId;
        this.payoutId = payoutId;
        this.entryType = entryType;
        this.payableDelta = payableDelta;
        this.account = account;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.currency = currency;
        this.orderItemId = orderItemId;
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getPayoutId() { return payoutId; }
    public PayoutLedgerType getEntryType() { return entryType; }
    public BigDecimal getPayableDelta() { return payableDelta; }
    public LedgerAccount getAccount() { return account; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getCurrency() { return currency; }
    public UUID getOrderItemId() { return orderItemId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReference() { return reference; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
