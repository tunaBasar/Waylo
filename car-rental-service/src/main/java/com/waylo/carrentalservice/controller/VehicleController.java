package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.VehicleRequest;
import com.waylo.carrentalservice.dto.VehicleResponse;
import com.waylo.carrentalservice.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Vehicle created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.getById(id)));
    }

    @GetMapping("/plate/{plateNumber}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getByPlateNumber(@PathVariable String plateNumber) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.getByPlateNumber(plateNumber)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.getAll()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> search(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) BigDecimal maxDailyRate) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.search(location, vehicleType, status, maxDailyRate)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(@PathVariable UUID id,
                                                                @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.update(id, request), "Vehicle updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(@PathVariable UUID id,
                                                                      @RequestParam VehicleStatus status) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.updateStatus(id, status), "Vehicle status updated"));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<VehicleResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.deactivate(id), "Vehicle deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<VehicleResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.activate(id), "Vehicle activated"));
    }

    @GetMapping("/available-count")
    public ResponseEntity<ApiResponse<Long>> getAvailableCount(@RequestParam String location) {
        return ResponseEntity.ok(ApiResponse.of(vehicleService.getAvailableCount(location)));
    }
}
