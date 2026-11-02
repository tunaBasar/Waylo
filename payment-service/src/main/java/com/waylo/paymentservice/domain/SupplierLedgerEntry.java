package com.waylo.paymentservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only (database trigger rejects UPDATE and DELETE). */
@Entity
@Table(name = "supplier_ledger")
public class SupplierLedgerEntry {

    public enum Type { TOPUP, TICKET_PAYOUT, REFUND_CREDIT, ADJUSTMENT }

    @Id @Column(name = "id", updatable = false) private UUID id;
    @Column(name = "supplier", nullable = false, updatable = false, length = 30) private String supplier;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, updatable = false, length = 20) private Type entryType;
    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal balanceAfter;
    @Column(name = "currency", nullable = false, updatable = false, length = 3, columnDefinition = "bpchar(3)") private String currency;
    @Column(name = "order_id", updatable = false) private UUID orderId;
    @Column(name = "order_item_id", updatable = false) private UUID orderItemId;
    @Column(name = "reference", updatable = false, length = 200) private String reference;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200) private String idempotencyKey;
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected SupplierLedgerEntry() {}

    public SupplierLedgerEntry(String supplier, Type type, BigDecimal amount, BigDecimal balanceAfter, String currency,
                               UUID orderId, UUID orderItemId, String reference, String idempotencyKey, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.supplier = supplier;
        this.entryType = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.currency = currency;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSupplier() { return supplier; }
    public Type getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getCurrency() { return currency; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public String getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
