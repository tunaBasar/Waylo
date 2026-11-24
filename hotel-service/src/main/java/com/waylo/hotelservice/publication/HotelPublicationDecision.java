package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.domain.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit row. No setters after creation are used and the table has no UPDATE/DELETE policy. */
@Entity
@Table(name = "hotel_publication_decisions")
public class HotelPublicationDecision {

    public enum Type { SUBMIT, WITHDRAW, APPROVE, REJECT, PUBLISH, SUSPEND, REINSTATE, ARCHIVE, MIGRATION }

    public enum ActorRole { ADMIN, PARTNER, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 24)
    private Type decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 16)
    private PublicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 16)
    private PublicationStatus toStatus;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false, length = 16)
    private ActorRole actorRole;

    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public HotelPublicationDecision() {}

    public HotelPublicationDecision(UUID hotelId, UUID organizationId, UUID requestId, Type decision,
                                    PublicationStatus fromStatus, PublicationStatus toStatus, UUID actorUserId,
                                    ActorRole actorRole, String reason, Instant createdAt) {
        this.hotelId = hotelId;
        this.organizationId = organizationId;
        this.requestId = requestId;
        this.decision = decision;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getHotelId() { return hotelId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getRequestId() { return requestId; }
    public Type getDecision() { return decision; }
    public PublicationStatus getFromStatus() { return fromStatus; }
    public PublicationStatus getToStatus() { return toStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public ActorRole getActorRole() { return actorRole; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
