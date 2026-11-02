package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.CarDocument;
import com.waylo.carrentalservice.domain.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CarDocumentRepository extends JpaRepository<CarDocument, UUID> {

    List<CarDocument> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<CarDocument> findByOrganizationIdAndVehicleIdIsNull(UUID organizationId);

    List<CarDocument> findByVehicleId(UUID vehicleId);

    List<CarDocument> findByVehicleIdIn(Collection<UUID> vehicleIds);

    List<CarDocument> findByOrganizationIdIn(Collection<UUID> organizationIds);

    List<CarDocument> findByStatusOrderByCreatedAt(DocumentStatus status);

    List<CarDocument> findAllByOrderByCreatedAtDesc();
}
