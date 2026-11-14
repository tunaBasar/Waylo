package com.waylo.hotelservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Verified stay review. One per order item (unique). Score and text are immutable after creation. */
@Entity
@Table(name = "hotel_reviews")
public class HotelReview {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "score", nullable = false, updatable = false)
    private int score;

    @Column(name = "review_text", length = 2000, updatable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "partner_response", length = 2000)
    private String partnerResponse;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public HotelReview() {}

    public HotelReview(UUID hotelId, UUID orderItemId, UUID customerId, int score, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.hotelId = hotelId;
        this.orderItemId = orderItemId;
        this.customerId = customerId;
        this.score = score;
        this.text = text;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getHotelId() { return hotelId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getCustomerId() { return customerId; }
    public int getScore() { return score; }
    public String getText() { return text; }
    public ReviewStatus getStatus() { return status; }
    public String getPartnerResponse() { return partnerResponse; }
    public Instant getRespondedAt() { return respondedAt; }
    public UUID getRespondedBy() { return respondedBy; }
    public String getModerationReason() { return moderationReason; }
    public UUID getModeratedBy() { return moderatedBy; }
    public Instant getModeratedAt() { return moderatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void respond(String response, UUID by, Instant now) {
        this.partnerResponse = response;
        this.respondedBy = by;
        this.respondedAt = now;
    }

    public void moderate(ReviewStatus newStatus, String reason, UUID by, Instant now) {
        this.status = newStatus;
        this.moderationReason = reason;
        this.moderatedBy = by;
        this.moderatedAt = now;
    }
}
