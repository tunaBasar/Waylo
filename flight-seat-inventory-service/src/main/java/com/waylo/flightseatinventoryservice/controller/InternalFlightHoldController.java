package com.waylo.flightseatinventoryservice.controller;

import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.FlightDtos.HoldRequest;
import com.waylo.flightseatinventoryservice.supplier.InternalFlightSupplier;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.FlightHold;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.HoldCommand;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.RefundQuote;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/flight-holds")
public class InternalFlightHoldController {

    private final InternalFlightSupplier supplier;

    public InternalFlightHoldController(InternalFlightSupplier supplier) { this.supplier = supplier; }

    @PostMapping
    public ApiResponse<FlightHold> hold(@Valid @RequestBody HoldRequest r) {
        return ApiResponse.of(supplier.hold(new HoldCommand(r.offerId(), r.seatNumber(), r.passengerFirstName(),
                r.passengerLastName(), r.principalUserId(), r.idempotencyKey(), r.orderCorrelationId())));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlightHold> get(@PathVariable UUID id) { return ApiResponse.of(supplier.getHold(id)); }

    @PostMapping("/{id}/confirm")
    public ApiResponse<FlightHold> confirm(@PathVariable UUID id) { return ApiResponse.of(supplier.issueTicket(id)); }

    @PostMapping("/{id}/release")
    public ApiResponse<FlightHold> release(@PathVariable UUID id) { return ApiResponse.of(supplier.cancel(id)); }

    @GetMapping("/{id}/refund-quote")
    public ApiResponse<RefundQuote> refundQuote(@PathVariable UUID id) { return ApiResponse.of(supplier.refundQuote(id)); }
}
