package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.domain.Vehicle;
import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.VehicleMapper;
import com.waylo.carrentalservice.dto.VehicleRequest;
import com.waylo.carrentalservice.dto.VehicleResponse;
import com.waylo.carrentalservice.exception.DuplicateResourceException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.VehicleRepository;
import com.waylo.carrentalservice.repository.RentalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.time.Year;

@Service
@Transactional
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final RentalRepository rentalRepository;

    public VehicleServiceImpl(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper, RentalRepository rentalRepository) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
        this.rentalRepository = rentalRepository;
    }

    @Override
    public VehicleResponse create(VehicleRequest request) {
        VehicleRequest normalized = normalize(request);
        validateYear(normalized.year());
        if (vehicleRepository.existsByPlateNumber(normalized.plateNumber())) {
            throw new DuplicateResourceException("Vehicle", "plateNumber", normalized.plateNumber());
        }
        Vehicle vehicle = vehicleMapper.toEntity(normalized);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getById(UUID id) {
        return vehicleMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getByPlateNumber(String plateNumber) {
        Vehicle vehicle = vehicleRepository.findByPlateNumber(normalizePlate(plateNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "plateNumber", plateNumber));
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAll() {
        return vehicleRepository.findByActiveTrue().stream().map(vehicleMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> search(String location, VehicleType vehicleType,
                                         VehicleStatus status, BigDecimal maxDailyRate) {
        Stream<Vehicle> stream;
        if (location != null && vehicleType != null && status != null) {
            stream = vehicleRepository.findByLocationAndVehicleTypeAndStatus(location, vehicleType, status).stream();
        } else if (location != null && status != null) {
            stream = vehicleRepository.findByLocationAndStatus(location, status).stream();
        } else if (location != null) {
            stream = vehicleRepository.findByLocation(location).stream();
        } else if (vehicleType != null) {
            stream = vehicleRepository.findByVehicleType(vehicleType).stream();
        } else if (status != null) {
            stream = vehicleRepository.findByStatus(status).stream();
        } else {
            stream = vehicleRepository.findAll().stream();
        }
        if (maxDailyRate != null) {
            stream = stream.filter(v -> v.getDailyRate().compareTo(maxDailyRate) <= 0);
        }
        return stream.filter(Vehicle::isActive).map(vehicleMapper::toResponse).toList();
    }

    @Override
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = findOrThrow(id);
        VehicleRequest normalized = normalize(request);
        validateYear(normalized.year());
        if (!normalized.currency().equals(vehicle.getCurrency()) && rentalRepository.existsFutureReserved(vehicle.getId(), java.time.LocalDate.now()))
            throw new com.waylo.carrentalservice.exception.BusinessRuleException("VEHICLE_HAS_BOOKINGS", "Vehicle has future bookings");
        if (vehicleRepository.existsByPlateNumberAndIdNot(normalized.plateNumber(), id))
            throw new DuplicateResourceException("Vehicle", "plateNumber", normalized.plateNumber());
        vehicleMapper.updateEntity(normalized, vehicle);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public void delete(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        if (vehicle.isActive()) throw new com.waylo.carrentalservice.exception.BusinessRuleException("VEHICLE_ACTIVE", "Vehicle must be inactive before deletion");
        vehicleRepository.deleteById(id);
    }

    @Override
    public VehicleResponse updateStatus(UUID id, VehicleStatus status) {
        if (status != VehicleStatus.AVAILABLE && status != VehicleStatus.MAINTENANCE)
            throw new IllegalArgumentException("Status may only be AVAILABLE or MAINTENANCE");
        Vehicle vehicle = findOrThrow(id);
        vehicle.setStatus(status);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse deactivate(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setActive(false);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse activate(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setActive(true);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public long getAvailableCount(String location) {
        return vehicleRepository.countByLocationAndStatusAndActiveTrue(location, VehicleStatus.AVAILABLE);
    }

    private VehicleRequest normalize(VehicleRequest r) {
        return new VehicleRequest(normalizePlate(r.plateNumber()), r.brand().trim(), r.model().trim(), r.year(), r.vehicleType(),
                r.color() == null ? null : r.color().trim(), r.seatCount(), r.dailyRate(), r.currency().trim().toUpperCase(), r.location().trim(), r.mileage());
    }
    private String normalizePlate(String plate) { return plate.trim().replaceAll("\\s+", "").toUpperCase(); }
    private void validateYear(Integer year) { if (year < 1990 || year > Year.now().getValue() + 1) throw new IllegalArgumentException("Year must be between 1990 and next year"); }

    private Vehicle findOrThrow(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));
    }
}
