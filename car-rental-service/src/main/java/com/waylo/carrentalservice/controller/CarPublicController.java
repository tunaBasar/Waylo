package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.CarDtos.*;
import com.waylo.carrentalservice.service.HoldService;
import com.waylo.carrentalservice.service.OfferService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class CarPublicController {

    private final OfferService offers;
    private final HoldService holds;

    public CarPublicController(OfferService offers, HoldService holds) {
        this.offers = offers;
        this.holds = holds;
    }

    @GetMapping("/api/car-branches")
    public ApiResponse<List<PublicBranchResponse>> branches(@RequestParam(value = "city", required = false) String city) {
        return ApiResponse.of(offers.sellableBranches(city));
    }

    @GetMapping("/api/car-offers")
    public ApiResponse<List<OfferResponse>> offers(@RequestParam UUID pickupBranchId, @RequestParam UUID returnBranchId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime pickupAt,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime returnAt,
                                                   @RequestParam int driverAge) {
        return ApiResponse.of(offers.offers(pickupBranchId, returnBranchId, pickupAt, returnAt, driverAge));
    }

    @GetMapping("/api/rentals/me")
    public ApiResponse<List<RentalResponse>> mine(@RequestHeader("X-User-Id") UUID user) {
        return ApiResponse.of(holds.mine(user));
    }

    @GetMapping("/api/rentals/{id}")
    public ApiResponse<RentalResponse> get(@RequestHeader("X-User-Id") UUID user,
                                           @RequestHeader(value = "X-User-Role", required = false) String role,
                                           @PathVariable UUID id) {
        return ApiResponse.of(holds.get(id, user, "ADMIN".equals(role)));
    }
}
