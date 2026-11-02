package com.waylo.checkinservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "seat_number", length = 10)
    private String seatNumber;

    @Column(name = "boarding_pass_url")
    private String boardingPassUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CheckinStatus status = CheckinStatus.NOT_CHECKED_IN;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "order_item_id") private UUID orderItemId;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "pnr", length = 8) private String pnr;
    @Column(name = "passenger_name", length = 170) private String passengerName;
    @Column(name = "flight_number", length = 10) private String flightNumber;
    @Column(name = "departure_at") private Instant departureAt;

    public Checkin() {}

    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID v) { this.orderItemId = v; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public String getPnr() { return pnr; }
    public void setPnr(String v) { this.pnr = v; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String v) { this.passengerName = v; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String v) { this.flightNumber = v; }
    public Instant getDepartureAt() { return departureAt; }
    public void setDepartureAt(Instant v) { this.departureAt = v; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getBoardingPassUrl() { return boardingPassUrl; }
    public void setBoardingPassUrl(String boardingPassUrl) { this.boardingPassUrl = boardingPassUrl; }

    public CheckinStatus getStatus() { return status; }
    public void setStatus(CheckinStatus status) { this.status = status; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
