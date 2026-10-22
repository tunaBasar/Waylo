package com.waylo.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Ad gerekli") @Size(max = 100) String firstName,
        @NotBlank(message = "Soyad gerekli") @Size(max = 100) String lastName
) {}
