package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "customer_id", nullable = false) public UUID customerId;
    @Column(name = "trip_id", nullable = false) public UUID tripId;
    @Column(name = "from_seq", nullable = false) public int fromSeq;
    @Column(name = "to_seq", nullable = false) public int toSeq;
    @Column(nullable = false) public String currency;
    @Column(name = "total_amount", nullable = false) public BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public BookingStatus status;
    @Column(name = "hold_expires_at", nullable = false) public Instant holdExpiresAt;
    @Column(name = "idempotency_key", nullable = false) public String idempotencyKey;
    @Column(name = "order_correlation_id") public String orderCorrelationId;
    @Version public Long version;
}
