package com.waylo.packagebookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemRequest(@NotBlank @Size(max = 200) String payload) {}
