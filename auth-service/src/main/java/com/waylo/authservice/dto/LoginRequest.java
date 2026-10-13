package com.waylo.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-posta gerekli") @Email String email,
        @NotBlank(message = "Sifre gerekli") String password
) {}
