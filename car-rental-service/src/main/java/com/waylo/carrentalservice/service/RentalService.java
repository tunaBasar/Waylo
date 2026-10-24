package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.AvailableVehicleResponse;
import com.waylo.carrentalservice.dto.CreateRentalRequest;
import com.waylo.carrentalservice.dto.RentalResponse;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ConflictException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.RentalRepository;
import com.waylo.carrentalservice.repository.VehicleLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RentalService {

    static final int MAX_DAYS = 30;

    private final RentalRepository rentalRepository;
    private final VehicleLockRepository vehicleLockRepository;
    private final Clock clock;

    public RentalService(RentalRepository rentalRepository, VehicleLockRepository vehicleLockRepository,
                         Clock clock) {
        this.rentalRepository = rentalRepository;
        this.vehicleLockRepository = vehicleLockRepository;
        this.clock = clock;
    }

    @Transactional
    public RentalResponse create(UUID userId, CreateRentalRequest req) {
        validateRange(req.pickupDate(), req.returnDate());

        Vehicle vehicle = vehicleLockRepository.findByIdForUpdate(req.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", req.vehicleId()));
        if (!vehicle.isActive() || vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            throw new BusinessRuleException("VEHICLE_NOT_RENTABLE", "Vehicle is not available for rental");
        }
        if (rentalRepository.existsOverlapping(vehicle.getId(), RentalStatus.RESERVED,
                req.pickupDate(), req.returnDate())) {
            throw new ConflictException("VEHICLE_NOT_AVAILABLE", "Vehicle is already booked for these dates");
        }

        long days = ChronoUnit.DAYS.between(req.pickupDate(), req.returnDate());
        Rental rental = new Rental();
        rental.setVehicleId(vehicle.getId());
        rental.setCustomerId(userId);
        rental.setPickupDate(req.pickupDate());
        rental.setReturnDate(req.returnDate());
        rental.setDailyRate(vehicle.getDailyRate());
        rental.setTotalPrice(vehicle.getDailyRate().multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP));
        rental.setStatus(RentalStatus.RESERVED);
        return RentalResponse.from(rentalRepository.save(rental));
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> listMine(UUID userId) {
        return rentalRepository.findByCustomerIdOrderByCreatedAtDesc(userId).stream()
                .map(RentalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RentalResponse get(UUID userId, boolean admin, UUID id) {
        return RentalResponse.from(findAccessible(userId, admin, id));
    }

    @Transactional
    public RentalResponse cancel(UUID userId, boolean admin, UUID id) {
        Rental rental = findAccessible(userId, admin, id);
        if (rental.getStatus() == RentalStatus.CANCELLED) {
            throw new BusinessRuleException("RENTAL_ALREADY_CANCELLED", "Rental is already cancelled");
        }
        if (!rental.getPickupDate().isAfter(today())) {
            throw new BusinessRuleException("RENTAL_NOT_CANCELLABLE", "Rental can no longer be cancelled");
        }
        rental.setStatus(RentalStatus.CANCELLED);
        return RentalResponse.from(rentalRepository.saveAndFlush(rental));
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> listByVehicle(boolean admin, UUID vehicleId) {
        if (!admin) {
            throw new ForbiddenException("Admin role required");
        }
        List<Rental> rentals = vehicleId == null
                ? rentalRepository.findAll()
                : rentalRepository.findByVehicleIdOrderByPickupDateDesc(vehicleId);
        return rentals.stream().map(RentalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableVehicleResponse> findAvailable(LocalDate from, LocalDate to, String location,
                                                        VehicleType type) {
        validateRange(from, to);
        return vehicleLockRepository.findAvailable(from, to, VehicleStatus.MAINTENANCE).stream()
                .filter(v -> location == null || location.isBlank() || v.getLocation().equalsIgnoreCase(location))
                .filter(v -> type == null || v.getVehicleType() == type)
                .map(AvailableVehicleResponse::from).toList();
    }

    /** Non-owners get 404 so existence is not leaked (also on cancel). */
    private Rental findAccessible(UUID userId, boolean admin, UUID id) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental", "id", id));
        if (!admin && !rental.getCustomerId().equals(userId)) {
            throw new ResourceNotFoundException("Rental", "id", id);
        }
        return rental;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private void validateRange(LocalDate pickup, LocalDate ret) {
        if (pickup == null || ret == null) {
            throw new IllegalArgumentException("Dates are required");
        }
        if (pickup.isBefore(today())) {
            throw new IllegalArgumentException("Pickup date must not be in the past");
        }
        if (!ret.isAfter(pickup)) {
            throw new IllegalArgumentException("Return date must be after pickup date");
        }
        if (ChronoUnit.DAYS.between(pickup, ret) > MAX_DAYS) {
            throw new IllegalArgumentException("Rental cannot exceed " + MAX_DAYS + " days");
        }
    }
}
