package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelHoldRequest;
import com.waylo.hotelservice.dto.HotelHoldResponse;
import com.waylo.hotelservice.service.HotelHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoints for hotel hold operations.
 * Access restricted by InternalTokenFilter.
 */
@RestController
@RequestMapping("/internal")
public class HotelHoldController {

    private final HotelHoldService hotelHoldService;

    public HotelHoldController(HotelHoldService hotelHoldService) {
        this.hotelHoldService = hotelHoldService;
    }

    @PostMapping("/hotel-holds")
    public ResponseEntity<ApiResponse<HotelHoldResponse>> hold(@Valid @RequestBody HotelHoldRequest request) {
        HotelHoldResponse response = hotelHoldService.hold(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Hold created"));
    }

    @PostMapping("/hotel-holds/{holdId}/confirm")
    public ResponseEntity<ApiResponse<HotelHoldResponse>> confirm(@PathVariable UUID holdId) {
        HotelHoldResponse response = hotelHoldService.confirm(holdId);
        return ResponseEntity.ok(ApiResponse.of(response, "Hold confirmed"));
    }

    @PostMapping("/hotel-holds/{holdId}/release")
    public ResponseEntity<ApiResponse<HotelHoldResponse>> release(@PathVariable UUID holdId) {
        HotelHoldResponse response = hotelHoldService.release(holdId);
        return ResponseEntity.ok(ApiResponse.of(response, "Hold released"));
    }

    @GetMapping("/hotel-holds/{holdId}")
    public ResponseEntity<ApiResponse<HotelHoldResponse>> getById(@PathVariable UUID holdId) {
        return ResponseEntity.ok(ApiResponse.of(hotelHoldService.findById(holdId)));
    }
}
