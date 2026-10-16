package com.waylo.authservice.controller;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.repository.OrganizationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint for domain services to build a public-projection filter
 * (e.g. hotel-service hiding hotels of a PENDING/SUSPENDED organization from
 * unauthenticated browse/search). NOT routed by the API gateway; protected by
 * InternalTokenFilter (X-Internal-Token), same as /internal/access.
 */
@RestController
@RequestMapping("/internal/organizations")
public class InternalOrganizationController {

    private final OrganizationRepository organizationRepository;

    public InternalOrganizationController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /** IDs of organizations currently in ACTIVE status. Plain list, no ApiResponse wrapper (matches /internal/access style). */
    @GetMapping("/active-ids")
    public List<UUID> activeIds() {
        return organizationRepository.findByStatus(OrganizationStatus.ACTIVE).stream()
                .map(Organization::getId)
                .toList();
    }
}
