package com.waylo.busservice.repository;

import com.waylo.busservice.domain.BusDocument;
import com.waylo.busservice.domain.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BusDocumentRepository extends JpaRepository<BusDocument, UUID> {

    List<BusDocument> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<BusDocument> findByOrganizationIdAndBusIdIsNull(UUID organizationId);

    List<BusDocument> findByBusId(UUID vehicleId);

    List<BusDocument> findByBusIdIn(Collection<UUID> vehicleIds);

    List<BusDocument> findByOrganizationIdIn(Collection<UUID> organizationIds);

    List<BusDocument> findByStatusOrderByCreatedAt(DocumentStatus status);

    List<BusDocument> findAllByOrderByCreatedAtDesc();
}
