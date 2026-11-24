package com.waylo.hotelservice.publication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_publication_requests")
public class HotelPublicationRequest {

    public enum Kind { INITIAL, CONTENT_CHANGE }

    public enum Status { SUBMITTED, APPROVED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.SUBMITTED;

    @Column(name = "revision_id", updatable = false)
    private UUID revisionId;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "submit_note", length = 1000)
    private String submitNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UUID getId() { return id; }
    public UUID getHotelId() { return hotelId; }
    public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getRevisionId() { return revisionId; }
    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public String getSubmitNote() { return submitNote; }
    public void setSubmitNote(String submitNote) { this.submitNote = submitNote; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public long getVersion() { return version == null ? 0L : version; }
}
