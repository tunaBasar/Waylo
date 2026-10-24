package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.AvailableVehicleResponse;
import com.waylo.carrentalservice.service.RentalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleAvailabilityController {

    private final RentalService rentalService;

    public VehicleAvailabilityController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<AvailableVehicleResponse>>> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) VehicleType vehicleType) {
        return ResponseEntity.ok(ApiResponse.of(rentalService.findAvailable(from, to, location, vehicleType)));
    }
}
