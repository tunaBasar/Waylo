package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.AdminDocumentResponse;
import com.waylo.carrentalservice.dto.CarDtos.DocumentResponse;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.BranchRepository;
import com.waylo.carrentalservice.repository.CarDocumentRepository;
import com.waylo.carrentalservice.repository.VehicleRepository;
import com.waylo.carrentalservice.rules.DocumentFiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.waylo.carrentalservice.exception.ApiException;

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
    private final CarDocumentRepository documents;
    private final VehicleRepository vehicles;
    private final BranchRepository branches;
    private final Clock clock;
    private final CarSettings settings;
    private final Path baseDir;

    public DocumentService(AuthGateway auth, CarDocumentRepository documents, VehicleRepository vehicles,
                           BranchRepository branches, Clock clock, CarSettings settings,
                           @Value("${waylo.car.documents-dir:/data/car-documents}") String baseDir) {
        this.auth = auth;
        this.documents = documents;
        this.vehicles = vehicles;
        this.branches = branches;
        this.clock = clock;
        this.settings = settings;
        this.baseDir = Path.of(baseDir);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId, UUID orgId) {
        auth.requireFleetManager(userId, orgId);
        return documents.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream().map(DocumentResponse::of).toList();
    }

    @Transactional
    public DocumentResponse upload(UUID userId, UUID orgId, MultipartFile file, DocumentType type,
                                   String documentNumber, LocalDate validUntil, UUID vehicleId) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
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
        if (type.isVehicleLevel()) {
            if (vehicleId == null) {
                throw new BusinessRuleException("INVALID_DOCUMENT", "vehicleId is required for " + type);
            }
            vehicles.findById(vehicleId).filter(v -> v.organizationId.equals(orgId))
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        } else {
            if (vehicleId != null) {
                throw new BusinessRuleException("INVALID_DOCUMENT", "vehicleId must be empty for " + type);
            }
            if (type != DocumentType.TAX_CERTIFICATE || org.individual()) {
                if (!DocumentType.requiredForOrganization(org.legalType()).contains(type)) {
                    throw new BusinessRuleException("DOCUMENT_TYPE_NOT_ALLOWED",
                            type + " is not allowed for a " + org.legalType() + " organization");
                }
            }
        }
        if (type.isValidUntilRequired() && validUntil == null) {
            throw new BusinessRuleException("INVALID_DOCUMENT", "validUntil is required for " + type);
        }

        CarDocument d = new CarDocument();
        d.id = UUID.randomUUID();
        d.organizationId = orgId;
        d.vehicleId = vehicleId;
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
        auth.requireFleetManager(userId, orgId);
        CarDocument d = documents.findById(id).filter(x -> x.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        return read(d);
    }

    @Transactional(readOnly = true)
    public StoredFile adminFile(UUID id) {
        return read(documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id)));
    }

    private StoredFile read(CarDocument d) {
        try {
            return new StoredFile(Files.readAllBytes(baseDir.resolve(d.storageKey)), d.contentType, d.fileName);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Document file is not available: " + d.id);
        }
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public List<AdminDocumentResponse> adminList(DocumentStatus status) {
        LocalDate today = LocalDate.now(clock.withZone(CarSettings.ZONE));

        // Collect distinct org IDs and vehicle IDs for batch lookup
        List<CarDocument> raw = status == null ? documents.findAllByOrderByCreatedAtDesc()
                : documents.findByStatusOrderByCreatedAt(status);
        Set<UUID> orgIds = raw.stream().map(d -> d.organizationId).collect(Collectors.toSet());
        Set<UUID> vehicleIds = raw.stream().filter(d -> d.vehicleId != null).map(d -> d.vehicleId).collect(Collectors.toSet());

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
        Map<UUID, String> vehicleLabels = new HashMap<>();
        for (UUID vid : vehicleIds) {
            vehicles.findById(vid).ifPresent(v ->
                vehicleLabels.put(vid, v.plateNumber + " " + v.brand + " " + v.model));
        }

        // Sort: PENDING first, then newest createdAt
        List<CarDocument> sorted = raw.stream()
                .sorted(Comparator.comparing((CarDocument d) -> d.status == DocumentStatus.PENDING ? 0 : 1)
                        .thenComparing(Comparator.comparing((CarDocument d) -> d.createdAt == null ? Instant.EPOCH : d.createdAt).reversed()))
                .toList();

        return sorted.stream().map(d -> {
            DocumentResponse dr = DocumentResponse.of(d);
            OrgInfo org = orgCache.get(d.organizationId);
            String orgName = org != null ? org.name() : null;
            String legalType = org != null ? org.legalType() : null;
            String vehicleLabel = (d.vehicleId != null && vehicleLabels.containsKey(d.vehicleId))
                    ? vehicleLabels.get(d.vehicleId) : null;
            return AdminDocumentResponse.of(dr, orgName, legalType, vehicleLabel);
        }).toList();
    }

    @Transactional
    public DocumentResponse approve(UUID adminId, UUID id) {
        CarDocument d = reviewable(id, DocumentStatus.APPROVED);
        d.status = DocumentStatus.APPROVED;
        d.rejectReason = null;
        stamp(d, adminId);
        return DocumentResponse.of(documents.save(d));
    }

    @Transactional
    public DocumentResponse reject(UUID adminId, UUID id, String reason) {
        CarDocument d = reviewable(id, DocumentStatus.REJECTED);
        d.status = DocumentStatus.REJECTED;
        d.rejectReason = reason.trim();
        stamp(d, adminId);
        return DocumentResponse.of(documents.save(d));
    }

    private CarDocument reviewable(UUID id, DocumentStatus target) {
        CarDocument d = documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        if (d.status == target) {
            throw new com.waylo.carrentalservice.exception.ConflictException("DOCUMENT_ALREADY_REVIEWED",
                    "Document was already reviewed");
        }
        return d;
    }

    private void stamp(CarDocument d, UUID adminId) {
        d.reviewedBy = adminId;
        d.reviewedAt = clock.instant();
    }
}
