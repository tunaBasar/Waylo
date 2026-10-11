package com.waylo.crmservice.dto;

import com.waylo.crmservice.domain.CustomerTier;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id, String firstName, String lastName, String email, String phone,
        String passportNumber, CustomerTier tier, int loyaltyPoints, boolean active,
        Instant createdAt, Instant updatedAt
) {}
