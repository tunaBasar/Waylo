package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos.AdminDocumentResponse;
import com.waylo.busservice.dto.BusDtos.DocumentResponse;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ResourceNotFoundException;
import com.waylo.busservice.repository.BusDocumentRepository;
import com.waylo.busservice.repository.BusRepository;
import com.waylo.busservice.rules.DocumentFiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.waylo.busservice.exception.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    public record StoredFile(byte[] content, String contentType, String fileName) { }

    private final AuthGateway auth;
    private final BusDocumentRepository documents;
    private final BusRepository buses;
        private final Clock clock;
        private final Path baseDir;

    public DocumentService(AuthGateway auth, BusDocumentRepository documents, BusRepository buses,
                           Clock clock,
                           @Value("${waylo.bus.documents-dir:/data/bus-documents}") String baseDir) {
        this.auth = auth;
        this.documents = documents;
        this.buses = buses;
        this.clock = clock;
        this.baseDir = Path.of(baseDir);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId, UUID orgId) {
        auth.requireBusManager(userId, orgId);
        return documents.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream().map(DocumentResponse::of).toList();
    }

    @Transactional
    public DocumentResponse upload(UUID userId, UUID orgId, MultipartFile file, DocumentType type,
                                   String documentNumber, LocalDate validUntil, UUID busId) {
        OrgInfo org = auth.requireBusManager(userId, orgId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Uploaded file is empty");
        }
        if (file.getSize() > DocumentFiles.MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the 10 MB limit");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNREADABLE_FILE", "Uploaded file could not be read");
        }
        String contentType = DocumentFiles.detectContentType(content);
        if (documentNumber == null || documentNumber.isBlank() || documentNumber.length() > 100) {
            throw new BusinessRuleException("INVALID_DOCUMENT", "documentNumber is required (max 100 characters)");
        }
        if (type.isBusLevel()) {
            if (busId == null) {
                throw new BusinessRuleException("INVALID_DOCUMENT", "busId is required for " + type);
            }
            buses.findById(busId).filter(v -> v.organizationId.equals(orgId))
                    .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + busId));
        } else {
            if (busId != null) {
                throw new BusinessRuleException("INVALID_DOCUMENT", "busId must be empty for " + type);
            }
        }
        if (type.isValidUntilRequired() && validUntil == null) {
            throw new BusinessRuleException("INVALID_DOCUMENT", "validUntil is required for " + type);
        }

        BusDocument d = new BusDocument();
        d.id = UUID.randomUUID();
        d.organizationId = orgId;
        d.busId = busId;
        d.type = type;
        d.documentNumber = documentNumber.trim();
        d.validUntil = validUntil;
        d.status = DocumentStatus.PENDING;
        d.fileName = DocumentFiles.sanitizeName(file.getOriginalFilename());
        d.contentType = contentType;
        d.sizeBytes = content.length;
        d.storageKey = UUID.randomUUID().toString();
        d.uploadedBy = userId;
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(d.storageKey), content);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Document storage is unavailable");
        }
        return DocumentResponse.of(documents.save(d));
    }

    @Transactional(readOnly = true)
    public StoredFile partnerFile(UUID userId, UUID orgId, UUID id) {
        auth.requireBusManager(userId, orgId);
        BusDocument d = documents.findById(id).filter(x -> x.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        return read(d);
    }

    @Transactional(readOnly = true)
    public StoredFile adminFile(UUID id) {
        return read(documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id)));
    }

    private StoredFile read(BusDocument d) {
        try {
            return new StoredFile(Files.readAllBytes(baseDir.resolve(d.storageKey)), d.contentType, d.fileName);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Document file is not available: " + d.id);
        }
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public List<AdminDocumentResponse> adminList(DocumentStatus status) {

        // Collect distinct org IDs and vehicle IDs for batch lookup
        List<BusDocument> raw = status == null ? documents.findAllByOrderByCreatedAtDesc()
                : documents.findByStatusOrderByCreatedAt(status);
        Set<UUID> orgIds = raw.stream().map(d -> d.organizationId).collect(Collectors.toSet());
        Set<UUID> busIds = raw.stream().filter(d -> d.busId != null).map(d -> d.busId).collect(Collectors.toSet());

        // Cache org lookups per request
        Map<UUID, OrgInfo> orgCache = new HashMap<>();
        for (UUID orgId : orgIds) {
            try {
                orgCache.put(orgId, auth.organization(orgId));
            } catch (Exception e) {
                // lookup fails -> null org
            }
        }

        // Vehicle labels cache
        Map<UUID, String> busLabels = new HashMap<>();
        for (UUID bid : busIds) {
            buses.findById(bid).ifPresent(v ->
                busLabels.put(bid, v.plateNumber + " " + v.brand + " " + v.model));
        }

        // Sort: PENDING first, then newest createdAt
        List<BusDocument> sorted = raw.stream()
                .sorted(Comparator.comparing((BusDocument d) -> d.status == DocumentStatus.PENDING ? 0 : 1)
                        .thenComparing(Comparator.comparing((BusDocument d) -> d.createdAt == null ? Instant.EPOCH : d.createdAt).reversed()))
                .toList();

        return sorted.stream().map(d -> {
            DocumentResponse dr = DocumentResponse.of(d);
            OrgInfo org = orgCache.get(d.organizationId);
            String orgName = org != null ? org.name() : null;
                        String busLabel = (d.busId != null && busLabels.containsKey(d.busId))
                    ? busLabels.get(d.busId) : null;
            return new AdminDocumentResponse(dr, orgName, busLabel);
        }).toList();
    }

    @Transactional
    public DocumentResponse approve(UUID adminId, UUID id) {
        BusDocument d = reviewable(id, DocumentStatus.APPROVED);
        d.status = DocumentStatus.APPROVED;
        d.rejectReason = null;
        stamp(d, adminId);
        return DocumentResponse.of(documents.save(d));
    }

    @Transactional
    public DocumentResponse reject(UUID adminId, UUID id, String reason) {
        BusDocument d = reviewable(id, DocumentStatus.REJECTED);
        d.status = DocumentStatus.REJECTED;
        d.rejectReason = reason.trim();
        stamp(d, adminId);
        return DocumentResponse.of(documents.save(d));
    }

    private BusDocument reviewable(UUID id, DocumentStatus target) {
        BusDocument d = documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        if (d.status == target) {
            throw new com.waylo.busservice.exception.ConflictException("DOCUMENT_ALREADY_REVIEWED",
                    "Document was already reviewed");
        }
        return d;
    }

    private void stamp(BusDocument d, UUID adminId) {
        d.reviewedBy = adminId;
        d.reviewedAt = clock.instant();
    }
}
