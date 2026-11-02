package com.waylo.authservice.dto;

import com.waylo.authservice.domain.BusinessType;
import com.waylo.authservice.domain.LegalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank
        @Size(max = 255)
        String name,
        @NotNull
        BusinessType businessType,
        @NotNull
        LegalType legalType
) {
}
