package com.waylo.authservice.dto;

import com.waylo.authservice.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RoleGrantRequest(
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotNull UUID hotelId) {
}
