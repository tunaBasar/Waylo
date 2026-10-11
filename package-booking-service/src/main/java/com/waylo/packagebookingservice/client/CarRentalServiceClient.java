package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "car-rental-service", path = "/api/vehicles")
public interface CarRentalServiceClient {

    @GetMapping("/{id}")
    VehicleDto getById(@PathVariable UUID id);

    record VehicleDto(
            UUID id,
            String plateNumber,
            String brand,
            String model,
            String status,
            BigDecimal dailyRate,
            boolean active
    ) {}
}
