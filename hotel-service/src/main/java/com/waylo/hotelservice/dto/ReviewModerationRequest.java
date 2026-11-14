package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewModerationRequest(@NotNull ReviewStatus status, @NotBlank @Size(max = 500) String reason) {}
