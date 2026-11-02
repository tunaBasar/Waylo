package com.waylo.packagebookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** seatNumber optional: omitted = cheapest free seat of the offered cabin. Names as on the ID. */
public record FlightOrderCreateRequest(
        @NotBlank @Size(max = 600) String offerId,
        @Size(max = 5) String seatNumber,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} '\\-]+$") String passengerFirstName,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} '\\-]+$") String passengerLastName,
        @Size(max = 50) String promoCode) {}
