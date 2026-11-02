package com.waylo.pricingservice.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tax rate configuration.
 * Maps to tax_rates table.
 */
@Entity
@Table(name = "tax_rates")
public class TaxRate {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String vertical;

    @Column(name = "scope_org_id")
    private UUID scopeOrgId;

    @Column(name = "tax_type", nullable = false)
    private String taxType;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private Boolean inclusive;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVertical() { return vertical; }
    public void setVertical(String vertical) { this.vertical = vertical; }

    public UUID getScopeOrgId() { return scopeOrgId; }
    public void setScopeOrgId(UUID scopeOrgId) { this.scopeOrgId = scopeOrgId; }

    public String getTaxType() { return taxType; }
    public void setTaxType(String taxType) { this.taxType = taxType; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public Boolean getInclusive() { return inclusive; }
    public void setInclusive(Boolean inclusive) { this.inclusive = inclusive; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
}
