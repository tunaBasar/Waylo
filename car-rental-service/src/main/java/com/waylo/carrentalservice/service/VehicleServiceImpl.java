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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleServiceImpl(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    public VehicleResponse create(VehicleRequest request) {
        if (vehicleRepository.existsByPlateNumber(request.plateNumber())) {
            throw new DuplicateResourceException("Vehicle", "plateNumber", request.plateNumber());
        }
        Vehicle vehicle = vehicleMapper.toEntity(request);
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
        Vehicle vehicle = vehicleRepository.findByPlateNumber(plateNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "plateNumber", plateNumber));
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAll() {
        return vehicleRepository.findAll().stream().map(vehicleMapper::toResponse).toList();
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
        return stream.map(vehicleMapper::toResponse).toList();
    }

    @Override
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = findOrThrow(id);
        vehicleMapper.updateEntity(request, vehicle);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public void delete(UUID id) {
        if (!vehicleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Vehicle", "id", id);
        }
        vehicleRepository.deleteById(id);
    }

    @Override
    public VehicleResponse updateStatus(UUID id, VehicleStatus status) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setStatus(status);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse deactivate(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setActive(false);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse activate(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public long getAvailableCount(String location) {
        return vehicleRepository.countByLocationAndStatus(location, VehicleStatus.AVAILABLE);
    }

    private Vehicle findOrThrow(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));
    }
}
