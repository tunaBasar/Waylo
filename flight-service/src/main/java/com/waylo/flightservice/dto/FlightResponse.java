package com.waylo.flightservice.dto;

import com.waylo.flightservice.domain.FlightStatus;
import java.time.Instant;
import java.util.UUID;

public record FlightResponse(
        UUID id,
        String flightNumber,
        String departureAirportCode,
        String departureAirportName,
        String arrivalAirportCode,
        String arrivalAirportName,
        Instant departureTime,
        Instant arrivalTime,
        String airline,
        FlightStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
