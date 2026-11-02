package com.waylo.carrentalservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rentals")
public class Rental extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "customer_id", nullable = false) public UUID customerId;
    @Column(name = "vehicle_id", nullable = false) public UUID vehicleId;
    @Column(name = "pickup_branch_id", nullable = false) public UUID pickupBranchId;
    @Column(name = "return_branch_id", nullable = false) public UUID returnBranchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public CarCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Transmission transmission;
    @Column(name = "pickup_at", nullable = false) public Instant pickupAt;
    @Column(name = "return_at", nullable = false) public Instant returnAt;
    @Column(name = "chargeable_days", nullable = false) public int chargeableDays;
    @Column(name = "daily_rate", nullable = false) public BigDecimal dailyRate;
    @Column(name = "one_way_fee", nullable = false) public BigDecimal oneWayFee;
    @Column(name = "total_amount", nullable = false) public BigDecimal totalAmount;
    @Column(nullable = false) public String currency;
    @Column(name = "deposit_amount", nullable = false) public BigDecimal depositAmount;
    @Column(name = "km_limit_per_day") public Integer kmLimitPerDay;
    @Column(name = "driver_age", nullable = false) public int driverAge;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public RentalStatus status;
    @Column(name = "hold_expires_at", nullable = false) public Instant holdExpiresAt;
    @Column(name = "idempotency_key", nullable = false) public String idempotencyKey;
    @Column(name = "order_correlation_id") public String orderCorrelationId;
    @Version public Long version;
}
