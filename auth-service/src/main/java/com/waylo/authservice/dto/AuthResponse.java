package com.waylo.authservice.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String refreshToken,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified
) {}
