package com.waylo.packagebookingservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Hotel order request. Deliberately has no price, currency or product fields: all of them come from the hotel hold. */
public record HotelOrderCreateRequest(
        @NotNull(message = "Quote id is required")
        UUID quoteId,

        @Size(max = 1000, message = "Notes must be at most 1000 characters")
        String notes
) {}
