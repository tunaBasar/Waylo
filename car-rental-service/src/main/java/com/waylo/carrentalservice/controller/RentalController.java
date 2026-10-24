package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.CreateRentalRequest;
import com.waylo.carrentalservice.dto.RentalResponse;
import com.waylo.carrentalservice.service.RentalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final RentalService rentalService;

    public RentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RentalResponse>> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateRentalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(rentalService.create(userId, request), "Rental reserved"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RentalResponse>>> mine(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(rentalService.listMine(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RentalResponse>> get(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(rentalService.get(userId, isAdmin(role), id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RentalResponse>> cancel(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(rentalService.cancel(userId, isAdmin(role), id), "Rental cancelled"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RentalResponse>>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) UUID vehicleId) {
        return ResponseEntity.ok(ApiResponse.of(rentalService.listByVehicle(isAdmin(role), vehicleId)));
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
