package com.waylo.packagebookingsagaorchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "flight-seat-inventory-service", path = "/api/seat-inventory")
public interface FlightSeatInventoryClient {

    @PostMapping("/seats/{flightNumber}/reserve")
    Object reserveSeat(@PathVariable String flightNumber,
                       @RequestBody SeatReservationRequest request);

    @DeleteMapping("/seats/{flightNumber}/reserve")
    void releaseSeat(@PathVariable String flightNumber,
                     @RequestBody SeatReservationRequest request);

    record SeatReservationRequest(String seatNumber) {}
}
