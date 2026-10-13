package com.waylo.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Ad gerekli") String firstName,
        @NotBlank(message = "Soyad gerekli") String lastName,
        @NotBlank(message = "E-posta gerekli") @Email(message = "Gecerli bir e-posta girin") String email,
        @NotBlank(message = "Sifre gerekli") @Size(min = 6, message = "Sifre en az 6 karakter olmali") String password
) {}
