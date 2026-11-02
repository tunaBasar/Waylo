package com.waylo.flightseatinventoryservice.controller;

import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.supplier.InternalFlightSupplier;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.FlightOffer;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.OfferSearch;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Public offers. Offer ids are signed and short-lived, see FLIGHT-SUPPLIER-CONTRACT.md. */
@RestController
@RequestMapping("/api/flight-offers")
public class FlightOfferController {

    private final InternalFlightSupplier supplier;

    public FlightOfferController(InternalFlightSupplier supplier) { this.supplier = supplier; }

    @GetMapping
    public ApiResponse<List<FlightOffer>> search(
            @RequestParam String from, @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int adults) {
        return ApiResponse.of(supplier.searchOffers(new OfferSearch(from.trim().toUpperCase(), to.trim().toUpperCase(), date, adults)));
    }

    @GetMapping("/{offerId}")
    public ApiResponse<FlightOffer> get(@PathVariable String offerId) {
        return ApiResponse.of(supplier.getOffer(offerId));
    }
}
