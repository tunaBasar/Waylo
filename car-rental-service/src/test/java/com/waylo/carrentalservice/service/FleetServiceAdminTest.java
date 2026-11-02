package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.AdminVehicleResponse;
import com.waylo.carrentalservice.repository.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

class FleetServiceAdminTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final CarSettings settings = mock(CarSettings.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final CategoryRateRepository rates = mock(CategoryRateRepository.class);
    private final OneWayRouteRepository routes = mock(OneWayRouteRepository.class);
    private final CarDocumentRepository documents = mock(CarDocumentRepository.class);
    private final RentalRepository rentals = mock(RentalRepository.class);

    private FleetService service() {
        when(settings.individualPartnersEnabled()).thenReturn(false);
        return new FleetService(auth, settings, clock, branches, vehicles, rates, routes, documents, rentals);
    }

    private OrgInfo org(String status, String legal) {
        UUID id = UUID.randomUUID();
        return new OrgInfo(id, "AcmeRent", status, "CAR_RENTAL", legal);
    }

    @Test
    void adminVehicles_enrichesWithOrgName() {
        FleetService svc = service();
        UUID orgId = UUID.randomUUID();
        UUID vid = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        when(vehicles.findAllByOrderByOrganizationIdAscPlateNumberAsc()).thenReturn(List.of(makeVehicle(vid, orgId, branchId, "Toyota", "Corolla")));
        when(auth.organization(orgId)).thenReturn(org("ACTIVE", "COMPANY"));
        when(branches.findByIdIn(anySet())).thenReturn(List.of(makeBranch(branchId, "Istanbul")));
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId)).thenReturn(List.of(
                makeDoc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, null),
                makeDoc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null)));
        when(documents.findByVehicleIdIn(anySet())).thenReturn(List.of(
                makeVehicleDoc(vid, DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid, DocumentType.RENTAL_INSURANCE, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid, DocumentType.TRAFFIC_INSURANCE, DocumentStatus.APPROVED, null)));

        List<AdminVehicleResponse> result = svc.adminVehicles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).organizationName()).isEqualTo("AcmeRent");
        assertThat(result.get(0).homeBranchName()).isEqualTo("Istanbul");
        assertThat(result.get(0).sellable()).isTrue();
        assertThat(result.get(0).missingDocuments()).isEmpty();
    }

    @Test
    void adminVehicles_computesSellableCorrectly() {
        FleetService svc = service();
        UUID orgId = UUID.randomUUID();
        UUID vid = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        when(vehicles.findAllByOrderByOrganizationIdAscPlateNumberAsc()).thenReturn(List.of(makeVehicle(vid, orgId, branchId, "Toyota", "Corolla")));
        when(auth.organization(orgId)).thenReturn(org("ACTIVE", "COMPANY"));
        when(branches.findByIdIn(anySet())).thenReturn(List.of(makeBranch(branchId, "Istanbul")));
        // Missing vehicle registration -> not sellable
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId)).thenReturn(List.of(
                makeDoc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, null),
                makeDoc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null)));
        when(documents.findByVehicleIdIn(anySet())).thenReturn(List.of(
                makeVehicleDoc(vid, DocumentType.RENTAL_INSURANCE, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid, DocumentType.TRAFFIC_INSURANCE, DocumentStatus.APPROVED, null)));

        List<AdminVehicleResponse> result = svc.adminVehicles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sellable()).isFalse();
        assertThat(result.get(0).missingDocuments()).containsExactly(DocumentType.VEHICLE_REGISTRATION);
    }

    @Test
    void adminVehicles_orgLookupFails_returnsNullOrg() {
        FleetService svc = service();
        UUID orgId = UUID.randomUUID();
        UUID vid = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        when(vehicles.findAllByOrderByOrganizationIdAscPlateNumberAsc()).thenReturn(List.of(makeVehicle(vid, orgId, branchId, "Toyota", "Corolla")));
        when(auth.organization(orgId)).thenThrow(new RuntimeException("auth unavailable"));
        when(branches.findByIdIn(anySet())).thenReturn(List.of(makeBranch(branchId, "Istanbul")));
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId)).thenReturn(List.of());
        when(documents.findByVehicleIdIn(anySet())).thenReturn(List.of());

        List<AdminVehicleResponse> result = svc.adminVehicles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).organizationName()).isNull();
    }

    @Test
    void adminVehicles_multipleOrgsAndVehicles() {
        FleetService svc = service();
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();
        UUID vid1 = UUID.randomUUID();
        UUID vid2 = UUID.randomUUID();
        UUID branchId1 = UUID.randomUUID();
        UUID branchId2 = UUID.randomUUID();

        when(vehicles.findAllByOrderByOrganizationIdAscPlateNumberAsc())
                .thenReturn(List.of(makeVehicle(vid1, orgId1, branchId1, "Toyota", "Corolla"),
                        makeVehicle(vid2, orgId2, branchId2, "Honda", "Civic")));
        when(auth.organization(orgId1)).thenReturn(org("ACTIVE", "COMPANY"));
        when(auth.organization(orgId2)).thenReturn(org("ACTIVE", "COMPANY"));
        when(branches.findByIdIn(anySet())).thenReturn(List.of(makeBranch(branchId1, "Istanbul"),
                makeBranch(branchId2, "Ankara")));

        List<CarDocument> org1Docs = List.of(makeDoc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, null),
                makeDoc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null));
        List<CarDocument> org2Docs = List.of(makeDoc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, null));

        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId1)).thenReturn(org1Docs);
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId2)).thenReturn(org2Docs);
        when(documents.findByVehicleIdIn(anySet())).thenReturn(List.of(
                makeVehicleDoc(vid1, DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid1, DocumentType.RENTAL_INSURANCE, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid1, DocumentType.TRAFFIC_INSURANCE, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid2, DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid2, DocumentType.RENTAL_INSURANCE, DocumentStatus.APPROVED, null),
                makeVehicleDoc(vid2, DocumentType.TRAFFIC_INSURANCE, DocumentStatus.APPROVED, null)));

        List<AdminVehicleResponse> result = svc.adminVehicles();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).organizationName()).isEqualTo("AcmeRent");
        assertThat(result.get(1).organizationName()).isEqualTo("AcmeRent");
        // vid1: org1 has all org docs + all vehicle docs -> sellable
        assertThat(result.get(0).sellable()).isTrue();
        // vid2: org2 missing TAX_CERTIFICATE -> not sellable
        assertThat(result.get(1).sellable()).isFalse();
    }

    private Vehicle makeVehicle(UUID id, UUID orgId, UUID branchId, String brand, String model) {
        Vehicle v = new Vehicle();
        v.id = id;
        v.organizationId = orgId;
        v.homeBranchId = branchId;
        v.plateNumber = "34ABC123";
        v.brand = brand;
        v.model = model;
        v.year = 2024;
        v.category = CarCategory.COMPACT;
        v.transmission = Transmission.AUTOMATIC;
        v.fuelType = FuelType.PETROL;
        v.seatCount = 5;
        v.active = true;
        v.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        v.updatedAt = Instant.parse("2026-01-01T00:00:00Z");
        return v;
    }

    private Branch makeBranch(UUID id, String name) {
        Branch b = new Branch();
        b.id = id;
        b.name = name;
        b.city = "Istanbul";
        b.organizationId = org("ACTIVE", "COMPANY").id();
        b.address = "Test";
        b.openTime = java.time.LocalTime.of(9, 0);
        b.closeTime = java.time.LocalTime.of(18, 0);
        b.active = true;
        b.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        b.updatedAt = Instant.parse("2026-01-01T00:00:00Z");
        return b;
    }

    private CarDocument makeDoc(DocumentType type, DocumentStatus status, java.time.LocalDate validUntil) {
        CarDocument d = new CarDocument();
        d.id = UUID.randomUUID();
        d.type = type;
        d.status = status;
        d.validUntil = validUntil;
        d.createdAt = Instant.parse("2026-12-01T00:00:00Z");
        d.updatedAt = Instant.parse("2026-12-01T00:00:00Z");
        return d;
    }

    private CarDocument makeVehicleDoc(UUID vehicleId, DocumentType type, DocumentStatus status, java.time.LocalDate validUntil) {
        CarDocument d = new CarDocument();
        d.id = UUID.randomUUID();
        d.vehicleId = vehicleId;
        d.type = type;
        d.status = status;
        d.validUntil = validUntil;
        d.createdAt = Instant.parse("2026-12-01T00:00:00Z");
        d.updatedAt = Instant.parse("2026-12-01T00:00:00Z");
        return d;
    }
}
