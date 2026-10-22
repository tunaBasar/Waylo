package com.waylo.authservice.dto;

import com.waylo.authservice.domain.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getRole().name(),
                u.isEmailVerified(),
                u.getCreatedAt()
        );
    }
}
