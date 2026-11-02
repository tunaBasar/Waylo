package com.waylo.crmservice.dto;

import com.waylo.crmservice.domain.CustomerTier;
import java.time.Instant;
import java.util.UUID;

/** passport_number is stored but intentionally never returned by any endpoint. */
public record CustomerResponse(
        UUID id, String firstName, String lastName, String email, String phone,
        CustomerTier tier, int loyaltyPoints, boolean active,
        Instant createdAt, Instant updatedAt
) {}
