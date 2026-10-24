package com.waylo.authservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String fullName, String role, boolean verified,
                                LocalDateTime deletedAt) {
}
