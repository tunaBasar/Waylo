package com.waylo.crmservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Append-only (DB trigger rejects UPDATE/DELETE). Every column is non-updatable here too. */
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    public enum Role { CUSTOMER, PARTNER, ADMIN }

    @Id
    private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;
    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10, updatable = false)
    private Role authorRole;
    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;
    @Column(nullable = false, length = 4000, updatable = false)
    private String body;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {}

    public SupportMessage(UUID caseId, Role authorRole, UUID authorUserId, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.authorRole = authorRole;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public Role getAuthorRole() { return authorRole; }
    public UUID getAuthorUserId() { return authorUserId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
