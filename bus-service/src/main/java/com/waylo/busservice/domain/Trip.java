package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "bus_id", nullable = false) public UUID busId;
    @Column(nullable = false) public String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public TripStatus status;
    @Column(name = "departure_at", nullable = false) public Instant departureAt;
    @Column(nullable = false) public String currency;
    @Version public Long version;
}
