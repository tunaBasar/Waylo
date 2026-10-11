package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record HotelRequest(
        @NotBlank(message = "Hotel name is required")
        String name,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Country is required")
        String country,

        @NotBlank(message = "Address is required")
        String address,

        @NotNull(message = "Star rating is required")
        @Min(value = 1, message = "Star rating must be at least 1")
        @Max(value = 5, message = "Star rating must be at most 5")
        Integer starRating,

        String description,

        BigDecimal latitude,
        BigDecimal longitude
) {}
