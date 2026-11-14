package com.waylo.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank(message = "Token is required") @Size(max = 512, message = "Token too long") String token,
        @NotBlank(message = "Platform is required")
        @Pattern(regexp = "ios|android|web", message = "Platform must be ios, android or web") String platform
) {}
