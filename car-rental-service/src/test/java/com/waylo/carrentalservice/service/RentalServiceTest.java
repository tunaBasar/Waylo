package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CreateRentalRequest;
import com.waylo.carrentalservice.dto.RentalResponse;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ConflictException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.RentalRepository;
import com.waylo.carrentalservice.repository.VehicleLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RentalServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    RentalRepository rentalRepository = mock(RentalRepository.class);
    VehicleLockRepository lockRepository = mock(VehicleLockRepository.class);
    Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    RentalService service;

    UUID user = UUID.randomUUID();
    UUID vehicleId = UUID.randomUUID();
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new RentalService(rentalRepository, lockRepository, clock);
        vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setDailyRate(new BigDecimal("450.50"));
        vehicle.setActive(true);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        when(lockRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.save(any(Rental.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateRentalRequest req(LocalDate p, LocalDate r) {
        return new CreateRentalRequest(vehicleId, p, r);
    }

    @Test
    void createComputesPriceAndLocksBeforeOverlapCheck() {
        RentalResponse res = service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(4)));

        assertThat(res.totalPrice()).isEqualByComparingTo("1351.50");
        assertThat(res.dailyRate()).isEqualByComparingTo("450.50");
        assertThat(res.customerId()).isEqualTo(user);
        assertThat(res.status()).isEqualTo(RentalStatus.RESERVED);
        InOrder order = inOrder(lockRepository, rentalRepository);
        order.verify(lockRepository).findByIdForUpdate(vehicleId);
        order.verify(rentalRepository).existsOverlapping(eq(vehicleId), eq(RentalStatus.RESERVED), any(), any());
    }

    @Test
    void pickupTodayIsAllowed() {
        assertThat(service.create(user, req(TODAY, TODAY.plusDays(1))).totalPrice()).isEqualByComparingTo("450.50");
    }

    @Test
    void pastPickupRejected() {
        assertThatThrownBy(() -> service.create(user, req(TODAY.minusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnNotAfterPickupRejected() {
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(2), TODAY.plusDays(2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(3), TODAY.plusDays(2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thirtyOneDaysRejectedThirtyAllowed() {
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(32))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(31))).totalPrice())
                .isEqualByComparingTo("13515.00");
    }

    @Test
    void missingVehicleIs404() {
        UUID other = UUID.randomUUID();
        when(lockRepository.findByIdForUpdate(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(user, new CreateRentalRequest(other, TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveVehicleNotRentable() {
        vehicle.setActive(false);
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("VEHICLE_NOT_RENTABLE");
    }

    @Test
    void maintenanceVehicleNotRentable() {
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void overlapIs409AndNothingSaved() {
        when(rentalRepository.existsOverlapping(eq(vehicleId), eq(RentalStatus.RESERVED), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(user, req(TODAY.plusDays(1), TODAY.plusDays(3))))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("VEHICLE_NOT_AVAILABLE");
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adjacentBookingAllowedAndOnlyReservedChecked() {
        // existing booking ends on day+3, new one picks up day+3: query is asked with exact dates, repo says free
        LocalDate pickup = TODAY.plusDays(3);
        when(rentalRepository.existsOverlapping(vehicleId, RentalStatus.RESERVED, pickup, pickup.plusDays(2)))
                .thenReturn(false);
        assertThat(service.create(user, req(pickup, pickup.plusDays(2))).status()).isEqualTo(RentalStatus.RESERVED);
        verify(rentalRepository).existsOverlapping(vehicleId, RentalStatus.RESERVED, pickup, pickup.plusDays(2));
        verify(rentalRepository, never()).existsOverlapping(any(), eq(RentalStatus.CANCELLED), any(), any());
    }

    private Rental rental(UUID owner, LocalDate pickup, RentalStatus status) {
        Rental r = new Rental();
        r.setId(UUID.randomUUID());
        r.setCustomerId(owner);
        r.setVehicleId(vehicleId);
        r.setPickupDate(pickup);
        r.setReturnDate(pickup.plusDays(2));
        r.setDailyRate(BigDecimal.TEN);
        r.setTotalPrice(BigDecimal.valueOf(20));
        r.setStatus(status);
        when(rentalRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(rentalRepository.saveAndFlush(r)).thenReturn(r);
        return r;
    }

    @Test
    void getByOwnerAndAdminOkStrangerNotFound() {
        Rental r = rental(user, TODAY.plusDays(5), RentalStatus.RESERVED);
        assertThat(service.get(user, false, r.getId()).id()).isEqualTo(r.getId());
        assertThat(service.get(UUID.randomUUID(), true, r.getId()).id()).isEqualTo(r.getId());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), false, r.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelByOwner() {
        Rental r = rental(user, TODAY.plusDays(5), RentalStatus.RESERVED);
        assertThat(service.cancel(user, false, r.getId()).status()).isEqualTo(RentalStatus.CANCELLED);
    }

    @Test
    void cancelByAdmin() {
        Rental r = rental(user, TODAY.plusDays(5), RentalStatus.RESERVED);
        assertThat(service.cancel(UUID.randomUUID(), true, r.getId()).status()).isEqualTo(RentalStatus.CANCELLED);
    }

    @Test
    void cancelByStrangerIs404() {
        Rental r = rental(user, TODAY.plusDays(5), RentalStatus.RESERVED);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), false, r.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(r.getStatus()).isEqualTo(RentalStatus.RESERVED);
    }

    @Test
    void cancelOnOrAfterPickupIs422() {
        Rental today = rental(user, TODAY, RentalStatus.RESERVED);
        Rental past = rental(user, TODAY.minusDays(1), RentalStatus.RESERVED);
        assertThatThrownBy(() -> service.cancel(user, false, today.getId())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.cancel(user, false, past.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelTwiceIs422() {
        Rental r = rental(user, TODAY.plusDays(5), RentalStatus.RESERVED);
        service.cancel(user, false, r.getId());
        assertThatThrownBy(() -> service.cancel(user, false, r.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminListForbiddenForUser() {
        assertThatThrownBy(() -> service.listByVehicle(false, vehicleId)).isInstanceOf(ForbiddenException.class);
        when(rentalRepository.findByVehicleIdOrderByPickupDateDesc(vehicleId)).thenReturn(List.of());
        assertThat(service.listByVehicle(true, vehicleId)).isEmpty();
    }

    @Test
    void availabilityValidatesRangeAndFilters() {
        assertThatThrownBy(() -> service.findAvailable(TODAY.plusDays(3), TODAY.plusDays(1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        vehicle.setLocation("Istanbul");
        vehicle.setVehicleType(VehicleType.values()[0]);
        when(lockRepository.findAvailable(any(), any(), eq(VehicleStatus.MAINTENANCE))).thenReturn(List.of(vehicle));
        assertThat(service.findAvailable(TODAY, TODAY.plusDays(2), "istanbul", null)).hasSize(1);
        assertThat(service.findAvailable(TODAY, TODAY.plusDays(2), "Ankara", null)).isEmpty();
    }
}
