package com.waylo.crmservice.repository;

import com.waylo.crmservice.domain.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportCaseRepository extends JpaRepository<SupportCase, UUID> {
    List<SupportCase> findTop200ByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<SupportCase> findTop200BySupplierOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<SupportCase> findTop200ByOrderByCreatedAtDesc();
    List<SupportCase> findTop200ByStatusOrderByCreatedAtDesc(SupportCase.Status status);
}
