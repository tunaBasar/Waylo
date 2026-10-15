package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RoomRequest;
import com.waylo.hotelservice.dto.RoomResponse;
import com.waylo.hotelservice.service.RoomService;
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
@RequestMapping("/api/hotels/{hotelId}/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(@PathVariable UUID hotelId,
                                                             @Valid @RequestBody RoomRequest request) {
        RoomResponse response = roomService.create(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Room created"));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> getById(@PathVariable UUID hotelId,
                                                              @PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.of(roomService.getById(hotelId, roomId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getByHotelId(@PathVariable UUID hotelId) {
        return ResponseEntity.ok(ApiResponse.of(roomService.getByHotelId(hotelId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> search(
            @PathVariable UUID hotelId,
            @RequestParam(required = false) RoomType roomType,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return ResponseEntity.ok(ApiResponse.of(roomService.search(hotelId, roomType, status, maxPrice)));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> update(@PathVariable UUID hotelId,
                                                             @PathVariable UUID roomId,
                                                             @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(ApiResponse.of(roomService.update(hotelId, roomId, request), "Room updated"));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable UUID hotelId,
                                       @PathVariable UUID roomId) {
        roomService.delete(hotelId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/status")
    public ResponseEntity<ApiResponse<RoomResponse>> updateStatus(@PathVariable UUID hotelId,
                                                                   @PathVariable UUID roomId,
                                                                   @RequestParam RoomStatus status) {
        return ResponseEntity.ok(ApiResponse.of(roomService.updateStatus(hotelId, roomId, status), "Room status updated"));
    }

    @GetMapping("/available-count")
    public ResponseEntity<ApiResponse<Long>> getAvailableCount(@PathVariable UUID hotelId) {
        return ResponseEntity.ok(ApiResponse.of(roomService.getAvailableRoomCount(hotelId)));
    }
}
