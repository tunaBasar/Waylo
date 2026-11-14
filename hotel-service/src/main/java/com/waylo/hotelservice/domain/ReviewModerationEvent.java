package com.waylo.hotelservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_review_moderation_events")
public class ReviewModerationEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 20)
    private ReviewStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private ReviewStatus toStatus;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReviewModerationEvent() {}

    public ReviewModerationEvent(UUID reviewId, ReviewStatus from, ReviewStatus to, String reason, UUID actorId, Instant now) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public ReviewStatus getFromStatus() { return fromStatus; }
    public ReviewStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public UUID getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
