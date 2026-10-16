package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RoomTypeCreateRequest;
import com.waylo.hotelservice.dto.RoomTypeResponse;
import com.waylo.hotelservice.dto.RoomTypeUpdateRequest;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RoomTypeService;
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
@RequestMapping("/api/hotels/{hotelId}/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;
    private final HotelAccessGuard accessGuard;

    public RoomTypeController(RoomTypeService roomTypeService, HotelAccessGuard accessGuard) {
        this.roomTypeService = roomTypeService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomTypeResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody RoomTypeCreateRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        RoomTypeResponse response = roomTypeService.create(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Room type created"));
    }

    @GetMapping("/{roomTypeId}")
    public ResponseEntity<ApiResponse<RoomTypeResponse>> getById(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (includeInactive) {
            accessGuard.requireRoomManage(userId, hotelId);
        }
        RoomTypeResponse response = roomTypeService.getById(hotelId, roomTypeId, includeInactive);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomTypeResponse>>> getByHotelId(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (includeInactive) {
            accessGuard.requireRoomManage(userId, hotelId);
        }
        List<RoomTypeResponse> responses = roomTypeService.getByHotelId(hotelId, includeInactive);
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

    @PatchMapping("/{roomTypeId}")
    public ResponseEntity<ApiResponse<RoomTypeResponse>> update(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody RoomTypeUpdateRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        RoomTypeResponse response = roomTypeService.update(hotelId, roomTypeId, request);
        return ResponseEntity.ok(ApiResponse.of(response, "Room type updated"));
    }
}
