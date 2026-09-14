package com.waylo.flightservice.dto;

import com.waylo.flightservice.domain.FlightStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FlightRequest(
        @NotBlank(message = "Flight number is required")
        @Size(max = 10, message = "Flight number must be at most 10 characters")
        String flightNumber,

        @NotBlank(message = "Departure airport IATA code is required")
        @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
        String departureAirportCode,

        @NotBlank(message = "Arrival airport IATA code is required")
        @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
        String arrivalAirportCode,

        @NotNull(message = "Departure time is required")
        Instant departureTime,

        @NotNull(message = "Arrival time is required")
        Instant arrivalTime,

        @NotBlank(message = "Airline is required")
        String airline,

        FlightStatus status
) {}
