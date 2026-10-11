package com.waylo.checkinservice.controller;

import com.waylo.checkinservice.dto.ApiResponse;
import com.waylo.checkinservice.dto.CheckinRequest;
import com.waylo.checkinservice.dto.CheckinResponse;
import com.waylo.checkinservice.service.CheckinService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {
    private final CheckinService checkinService;
    public CheckinController(CheckinService checkinService) { this.checkinService = checkinService; }

    @PostMapping
    public ResponseEntity<ApiResponse<CheckinResponse>> checkIn(@Valid @RequestBody CheckinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(checkinService.checkIn(request), "Checked in"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CheckinResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(checkinService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CheckinResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(checkinService.getAll()));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<CheckinResponse>>> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.of(checkinService.getByBookingId(bookingId)));
    }

    @GetMapping("/flight/{flightId}")
    public ResponseEntity<ApiResponse<List<CheckinResponse>>> getByFlightId(@PathVariable UUID flightId) {
        return ResponseEntity.ok(ApiResponse.of(checkinService.getByFlightId(flightId)));
    }

    @PostMapping("/{id}/boarding-pass")
    public ResponseEntity<ApiResponse<CheckinResponse>> issueBoardingPass(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(checkinService.issueBoardingPass(id), "Boarding pass issued"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CheckinResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(checkinService.cancel(id), "Check-in cancelled"));
    }
}
