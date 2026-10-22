package com.waylo.flightservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FlightRequest(
        @NotBlank(message = "Flight number is required")
        @Size(max = 10, message = "Flight number must be at most 10 characters")
        String flightNumber,

        @NotBlank(message = "Departure airport IATA code is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "IATA code must be exactly 3 letters")
        String departureAirportCode,

        @NotBlank(message = "Arrival airport IATA code is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "IATA code must be exactly 3 letters")
        String arrivalAirportCode,

        @NotBlank(message = "Airline is required")
        @Size(max = 100, message = "Airline must be at most 100 characters")
        String airline,

        @NotNull(message = "Departure time is required")
        Instant departureTime,

        @NotNull(message = "Arrival time is required")
        Instant arrivalTime
) {}
