package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trip_fares")
public class TripFare {
    @Id public UUID id;
    @Column(name = "trip_id", nullable = false) public UUID tripId;
    @Column(name = "from_seq", nullable = false) public int fromSeq;
    @Column(name = "to_seq", nullable = false) public int toSeq;
    @Column(nullable = false) public BigDecimal price;
}
