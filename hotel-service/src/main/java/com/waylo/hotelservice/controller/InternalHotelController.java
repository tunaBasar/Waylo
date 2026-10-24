package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.service.HotelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Unfiltered hotel lookup for service-to-service callers (X-Internal-Token enforced by InternalTokenFilter).
 * auth-service uses it to verify a hotel's organization while granting roles, before the hotel is public.
 */
@RestController
@RequestMapping("/internal/hotels")
public class InternalHotelController {

    private final HotelService hotelService;

    public InternalHotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.getById(id)));
    }
}
