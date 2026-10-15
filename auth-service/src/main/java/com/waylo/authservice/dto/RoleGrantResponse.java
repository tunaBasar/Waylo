package com.waylo.authservice.dto;

import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.ScopeType;

import java.util.UUID;

public record RoleGrantResponse(
        Role role,
        ScopeType scopeType,
        UUID scopeId
) {
}
