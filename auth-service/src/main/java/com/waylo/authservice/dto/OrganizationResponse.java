package com.waylo.authservice.dto;

import com.waylo.authservice.domain.OrganizationStatus;

import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        OrganizationStatus status
) {
}
