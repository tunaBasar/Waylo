package com.waylo.busservice.controller;

import com.waylo.busservice.dto.ApiResponse;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.service.BusService;
import com.waylo.busservice.service.HoldService;
import com.waylo.busservice.service.OfferService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class BusPublicController {

    private final OfferService offers;
    private final HoldService holds;
    private final BusService service;
    private final com.waylo.busservice.config.BusSettings settings;

    public BusPublicController(OfferService offers, HoldService holds, BusService service,
                               com.waylo.busservice.config.BusSettings settings) {
        this.settings = settings;
        this.offers = offers;
        this.holds = holds;
        this.service = service;
    }

    /** Lets the customer UI show "not open yet" instead of an empty search (legal gate). */
    @GetMapping("/api/bus-sales-status")
    public ApiResponse<java.util.Map<String, Boolean>> salesStatus() {
        return ApiResponse.of(java.util.Map.of("enabled", settings.salesEnabled()));
    }

    @GetMapping("/api/bus-terminals")
    public ApiResponse<List<TerminalResponse>> terminals() {
        return ApiResponse.of(settings.salesEnabled() ? service.listTerminals(true) : List.of());
    }

    @GetMapping("/api/bus-trips")
    public ApiResponse<List<TripOffer>> trips(@RequestParam String fromCity, @RequestParam String toCity,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.of(offers.search(fromCity, toCity, date));
    }

    @GetMapping("/api/bus-trips/{id}/seats")
    public ApiResponse<SeatMap> seats(@PathVariable UUID id, @RequestParam int fromSeq, @RequestParam int toSeq) {
        return ApiResponse.of(offers.seatMap(id, fromSeq, toSeq));
    }

    @GetMapping("/api/bus-bookings/me")
    public ApiResponse<List<BookingResponse>> mine(@RequestHeader("X-User-Id") UUID user) {
        return ApiResponse.of(holds.mine(user));
    }

    @GetMapping("/api/bus-bookings/{id}")
    public ApiResponse<BookingResponse> get(@RequestHeader("X-User-Id") UUID user,
                                            @RequestHeader(value = "X-User-Role", required = false) String role,
                                            @PathVariable UUID id) {
        return ApiResponse.of(holds.get(id, user, "ADMIN".equals(role)));
    }
}
