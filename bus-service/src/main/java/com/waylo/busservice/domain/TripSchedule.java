package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Weekly timetable: a daily job turns it into regular trips. Times are Europe/Istanbul. */
@Entity
@Table(name = "trip_schedules")
public class TripSchedule extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "bus_id", nullable = false) public UUID busId;
    @Column(nullable = false) public String code;
    @Column(nullable = false) public String currency;
    /** Bit (dayOfWeek.getValue() - 1): MONDAY = 1 ... SUNDAY = 64. */
    @Column(nullable = false) public short weekdays;
    @Column(name = "departure_time", nullable = false) public LocalTime departureTime;
    @Column(name = "valid_from", nullable = false) public LocalDate validFrom;
    @Column(name = "valid_to") public LocalDate validTo;
    @Column(name = "horizon_days", nullable = false) public int horizonDays;
    @Column(nullable = false) public boolean active = true;
    @Version public Long version;
}
