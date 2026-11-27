package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trip_schedule_fares")
public class TripScheduleFare {
    @Id public UUID id;
    @Column(name = "schedule_id", nullable = false) public UUID scheduleId;
    @Column(name = "from_seq", nullable = false) public int fromSeq;
    @Column(name = "to_seq", nullable = false) public int toSeq;
    @Column(nullable = false) public BigDecimal price;
}
