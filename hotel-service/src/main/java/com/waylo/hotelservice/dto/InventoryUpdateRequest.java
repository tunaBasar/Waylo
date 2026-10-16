package com.waylo.hotelservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryUpdateRequest(
        @Size(max = 500) String reason,
        @NotEmpty @Size(max = 400) @Valid List<InventoryUpdateItem> items
) {}
