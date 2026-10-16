package com.waylo.hotelservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

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

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "old_allotment")
    private Integer oldAllotment;

    @Column(name = "new_allotment", nullable = false)
    private int newAllotment;

    @Column(name = "old_out_of_service")
    private Integer oldOutOfService;

    @Column(name = "new_out_of_service", nullable = false)
    private int newOutOfService;

    @Column(name = "old_stop_sell")
    private Boolean oldStopSell;

    @Column(name = "new_stop_sell", nullable = false)
    private boolean newStopSell;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InventoryAdjustment() {}

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHotelId() { return hotelId; }
    public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }

    public UUID getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(UUID roomTypeId) { this.roomTypeId = roomTypeId; }

    public LocalDate getStayDate() { return stayDate; }
    public void setStayDate(LocalDate stayDate) { this.stayDate = stayDate; }

    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getOldAllotment() { return oldAllotment; }
    public void setOldAllotment(Integer oldAllotment) { this.oldAllotment = oldAllotment; }

    public int getNewAllotment() { return newAllotment; }
    public void setNewAllotment(int newAllotment) { this.newAllotment = newAllotment; }

    public Integer getOldOutOfService() { return oldOutOfService; }
    public void setOldOutOfService(Integer oldOutOfService) { this.oldOutOfService = oldOutOfService; }

    public int getNewOutOfService() { return newOutOfService; }
    public void setNewOutOfService(int newOutOfService) { this.newOutOfService = newOutOfService; }

    public Boolean getOldStopSell() { return oldStopSell; }
    public void setOldStopSell(Boolean oldStopSell) { this.oldStopSell = oldStopSell; }

    public boolean isNewStopSell() { return newStopSell; }
    public void setNewStopSell(boolean newStopSell) { this.newStopSell = newStopSell; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
