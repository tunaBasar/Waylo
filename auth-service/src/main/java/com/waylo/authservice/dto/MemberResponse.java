package com.waylo.authservice.dto;

import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.ScopeType;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String email,
        String name,
        Role role,
        ScopeType scopeType,
        UUID scopeId,
        UUID grantId,
        Instant createdAt) {
}
