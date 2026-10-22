package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.HotelCurrencyResponse;
import com.waylo.hotelservice.dto.RateUpdateRequest;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RateService;
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
@RequestMapping("/api/hotels/{hotelId}/rates")
public class RateController {

    private final RateService rateService;
    private final HotelAccessGuard accessGuard;

    public RateController(RateService rateService, HotelAccessGuard accessGuard) {
        this.rateService = rateService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DailyRateResponse>>> getRates(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) UUID ratePlanId) {

        accessGuard.requireRoomManage(userId, hotelId);
        List<DailyRateResponse> rates = rateService.getRates(hotelId, from, to, roomTypeId, ratePlanId);
        return ResponseEntity.ok(ApiResponse.of(rates));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<DailyRateResponse>>> updateRates(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody RateUpdateRequest request) {

        accessGuard.requireRoomManage(userId, hotelId);
        List<DailyRateResponse> response = rateService.updateRates(hotelId, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.of(response, "Rates updated"));
    }

    @GetMapping("/currency")
    public ResponseEntity<ApiResponse<HotelCurrencyResponse>> getCurrency(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {

        accessGuard.requireRoomManage(userId, hotelId);
        HotelCurrencyResponse response = rateService.getHotelCurrency(hotelId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
