package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.HotelService;
import com.waylo.hotelservice.service.HotelSearchService;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;
    private final HotelAccessGuard accessGuard;
    private final HotelSearchService hotelSearchService;

    public HotelController(HotelService hotelService, HotelAccessGuard accessGuard, HotelSearchService hotelSearchService) {
        this.hotelService = hotelService;
        this.accessGuard = accessGuard;
        this.hotelSearchService = hotelSearchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HotelResponse>> create(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                              @Valid @RequestBody HotelRequest request) {
        accessGuard.requireCreate(userId, request.organizationId());
        HotelResponse response = hotelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Hotel created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelResponse>> getById(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                               @RequestHeader(value = "X-User-Role", required = false) String role,
                                                               @PathVariable UUID id) {
        HotelResponse response = hotelService.getById(id);
        if (!hotelService.isPublic(id) && !accessGuard.canReadOrganization(userId, role, response.organizationId())) {
            // Unpublished hotels are invisible to everyone but the owning organization's members and ADMIN.
            throw new ResourceNotFoundException("Hotel", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HotelResponse>>> getAll(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) UUID organizationId) {
        List<HotelResponse> hotels;
        if (organizationId == null) {
            hotels = hotelService.getAll();
        } else if (accessGuard.canReadOrganization(userId, role, organizationId)) {
            hotels = hotelService.getByOrganization(organizationId);
        } else {
            hotels = hotelService.getPublicByOrganization(organizationId);
        }
        return ResponseEntity.ok(ApiResponse.of(hotels));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HotelResponse>>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer minStars,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") int rooms,
            @RequestParam(defaultValue = "1") int adults,
            @RequestParam(required = false) List<Integer> childrenAges) {
        if (checkIn == null && checkOut == null) {
            return ResponseEntity.ok(ApiResponse.of(hotelService.search(city, country, minStars)));
        }
        return ResponseEntity.ok(ApiResponse.of(hotelSearchService.search(
                city, country, minStars, checkIn, checkOut, rooms, adults, childrenAges)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelResponse>> update(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody HotelRequest request) {
        accessGuard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(hotelService.update(id, request), "Hotel updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                        @PathVariable UUID id) {
        accessGuard.requireHotelManage(userId, id);
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<HotelResponse>> deactivate(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                                  @PathVariable UUID id) {
        accessGuard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(hotelService.deactivate(id), "Hotel deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<HotelResponse>> activate(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                                @PathVariable UUID id) {
        accessGuard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(hotelService.activate(id), "Hotel activated"));
    }
}
