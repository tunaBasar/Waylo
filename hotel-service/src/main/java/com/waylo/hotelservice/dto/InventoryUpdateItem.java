package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record InventoryUpdateItem(
        @NotNull UUID roomTypeId,
        @NotNull LocalDate stayDate,
        @Min(0) @Max(10000) Integer allotment,
        @Min(0) Integer outOfService,
        Boolean stopSell,
        Long expectedVersion
) {}
