package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.VehicleRequest;
import com.waylo.carrentalservice.dto.VehicleResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface VehicleService {

    VehicleResponse create(VehicleRequest request);

    VehicleResponse getById(UUID id);

    VehicleResponse getByPlateNumber(String plateNumber);

    List<VehicleResponse> getAll();

    /**
     * Konum, tip, durum ve fiyat filtresiyle arac arama.
     */
    List<VehicleResponse> search(String location, VehicleType vehicleType,
                                  VehicleStatus status, BigDecimal maxDailyRate);

    VehicleResponse update(UUID id, VehicleRequest request);

    void delete(UUID id);

    VehicleResponse updateStatus(UUID id, VehicleStatus status);

    VehicleResponse deactivate(UUID id);

    VehicleResponse activate(UUID id);

    long getAvailableCount(String location);
}
