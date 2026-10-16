package com.waylo.hotelservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RateUpdateRequest(
        @NotEmpty @Size(max = 500) @Valid List<RateUpdateItem> items
) {}
