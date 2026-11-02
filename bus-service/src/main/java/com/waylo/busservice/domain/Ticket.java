package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One seat on one segment. Active (blocking) while HELD and unexpired, or CONFIRMED. */
@Entity
@Table(name = "tickets")
public class Ticket extends Audited {
    @Id public UUID id;
    @Column(name = "booking_id", nullable = false) public UUID bookingId;
    @Column(name = "trip_id", nullable = false) public UUID tripId;
    @Column(name = "seat_no", nullable = false) public int seatNo;
    @Column(name = "from_seq", nullable = false) public int fromSeq;
    @Column(name = "to_seq", nullable = false) public int toSeq;
    @Column(name = "passenger_name", nullable = false) public String passengerName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Gender gender;
    @Column(name = "identity_number") public String identityNumber;
    @Column(nullable = false) public BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public BookingStatus status;
    @Column(name = "hold_expires_at", nullable = false) public Instant holdExpiresAt;
}
