package com.waylo.flightservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AirportRequest(
        @NotBlank(message = "IATA code is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "IATA code must be exactly 3 letters")
        String iataCode,

        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 255, message = "City must be at most 255 characters")
        String city,

        @Size(max = 255, message = "Country must be at most 255 characters")
        String country,

        @Size(max = 64, message = "Timezone must be at most 64 characters")
        String timezone,

        @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
        BigDecimal longitude
) {}
