package com.waylo.busservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class BusDocument extends Audited {
    @Id public UUID id;
    @Column(name = "organization_id", nullable = false) public UUID organizationId;
    @Column(name = "bus_id") public UUID busId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public DocumentType type;
    @Column(name = "document_number", nullable = false) public String documentNumber;
    @Column(name = "valid_until") public LocalDate validUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public DocumentStatus status;
    @Column(name = "reject_reason") public String rejectReason;
    @Column(name = "reviewed_by") public UUID reviewedBy;
    @Column(name = "reviewed_at") public Instant reviewedAt;
    @Column(name = "file_name", nullable = false) public String fileName;
    @Column(name = "content_type", nullable = false) public String contentType;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(name = "storage_key", nullable = false) public String storageKey;
    @Column(name = "uploaded_by", nullable = false) public UUID uploadedBy;
    @Version public Long version;
}
