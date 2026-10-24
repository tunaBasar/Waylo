package com.waylo.authservice.service;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AdminOrganizationService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrganizationService.class);

    static final int MAX_PAGE_SIZE = 100;

    private final OrganizationRepository organizationRepository;

    public AdminOrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /** status absent -> PENDING (legacy default), status=ALL -> every status. */
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(String statusParam, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<Organization> result;
        if (statusParam != null && statusParam.trim().equalsIgnoreCase("ALL")) {
            result = organizationRepository.findAll(pageable);
        } else {
            OrganizationStatus status = OrganizationStatus.PENDING;
            if (statusParam != null && !statusParam.isBlank()) {
                status = OrganizationStatus.valueOf(statusParam.trim().toUpperCase());
            }
            result = organizationRepository.findByStatus(status, pageable);
        }
        return result.map(org -> new OrganizationResponse(org.getId(), org.getName(), org.getStatus()));
    }

    @Transactional
    public void approve(UUID organizationId, UUID adminId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (org.getStatus() != OrganizationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization is not pending");
        }

        org.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.save(org);
        log.info("Organization {} approved by admin {}", organizationId, adminId);
    }

    @Transactional
    public void suspend(UUID organizationId, UUID adminId, String reason) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (org.getStatus() != OrganizationStatus.ACTIVE && org.getStatus() != OrganizationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization is not active or pending");
        }

        org.setStatus(OrganizationStatus.SUSPENDED);
        organizationRepository.save(org);
        log.info("Organization {} suspended by admin {} reason={}", organizationId, adminId, reason);
    }
}
