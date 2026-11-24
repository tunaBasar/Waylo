package com.waylo.crmservice.repository;

import com.waylo.crmservice.domain.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
