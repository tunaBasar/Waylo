package com.waylo.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendOrganizationRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
