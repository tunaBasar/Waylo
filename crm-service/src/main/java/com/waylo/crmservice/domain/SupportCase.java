package com.waylo.crmservice.domain;

import com.waylo.crmservice.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** RM-36 support case on the customer's own order. Status flow: OPEN <-> ANSWERED, any -> CLOSED (terminal). */
@Entity
@Table(name = "support_cases")
public class SupportCase {

    public enum Status { OPEN, ANSWERED, CLOSED }

    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;
    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;
    @Column(name = "supplier_organization_id", updatable = false)
    private UUID supplierOrganizationId;
    @Column(nullable = false, length = 150, updatable = false)
    private String subject;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected SupportCase() {}

    public SupportCase(UUID customerId, UUID orderId, UUID orderItemId, UUID supplierOrganizationId, String subject, Instant now) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.supplierOrganizationId = supplierOrganizationId;
        this.subject = subject;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new message moves the case: customer -> OPEN, partner/admin -> ANSWERED. A CLOSED case accepts nothing. */
    public void onMessage(SupportMessage.Role role, Instant now) {
        if (status == Status.CLOSED) throw new BusinessRuleException("CASE_CLOSED", "This support case is closed");
        status = role == SupportMessage.Role.CUSTOMER ? Status.OPEN : Status.ANSWERED;
        updatedAt = now;
    }

    public void close(Instant now) {
        if (status == Status.CLOSED) return;
        status = Status.CLOSED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getSupplierOrganizationId() { return supplierOrganizationId; }
    public String getSubject() { return subject; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
