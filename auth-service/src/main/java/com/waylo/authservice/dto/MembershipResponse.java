package com.waylo.authservice.dto;

import com.waylo.authservice.domain.BusinessType;
import com.waylo.authservice.domain.LegalType;
import com.waylo.authservice.domain.MembershipStatus;
import com.waylo.authservice.domain.OrganizationStatus;

import java.util.List;
import java.util.UUID;

public record MembershipResponse(
        UUID membershipId,
        UUID organizationId,
        String organizationName,
        OrganizationStatus organizationStatus,
        BusinessType organizationBusinessType,
        LegalType organizationLegalType,
        MembershipStatus status,
        List<RoleGrantResponse> roles
) {
}
