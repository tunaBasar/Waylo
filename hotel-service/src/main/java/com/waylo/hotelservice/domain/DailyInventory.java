package com.waylo.hotelservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_inventory")
public class DailyInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "room_type_id", nullable = false, updatable = false)
    private UUID roomTypeId;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(name = "allotment", nullable = false)
    private int allotment;

    @Column(name = "out_of_service", nullable = false)
    private int outOfService = 0;

    @Column(name = "held", nullable = false)
    private int held = 0;

    @Column(name = "confirmed", nullable = false)
    private int confirmed = 0;

    @Column(name = "stop_sell", nullable = false)
    private boolean stopSell = false;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    public DailyInventory() {}

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHotelId() { return hotelId; }
    public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }

    public UUID getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(UUID roomTypeId) { this.roomTypeId = roomTypeId; }

    public LocalDate getStayDate() { return stayDate; }
    public void setStayDate(LocalDate stayDate) { this.stayDate = stayDate; }

    public int getAllotment() { return allotment; }
    public void setAllotment(int allotment) { this.allotment = allotment; }

    public int getOutOfService() { return outOfService; }
    public void setOutOfService(int outOfService) { this.outOfService = outOfService; }

    public int getHeld() { return held; }
    public void setHeld(int held) { this.held = held; }

    public int getConfirmed() { return confirmed; }
    public void setConfirmed(int confirmed) { this.confirmed = confirmed; }

    public boolean isStopSell() { return stopSell; }
    public void setStopSell(boolean stopSell) { this.stopSell = stopSell; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Compute available = allotment - outOfService - held - confirmed.
     * Derived value; not stored in DB.
     */
    public int getAvailable() {
        return allotment - outOfService - held - confirmed;
    }
}
