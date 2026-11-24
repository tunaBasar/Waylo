package com.waylo.packagebookingservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** checkedOutAt is optional and defaults to now. */
public record CheckOutRequest(@NotNull Long expectedVersion, Instant checkedOutAt) {}
