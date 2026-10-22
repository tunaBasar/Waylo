package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.AvailabilityResponse;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Internal availability endpoint - returns all sellable offers for a hotel and stay window.
 * Public endpoint (anonymous OK).
 */
@RestController
@RequestMapping("/api/hotels")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{hotelId}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> getAvailability(
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") int rooms,
            @RequestParam(defaultValue = "1") int adults,
            @RequestParam(required = false) List<Integer> childrenAges) {

        AvailabilityResponse response = availabilityService.getAvailability(
                hotelId, checkIn, checkOut, rooms, adults, childrenAges);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
