package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RatePlanCreateRequest;
import com.waylo.hotelservice.dto.RatePlanResponse;
import com.waylo.hotelservice.dto.RatePlanUpdateRequest;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RatePlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotels/{hotelId}/rate-plans")
public class RatePlanController {

    private final RatePlanService ratePlanService;
    private final HotelAccessGuard accessGuard;

    public RatePlanController(RatePlanService ratePlanService, HotelAccessGuard accessGuard) {
        this.ratePlanService = ratePlanService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RatePlanResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody RatePlanCreateRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        RatePlanResponse response = ratePlanService.create(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Rate plan created"));
    }

    @GetMapping("/{ratePlanId}")
    public ResponseEntity<ApiResponse<RatePlanResponse>> getById(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID ratePlanId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (includeInactive) {
            accessGuard.requireRoomManage(userId, hotelId);
        }
        RatePlanResponse response = ratePlanService.getById(hotelId, ratePlanId, includeInactive);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RatePlanResponse>>> getByHotelId(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (includeInactive) {
            accessGuard.requireRoomManage(userId, hotelId);
        }
        List<RatePlanResponse> responses = ratePlanService.getByHotelId(hotelId, includeInactive);
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

    @PatchMapping("/{ratePlanId}")
    public ResponseEntity<ApiResponse<RatePlanResponse>> update(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID ratePlanId,
            @Valid @RequestBody RatePlanUpdateRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        RatePlanResponse response = ratePlanService.update(hotelId, ratePlanId, request);
        return ResponseEntity.ok(ApiResponse.of(response, "Rate plan updated"));
    }
}
