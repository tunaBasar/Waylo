package com.waylo.flightseatinventoryservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Order-level hold of one seat for one passenger (internal flight supplier). */
@Entity
@Table(name = "seat_holds")
public class SeatHold {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "order_correlation_id", length = 100)
    private String orderCorrelationId;

    @Column(name = "principal_user_id", nullable = false)
    private UUID principalUserId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Column(name = "cabin", nullable = false, length = 20)
    private String cabin;

    @Column(name = "passenger_first_name", nullable = false, length = 80)
    private String passengerFirstName;

    @Column(name = "passenger_last_name", nullable = false, length = 80)
    private String passengerLastName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "departure_airport", nullable = false, length = 10)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 10)
    private String arrivalAirport;

    @Column(name = "airline", nullable = false, length = 100)
    private String airline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private HoldStatus status = HoldStatus.HELD;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "pnr", length = 8)
    private String pnr;

    @Column(name = "ticket_number", length = 20)
    private String ticketNumber;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SeatHold() {}

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getOrderCorrelationId() { return orderCorrelationId; }
    public void setOrderCorrelationId(String v) { this.orderCorrelationId = v; }
    public UUID getPrincipalUserId() { return principalUserId; }
    public void setPrincipalUserId(UUID v) { this.principalUserId = v; }
    public UUID getSeatId() { return seatId; }
    public void setSeatId(UUID v) { this.seatId = v; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String v) { this.flightNumber = v; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String v) { this.seatNumber = v; }
    public String getCabin() { return cabin; }
    public void setCabin(String v) { this.cabin = v; }
    public String getPassengerFirstName() { return passengerFirstName; }
    public void setPassengerFirstName(String v) { this.passengerFirstName = v; }
    public String getPassengerLastName() { return passengerLastName; }
    public void setPassengerLastName(String v) { this.passengerLastName = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public Instant getDepartureTime() { return departureTime; }
    public void setDepartureTime(Instant v) { this.departureTime = v; }
    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant v) { this.arrivalTime = v; }
    public String getDepartureAirport() { return departureAirport; }
    public void setDepartureAirport(String v) { this.departureAirport = v; }
    public String getArrivalAirport() { return arrivalAirport; }
    public void setArrivalAirport(String v) { this.arrivalAirport = v; }
    public String getAirline() { return airline; }
    public void setAirline(String v) { this.airline = v; }
    public HoldStatus getStatus() { return status; }
    public void setStatus(HoldStatus v) { this.status = v; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(Instant v) { this.holdExpiresAt = v; }
    public String getPnr() { return pnr; }
    public void setPnr(String v) { this.pnr = v; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String v) { this.ticketNumber = v; }
    public long getVersion() { return version; }
}
