package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One per OrderItem. Money held for the item. The commission rate and amounts are snapshots taken at capture time. */
@Entity
@Table(name = "escrow_items")
public class EscrowItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_intent_id", nullable = false, updatable = false)
    private UUID paymentIntentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false, updatable = false, unique = true)
    private UUID orderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vertical", nullable = false, length = 20, updatable = false)
    private Vertical vertical;

    @Column(name = "supplier_organization_id", nullable = false, updatable = false)
    private UUID supplierOrganizationId;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal commissionAmount;

    @Column(name = "supplier_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal supplierAmount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)", updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowStatus status = EscrowStatus.HELD;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_trigger", nullable = false, length = 20, updatable = false)
    private ReleaseTrigger releaseTrigger;

    @Column(name = "provider_item_ref")
    private String providerItemRef;

    @Column(name = "held_at", nullable = false, updatable = false)
    private Instant heldAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public EscrowItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(UUID paymentIntentId) { this.paymentIntentId = paymentIntentId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }
    public Vertical getVertical() { return vertical; }
    public void setVertical(Vertical vertical) { this.vertical = vertical; }
    public UUID getSupplierOrganizationId() { return supplierOrganizationId; }
    public void setSupplierOrganizationId(UUID supplierOrganizationId) { this.supplierOrganizationId = supplierOrganizationId; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public BigDecimal getSupplierAmount() { return supplierAmount; }
    public void setSupplierAmount(BigDecimal supplierAmount) { this.supplierAmount = supplierAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public EscrowStatus getStatus() { return status; }
    public void setStatus(EscrowStatus status) { this.status = status; }
    public ReleaseTrigger getReleaseTrigger() { return releaseTrigger; }
    public void setReleaseTrigger(ReleaseTrigger releaseTrigger) { this.releaseTrigger = releaseTrigger; }
    public String getProviderItemRef() { return providerItemRef; }
    public void setProviderItemRef(String providerItemRef) { this.providerItemRef = providerItemRef; }
    public Instant getHeldAt() { return heldAt; }
    public void setHeldAt(Instant heldAt) { this.heldAt = heldAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public Long getVersion() { return version; }
}
