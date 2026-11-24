package com.waylo.hotelservice.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append only audit row for an admin media review decision. */
@Entity
@Table(name = "hotel_media_decisions")
public class HotelMediaDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "media_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "decision", nullable = false, updatable = false, length = 16)
    private String decision;

    @Column(name = "from_status", nullable = false, updatable = false, length = 16)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false, length = 16)
    private String toStatus;

    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public HotelMediaDecision() { }

    public HotelMediaDecision(HotelMedia media, String decision, String fromStatus, String toStatus, String reason, UUID actor) {
        this.mediaId = media.getId();
        this.hotelId = media.getHotelId();
        this.organizationId = media.getOrganizationId();
        this.decision = decision;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.actorUserId = actor;
    }

    public UUID getId() { return id; }
    public UUID getMediaId() { return mediaId; }
    public UUID getHotelId() { return hotelId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getDecision() { return decision; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
