package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.HoldDto;
import com.waylo.carrentalservice.dto.CarDtos.HoldRequest;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ConflictException;
import com.waylo.carrentalservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HoldServiceTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final CategoryRateRepository rates = mock(CategoryRateRepository.class);
    private final OneWayRouteRepository routes = mock(OneWayRouteRepository.class);
    private final CarDocumentRepository documents = mock(CarDocumentRepository.class);
    private final RentalRepository rentals = mock(RentalRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC);

    private final UUID orgId = UUID.randomUUID();
    private Branch branch;
    private Vehicle vehicle;
    private HoldService service;

    @BeforeEach
    void setUp() {
        branch = new Branch();
        branch.id = UUID.randomUUID();
        branch.organizationId = orgId;
        branch.openTime = LocalTime.of(8, 0);
        branch.closeTime = LocalTime.of(20, 0);
        branch.active = true;
        vehicle = new Vehicle();
        vehicle.id = UUID.randomUUID();
        vehicle.organizationId = orgId;
        vehicle.homeBranchId = branch.id;
        vehicle.brand = "Fiat";
        vehicle.model = "Egea";
        service = new HoldService(auth, new CarSettings(false, 15), clock, branches, vehicles, rates, routes, documents, rentals);

        when(branches.findById(branch.id)).thenReturn(Optional.of(branch));
        when(auth.lookupOrEmpty(orgId)).thenReturn(Optional.of(new OrgInfo(orgId, "Acme", "ACTIVE", "CAR_RENTAL", "COMPANY")));
        when(rentals.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(rentals.saveAndFlush(any(Rental.class))).thenAnswer(i -> i.getArgument(0));
        when(rentals.save(any(Rental.class))).thenAnswer(i -> i.getArgument(0));
        when(vehicles.findById(vehicle.id)).thenReturn(Optional.of(vehicle));
    }

    private void sellableSetup() {
        LocalDate far = LocalDate.of(2030, 1, 1);
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId)).thenReturn(List.of(
                doc(DocumentType.RENTAL_AUTHORIZATION, null, far), doc(DocumentType.TAX_CERTIFICATE, null, null)));
        when(documents.findByVehicleIdIn(any())).thenReturn(List.of(
                doc(DocumentType.VEHICLE_REGISTRATION, vehicle.id, far),
                doc(DocumentType.RENTAL_INSURANCE, vehicle.id, far),
                doc(DocumentType.TRAFFIC_INSURANCE, vehicle.id, far)));
        CategoryRate rate = new CategoryRate();
        rate.dailyRate = new BigDecimal("100.00");
        rate.depositAmount = new BigDecimal("500.00");
        rate.currency = "TRY";
        rate.minDriverAge = 21;
        when(rates.findByBranchIdAndCategoryAndTransmission(any(), any(), any())).thenReturn(Optional.of(rate));
        when(vehicles.lockCandidates(any(), any(), any())).thenReturn(List.of(vehicle));
        when(rentals.findBlocking(any(), any())).thenReturn(List.of());
    }

    private CarDocument doc(DocumentType type, UUID vehicleId, LocalDate until) {
        CarDocument d = new CarDocument();
        d.type = type;
        d.vehicleId = vehicleId;
        d.validUntil = until;
        d.status = DocumentStatus.APPROVED;
        d.createdAt = Instant.EPOCH;
        return d;
    }

    private HoldRequest request(String key, int age) {
        LocalDateTime pickup = LocalDateTime.of(2027, 2, 10, 10, 0);
        return new HoldRequest(branch.id, branch.id, CarCategory.ECONOMY, Transmission.MANUAL, pickup,
                pickup.plusDays(1).plusMinutes(30), age, UUID.randomUUID(), key, "corr");
    }

    @Test
    void hold_createsHeldRentalWithPricingFromRate() {
        sellableSetup();
        HoldDto dto = service.hold(request("k1", 30));
        assertThat(dto.status()).isEqualTo(RentalStatus.HELD);
        assertThat(dto.chargeableDays()).isEqualTo(2);
        assertThat(dto.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(dto.expiresAt()).isEqualTo(clock.instant().plusSeconds(15 * 60));
        assertThat(dto.exampleModel()).isEqualTo("Fiat Egea veya benzeri");
    }

    @Test
    void hold_sameIdempotencyKeyReturnsExistingHold() {
        Rental existing = new Rental();
        existing.id = UUID.randomUUID();
        existing.vehicleId = vehicle.id;
        existing.pickupAt = Instant.parse("2027-02-10T07:00:00Z");
        existing.returnAt = Instant.parse("2027-02-11T07:00:00Z");
        existing.status = RentalStatus.HELD;
        existing.holdExpiresAt = clock.instant().plusSeconds(60);
        existing.totalAmount = BigDecimal.TEN;
        when(rentals.findByIdempotencyKey("dup")).thenReturn(Optional.of(existing));

        HoldDto dto = service.hold(request("dup", 30));

        assertThat(dto.holdId()).isEqualTo(existing.id);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    void hold_noFreeVehicle_is409() {
        sellableSetup();
        Rental overlapping = new Rental();
        overlapping.vehicleId = vehicle.id;
        overlapping.pickupBranchId = branch.id;
        overlapping.returnBranchId = branch.id;
        overlapping.pickupAt = Instant.parse("2027-02-10T00:00:00Z");
        overlapping.returnAt = Instant.parse("2027-02-12T00:00:00Z");
        when(rentals.findBlocking(any(), any())).thenReturn(List.of(overlapping));

        assertThatThrownBy(() -> service.hold(request("k2", 30)))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("NO_VEHICLE_AVAILABLE"));
    }

    @Test
    void hold_driverTooYoung_is422() {
        sellableSetup();
        assertThatThrownBy(() -> service.hold(request("k3", 19)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("DRIVER_TOO_YOUNG"));
    }

    @Test
    void hold_individualPartnerWithGateClosed_is422() {
        sellableSetup();
        when(auth.lookupOrEmpty(orgId)).thenReturn(Optional.of(new OrgInfo(orgId, "Ali", "ACTIVE", "CAR_RENTAL", "INDIVIDUAL")));
        assertThatThrownBy(() -> service.hold(request("k4", 30)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("INDIVIDUAL_PARTNERS_DISABLED"));
    }

    @Test
    void hold_authDown_failsClosedAsNotSellable() {
        sellableSetup();
        when(auth.lookupOrEmpty(orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hold(request("k5", 30)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("PARTNER_NOT_SELLABLE"));
    }

    @Test
    void hold_missingOrgDocument_isNotSellable() {
        sellableSetup();
        when(documents.findByOrganizationIdAndVehicleIdIsNull(orgId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.hold(request("k6", 30)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("PARTNER_NOT_SELLABLE"));
    }

    @Test
    void hold_oneWayWithoutRoute_is422() {
        sellableSetup();
        Branch other = new Branch();
        other.id = UUID.randomUUID();
        other.organizationId = orgId;
        other.openTime = branch.openTime;
        other.closeTime = branch.closeTime;
        other.active = true;
        when(branches.findById(other.id)).thenReturn(Optional.of(other));
        when(routes.findByFromBranchIdAndToBranchId(branch.id, other.id)).thenReturn(Optional.empty());
        LocalDateTime pickup = LocalDateTime.of(2027, 2, 10, 10, 0);
        HoldRequest req = new HoldRequest(branch.id, other.id, CarCategory.ECONOMY, Transmission.MANUAL, pickup,
                pickup.plusDays(1), 30, UUID.randomUUID(), "k7", null);
        assertThatThrownBy(() -> service.hold(req))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("ROUTE_NOT_OFFERED"));
    }

    private Rental rental(RentalStatus status, Instant expires) {
        Rental r = new Rental();
        r.id = UUID.randomUUID();
        r.vehicleId = vehicle.id;
        r.pickupAt = Instant.parse("2027-02-10T07:00:00Z");
        r.returnAt = Instant.parse("2027-02-11T07:00:00Z");
        r.status = status;
        r.holdExpiresAt = expires;
        r.totalAmount = BigDecimal.TEN;
        when(rentals.findById(r.id)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void confirm_unexpiredHeld_becomesConfirmed() {
        Rental r = rental(RentalStatus.HELD, clock.instant().plusSeconds(60));
        assertThat(service.confirm(r.id).status()).isEqualTo(RentalStatus.CONFIRMED);
    }

    @Test
    void confirm_expiredHold_isHoldExpired() {
        Rental r = rental(RentalStatus.HELD, clock.instant().minusSeconds(1));
        assertThatThrownBy(() -> service.confirm(r.id))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));
    }

    @Test
    void confirm_alreadyExpiredStatus_isHoldExpired() {
        Rental r = rental(RentalStatus.EXPIRED, clock.instant().minusSeconds(100));
        assertThatThrownBy(() -> service.confirm(r.id))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));
    }

    @Test
    void release_isIdempotent() {
        Rental r = rental(RentalStatus.CONFIRMED, clock.instant().plusSeconds(60));
        assertThat(service.release(r.id).status()).isEqualTo(RentalStatus.CANCELLED);
        assertThat(service.release(r.id).status()).isEqualTo(RentalStatus.CANCELLED);
    }

    @Test
    void expireOverdue_marksHeldRentalsExpired() {
        Rental r = rental(RentalStatus.HELD, clock.instant().minusSeconds(5));
        when(rentals.findExpiredHolds(clock.instant())).thenReturn(List.of(r));
        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(r.status).isEqualTo(RentalStatus.EXPIRED);
    }
}
