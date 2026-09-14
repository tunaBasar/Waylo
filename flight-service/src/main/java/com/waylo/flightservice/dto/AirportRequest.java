package com.waylo.flightservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AirportRequest(
        @NotBlank(message = "IATA code is required")
        @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
        String iataCode,

        @NotBlank(message = "Airport name is required")
        String name,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Country is required")
        String country,

        @NotBlank(message = "Timezone is required")
        String timezone,

        BigDecimal latitude,
        BigDecimal longitude
) {}
