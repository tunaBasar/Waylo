package com.waylo.authservice.dto;

import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String fullName, String role, boolean verified) {
}
