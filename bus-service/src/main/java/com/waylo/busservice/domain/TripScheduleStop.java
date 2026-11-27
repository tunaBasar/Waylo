package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "trip_schedule_stops")
public class TripScheduleStop {
    @Id public UUID id;
    @Column(name = "schedule_id", nullable = false) public UUID scheduleId;
    @Column(nullable = false) public int seq;
    @Column(name = "terminal_id", nullable = false) public UUID terminalId;
    @Column(name = "arrival_offset_minutes", nullable = false) public int arrivalOffsetMinutes;
    @Column(name = "departure_offset_minutes", nullable = false) public int departureOffsetMinutes;
}
