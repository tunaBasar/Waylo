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
 * Append-only ledger leg. Immutable by design: no setters, every column updatable=false, and the database rejects UPDATE,
 * DELETE and TRUNCATE with a trigger (V2 migration). One movement = several legs sharing a groupId whose debits equal credits.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(name = "entry_id", updatable = false, nullable = false)
    private UUID entryId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account", nullable = false, length = 30, updatable = false)
    private LedgerAccount account;

    @Column(name = "debit_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal creditAmount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)", updatable = false)
    private String currency;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "description", length = 200, updatable = false)
    private String description;

    protected LedgerEntry() {}

    public LedgerEntry(UUID groupId, Instant occurredAt, UUID orderId, UUID orderItemId, LedgerAccount account,
                       BigDecimal debitAmount, BigDecimal creditAmount, String currency, UUID correlationId,
                       String description) {
        this.entryId = UUID.randomUUID();
        this.groupId = groupId;
        this.occurredAt = occurredAt;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.account = account;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.currency = currency;
        this.correlationId = correlationId;
        this.description = description;
    }

    public UUID getEntryId() { return entryId; }
    public UUID getGroupId() { return groupId; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public LedgerAccount getAccount() { return account; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getCurrency() { return currency; }
    public UUID getCorrelationId() { return correlationId; }
    public String getDescription() { return description; }
}
