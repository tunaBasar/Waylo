package com.waylo.authservice.dto;

import com.waylo.authservice.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @StrongPassword String newPassword
) {}
