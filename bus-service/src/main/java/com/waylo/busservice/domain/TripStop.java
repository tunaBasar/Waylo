package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_stops")
public class TripStop {
    @Id public UUID id;
    @Column(name = "trip_id", nullable = false) public UUID tripId;
    @Column(nullable = false) public int seq;
    @Column(name = "terminal_id", nullable = false) public UUID terminalId;
    @Column(name = "arrival_at") public Instant arrivalAt;
    @Column(name = "departure_at") public Instant departureAt;
}
