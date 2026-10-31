package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A versioned commission rate (rows are never updated, a new row with a later validFrom supersedes). No rows are seeded. */
@Entity
@Table(name = "commission_rates")
public class CommissionRate {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vertical", nullable = false, length = 20, updatable = false)
    private Vertical vertical;

    /** null = default for the vertical, set = override for that supplier organization. */
    @Column(name = "supplier_organization_id", updatable = false)
    private UUID supplierOrganizationId;

    @Column(name = "rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CommissionRate() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Vertical getVertical() { return vertical; }
    public void setVertical(Vertical vertical) { this.vertical = vertical; }
    public UUID getSupplierOrganizationId() { return supplierOrganizationId; }
    public void setSupplierOrganizationId(UUID supplierOrganizationId) { this.supplierOrganizationId = supplierOrganizationId; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
