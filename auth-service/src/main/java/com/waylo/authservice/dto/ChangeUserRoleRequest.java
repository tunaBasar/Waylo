package com.waylo.authservice.dto;

import com.waylo.authservice.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(@NotNull UserRole role) {
}
