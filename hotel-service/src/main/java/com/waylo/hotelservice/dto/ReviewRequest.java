package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewRequest(@NotNull UUID orderItemId, @Min(1) @Max(5) int score, @Size(max = 2000) String text) {}
