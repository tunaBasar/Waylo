package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "flight-service", path = "/api/flights")
public interface FlightServiceClient {

    @GetMapping("/{id}")
    FlightDto getById(@PathVariable UUID id);

    record FlightDto(
            UUID id,
            String flightNumber,
            String airline,
            String status,
            Instant departureTime,
            Instant arrivalTime
    ) {}
}
