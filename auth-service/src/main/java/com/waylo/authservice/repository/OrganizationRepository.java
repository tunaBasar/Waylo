package com.waylo.authservice.repository;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByStatus(OrganizationStatus status);
}
