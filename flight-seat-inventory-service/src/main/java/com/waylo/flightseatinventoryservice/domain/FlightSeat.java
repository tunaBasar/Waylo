package com.waylo.flightseatinventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uçuş koltuk envanteri.
 */
@Entity
@Table(name = "flight_seats")
public class FlightSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Column(name = "seat_type", nullable = false, length = 20)
    private String seatType;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "reservation_id", length = 64)
    private String reservationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "reserved_by")
    private UUID reservedBy;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FlightSeat() {}

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public long getVersion() { return version; }

    public UUID getReservedBy() { return reservedBy; }
    public void setReservedBy(UUID reservedBy) { this.reservedBy = reservedBy; }

    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(Instant holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
