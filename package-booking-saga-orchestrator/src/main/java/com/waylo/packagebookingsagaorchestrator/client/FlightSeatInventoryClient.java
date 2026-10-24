package com.waylo.packagebookingsagaorchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "flight-seat-inventory-service", path = "/api/seat-inventory")
public interface FlightSeatInventoryClient {

    @PostMapping("/seats/{flightNumber}/reserve")
    Object reserveSeat(@PathVariable String flightNumber,
                       @RequestHeader("X-User-Id") UUID userId,
                       @RequestBody SeatReservationRequest request);

    @DeleteMapping("/seats/{flightNumber}/reserve")
    void releaseSeat(@PathVariable String flightNumber,
                     @RequestHeader("X-User-Id") UUID userId,
                     @RequestBody SeatReservationRequest request);

    record SeatReservationRequest(String seatNumber) {}
}
