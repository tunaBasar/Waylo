package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only bank account audit row. Never stores a full IBAN (last4 and HMAC only). */
@Entity
@Table(name = "partner_bank_account_changes")
public class BankAccountChange {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16, updatable = false)
    private BankAccountChangeType changeType;

    @Column(name = "old_last4", length = 4, columnDefinition = "bpchar(4)", updatable = false)
    private String oldLast4;

    @Column(name = "new_last4", length = 4, columnDefinition = "bpchar(4)", updatable = false)
    private String newLast4;

    @Column(name = "old_iban_hash", length = 64, columnDefinition = "bpchar(64)", updatable = false)
    private String oldIbanHash;

    @Column(name = "new_iban_hash", length = 64, columnDefinition = "bpchar(64)", updatable = false)
    private String newIbanHash;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8, updatable = false)
    private PayoutActorType actorType;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected BankAccountChange() {}

    public BankAccountChange(UUID bankAccountId, UUID organizationId, BankAccountChangeType changeType,
                             String oldLast4, String newLast4, String oldIbanHash, String newIbanHash,
                             UUID actorUserId, PayoutActorType actorType, String reason, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.bankAccountId = bankAccountId;
        this.organizationId = organizationId;
        this.changeType = changeType;
        this.oldLast4 = oldLast4;
        this.newLast4 = newLast4;
        this.oldIbanHash = oldIbanHash;
        this.newIbanHash = newIbanHash;
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getBankAccountId() { return bankAccountId; }
    public UUID getOrganizationId() { return organizationId; }
    public BankAccountChangeType getChangeType() { return changeType; }
    public String getOldLast4() { return oldLast4; }
    public String getNewLast4() { return newLast4; }
    public String getOldIbanHash() { return oldIbanHash; }
    public String getNewIbanHash() { return newIbanHash; }
    public UUID getActorUserId() { return actorUserId; }
    public PayoutActorType getActorType() { return actorType; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
