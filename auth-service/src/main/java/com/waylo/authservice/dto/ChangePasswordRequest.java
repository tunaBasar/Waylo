package com.waylo.authservice.dto;

import com.waylo.authservice.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword String newPassword
) {}
