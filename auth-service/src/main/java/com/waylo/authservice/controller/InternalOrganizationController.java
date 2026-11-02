package com.waylo.authservice.controller;

import com.waylo.authservice.domain.BusinessType;
import com.waylo.authservice.domain.LegalType;
import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * Return a public-facing projection of one organization by ID.
     * Protected by InternalTokenFilter (X-Internal-Token).
     */
    @GetMapping("/{id}")
    public InternalOrganizationDetail getOrganization(@PathVariable UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        return new InternalOrganizationDetail(org.getId(), org.getName(), org.getStatus(),
                org.getBusinessType(), org.getLegalType());
    }

    /** Public-facing organization detail for internal callers. */
    record InternalOrganizationDetail(
            UUID id,
            String name,
            OrganizationStatus status,
            BusinessType businessType,
            LegalType legalType
    ) {}
}
