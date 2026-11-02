package com.waylo.carrentalservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "category_rates")
public class CategoryRate extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "branch_id", nullable = false) public UUID branchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public CarCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Transmission transmission;
    @Column(nullable = false) public String currency;
    @Column(name = "daily_rate", nullable = false) public BigDecimal dailyRate;
    @Column(name = "deposit_amount", nullable = false) public BigDecimal depositAmount;
    @Column(name = "km_limit_per_day") public Integer kmLimitPerDay;
    @Column(name = "extra_km_fee") public BigDecimal extraKmFee;
    @Column(name = "min_driver_age", nullable = false) public int minDriverAge;
    @Column(name = "min_license_years", nullable = false) public int minLicenseYears;
    @Version public Long version;
}
