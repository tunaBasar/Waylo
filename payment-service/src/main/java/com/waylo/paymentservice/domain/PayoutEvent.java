package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only decision/audit row, one per payout transition. No setters: the database rejects UPDATE and DELETE. */
@Entity
@Table(name = "payout_events")
public class PayoutEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payout_id", nullable = false, updatable = false)
    private UUID payoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16, updatable = false)
    private PayoutStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16, updatable = false)
    private PayoutStatus toStatus;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8, updatable = false)
    private PayoutActorType actorType;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "reference", length = 100, updatable = false)
    private String reference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PayoutEvent() {}

    public PayoutEvent(UUID payoutId, PayoutStatus fromStatus, PayoutStatus toStatus, UUID actorUserId,
                       PayoutActorType actorType, String reason, String reference, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.payoutId = payoutId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.reason = reason;
        this.reference = reference;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getPayoutId() { return payoutId; }
    public PayoutStatus getFromStatus() { return fromStatus; }
    public PayoutStatus getToStatus() { return toStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public PayoutActorType getActorType() { return actorType; }
    public String getReason() { return reason; }
    public String getReference() { return reference; }
    public Instant getOccurredAt() { return occurredAt; }
}
