package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of one reconciliation decision (DB trigger rejects update and delete). */
@Entity
@Table(name = "payment_reconciliations")
public class PaymentReconciliation {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "payment_intent_id", nullable = false, updatable = false)
    private UUID paymentIntentId;
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;
    @Column(name = "provider_payment_id", updatable = false)
    private String providerPaymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "intent_status", nullable = false, updatable = false, length = 30)
    private PaymentIntentStatus intentStatus;
    @Column(name = "order_status", updatable = false, length = 20)
    private String orderStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 24)
    private ReconciliationStatus status;
    @Column(name = "detail", updatable = false, length = 500)
    private String detail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentReconciliation() {}

    public PaymentReconciliation(PaymentIntent intent, PaymentIntentStatus intentStatusBefore, String orderStatus,
                                 ReconciliationStatus status, String detail, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentIntentId = intent.getId();
        this.orderId = intent.getOrderId();
        this.providerPaymentId = intent.getProviderPaymentId();
        this.intentStatus = intentStatusBefore;
        this.orderStatus = orderStatus;
        this.status = status;
        this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentIntentId() { return paymentIntentId; }
    public UUID getOrderId() { return orderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public PaymentIntentStatus getIntentStatus() { return intentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public ReconciliationStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
