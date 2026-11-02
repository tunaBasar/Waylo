package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.AdminDocumentResponse;
import com.waylo.carrentalservice.repository.BranchRepository;
import com.waylo.carrentalservice.repository.CarDocumentRepository;
import com.waylo.carrentalservice.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceAdminTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final CarDocumentRepository documents = mock(CarDocumentRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC);
    private final CarSettings settings = mock(CarSettings.class);

    private DocumentService service(Path dir) {
        return new DocumentService(auth, documents, vehicles, branches, clock, settings, dir.toString());
    }

    @Test
    void adminList_enrichesWithOrgInfo(@TempDir Path dir) throws Exception {
        DocumentService svc = service(dir);
        UUID orgId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(settings.individualPartnersEnabled()).thenReturn(false);
        when(auth.organization(orgId)).thenReturn(new OrgInfo(orgId, "AcmeRent", "ACTIVE", "CAR_RENTAL", "COMPANY"));

        CarDocument d = new CarDocument();
        d.id = docId;
        d.organizationId = orgId;
        d.vehicleId = null;
        d.type = DocumentType.RENTAL_AUTHORIZATION;
        d.documentNumber = "YB-1";
        d.validUntil = LocalDate.of(2028, 1, 1);
        d.status = DocumentStatus.PENDING;
        d.uploadedBy = orgId;
        d.createdAt = Instant.parse("2027-01-15T10:00:00Z");
        d.updatedAt = Instant.parse("2027-01-15T10:00:00Z");

        when(documents.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d));

        List<AdminDocumentResponse> result = svc.adminList(null);

        assertThat(result).hasSize(1);
        AdminDocumentResponse r = result.get(0);
        assertThat(r.organizationName()).isEqualTo("AcmeRent");
        assertThat(r.legalType()).isEqualTo("COMPANY");
        assertThat(r.vehicleLabel()).isNull();
    }

    @Test
    void adminList_enrichesWithVehicleLabel(@TempDir Path dir) throws Exception {
        DocumentService svc = service(dir);
        UUID orgId = UUID.randomUUID();
        UUID vid = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(settings.individualPartnersEnabled()).thenReturn(false);
        when(auth.organization(orgId)).thenReturn(new OrgInfo(orgId, "AcmeRent", "ACTIVE", "CAR_RENTAL", "COMPANY"));

        CarDocument d = new CarDocument();
        d.id = docId;
        d.organizationId = orgId;
        d.vehicleId = vid;
        d.type = DocumentType.VEHICLE_REGISTRATION;
        d.documentNumber = "RUH-1";
        d.validUntil = LocalDate.of(2028, 6, 1);
        d.status = DocumentStatus.PENDING;
        d.uploadedBy = orgId;
        d.createdAt = Instant.parse("2027-01-20T12:00:00Z");
        d.updatedAt = Instant.parse("2027-01-20T12:00:00Z");

        Vehicle v = new Vehicle();
        v.id = vid;
        v.plateNumber = "34WYL10";
        v.brand = "Toyota";
        v.model = "Corolla";

        when(documents.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d));
        when(vehicles.findById(vid)).thenReturn(java.util.Optional.of(v));

        List<AdminDocumentResponse> result = svc.adminList(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vehicleLabel()).isEqualTo("34WYL10 Toyota Corolla");
        assertThat(result.get(0).organizationName()).isEqualTo("AcmeRent");
    }

    @Test
    void adminList_sortsPendingFirstThenNewest(@TempDir Path dir) throws Exception {
        DocumentService svc = service(dir);
        UUID orgId = UUID.randomUUID();

        when(settings.individualPartnersEnabled()).thenReturn(false);
        when(auth.organization(orgId)).thenReturn(new OrgInfo(orgId, "AcmeRent", "ACTIVE", "CAR_RENTAL", "COMPANY"));

        CarDocument approved = new CarDocument();
        approved.id = UUID.randomUUID();
        approved.organizationId = orgId;
        approved.vehicleId = null;
        approved.type = DocumentType.TAX_CERTIFICATE;
        approved.status = DocumentStatus.APPROVED;
        approved.uploadedBy = orgId;
        approved.createdAt = Instant.parse("2027-02-01T00:00:00Z");

        CarDocument pendingOld = new CarDocument();
        pendingOld.id = UUID.randomUUID();
        pendingOld.organizationId = orgId;
        pendingOld.vehicleId = null;
        pendingOld.type = DocumentType.RENTAL_AUTHORIZATION;
        pendingOld.status = DocumentStatus.PENDING;
        pendingOld.uploadedBy = orgId;
        pendingOld.createdAt = Instant.parse("2027-01-01T00:00:00Z");

        CarDocument pendingNew = new CarDocument();
        pendingNew.id = UUID.randomUUID();
        pendingNew.organizationId = orgId;
        pendingNew.vehicleId = null;
        pendingNew.type = DocumentType.IDENTITY_DOCUMENT;
        pendingNew.status = DocumentStatus.PENDING;
        pendingNew.uploadedBy = orgId;
        pendingNew.createdAt = Instant.parse("2027-02-01T12:00:00Z");

        when(documents.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(approved, pendingOld, pendingNew));

        List<AdminDocumentResponse> result = svc.adminList(null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.get(1).status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.get(2).status()).isEqualTo(DocumentStatus.APPROVED);
    }

    @Test
    void adminList_statusFilter(@TempDir Path dir) throws Exception {
        DocumentService svc = service(dir);
        UUID orgId = UUID.randomUUID();

        when(settings.individualPartnersEnabled()).thenReturn(false);
        when(auth.organization(orgId)).thenReturn(new OrgInfo(orgId, "Acme", "ACTIVE", "CAR_RENTAL", "COMPANY"));

        CarDocument pending = new CarDocument();
        pending.id = UUID.randomUUID();
        pending.organizationId = orgId;
        pending.vehicleId = null;
        pending.type = DocumentType.RENTAL_AUTHORIZATION;
        pending.status = DocumentStatus.PENDING;
        pending.uploadedBy = orgId;
        pending.createdAt = Instant.parse("2027-01-15T10:00:00Z");

        when(documents.findByStatusOrderByCreatedAt(DocumentStatus.PENDING)).thenReturn(List.of(pending));

        List<AdminDocumentResponse> result = svc.adminList(DocumentStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void adminList_orgLookupFails_returnsNullOrg(@TempDir Path dir) throws Exception {
        DocumentService svc = service(dir);
        UUID orgId = UUID.randomUUID();

        when(settings.individualPartnersEnabled()).thenReturn(false);
        when(auth.organization(orgId)).thenThrow(new RuntimeException("auth unavailable"));

        CarDocument d = new CarDocument();
        d.id = UUID.randomUUID();
        d.organizationId = orgId;
        d.vehicleId = null;
        d.type = DocumentType.RENTAL_AUTHORIZATION;
        d.status = DocumentStatus.PENDING;
        d.uploadedBy = orgId;
        d.createdAt = Instant.parse("2027-01-15T10:00:00Z");

        when(documents.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d));

        List<AdminDocumentResponse> result = svc.adminList(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).organizationName()).isNull();
    }
}
