package com.waylo.authservice.dto;

import com.waylo.authservice.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@StrongPassword(passwordField = "password", emailField = "email")
public record RegisterRequest(
        @NotBlank(message = "Ad gerekli") String firstName,
        @NotBlank(message = "Soyad gerekli") String lastName,
        @NotBlank(message = "E-posta gerekli") @Email(message = "Gecerli bir e-posta girin") String email,
        @NotBlank(message = "Sifre gerekli") @StrongPassword String password
) {}
