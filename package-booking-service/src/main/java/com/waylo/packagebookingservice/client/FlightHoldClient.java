package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "flight-seat-inventory-service", contextId = "flightHoldClient", path = "/internal/flight-holds",
        configuration = FlightHoldFeignConfig.class)
public interface FlightHoldClient {
    @PostMapping Envelope<HoldDto> hold(@RequestBody HoldRequest request);
    @PostMapping("/{holdId}/confirm") Envelope<HoldDto> confirm(@PathVariable("holdId") UUID holdId);
    @PostMapping("/{holdId}/release") Envelope<HoldDto> release(@PathVariable("holdId") UUID holdId);

    record HoldRequest(String offerId, String seatNumber, String passengerFirstName, String passengerLastName,
                       UUID principalUserId, String idempotencyKey, String orderCorrelationId) {}
    record Envelope<T>(boolean success, T data, String message) {}
    record HoldDto(UUID holdId, String supplier, String flightNumber, String airline, String departureAirport,
                   String arrivalAirport, Instant departureTime, Instant arrivalTime, String cabin, String seatNumber,
                   String passengerFirstName, String passengerLastName, String currency, BigDecimal totalAmount,
                   BigDecimal supplierNetAmount, String status, Instant expiresAt, String pnr, String ticketNumber) {}
}
