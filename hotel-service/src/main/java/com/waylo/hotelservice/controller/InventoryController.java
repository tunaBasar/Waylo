package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.DailyInventoryResponse;
import com.waylo.hotelservice.dto.InventoryUpdateItem;
import com.waylo.hotelservice.dto.InventoryUpdateRequest;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotels/{hotelId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final HotelAccessGuard accessGuard;

    public InventoryController(InventoryService inventoryService, HotelAccessGuard accessGuard) {
        this.inventoryService = inventoryService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DailyInventoryResponse>>> getInventory(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestParam(value = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "roomTypeId", required = false) UUID roomTypeId) {

        accessGuard.requireRoomManage(userId, hotelId);

        // Validate date range after guard
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must not be after 'to' date");
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (daysBetween > 366) {
            throw new IllegalArgumentException("Date range must be at most 366 days inclusive");
        }

        List<DailyInventoryResponse> data = inventoryService.getInventory(hotelId, from, to, roomTypeId);
        return ResponseEntity.ok(ApiResponse.of(data, "Inventory retrieved"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<DailyInventoryResponse>>> updateInventory(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody InventoryUpdateRequest request) {

        accessGuard.requireRoomManage(userId, hotelId);

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Inventory update request must contain at least one item");
        }

        List<DailyInventoryResponse> data = inventoryService.updateInventory(hotelId, userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.of(data, "Inventory updated"));
    }
}
