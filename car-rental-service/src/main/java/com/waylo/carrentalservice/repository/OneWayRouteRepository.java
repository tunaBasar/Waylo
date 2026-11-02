package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.OneWayRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OneWayRouteRepository extends JpaRepository<OneWayRoute, UUID> {

    List<OneWayRoute> findByOrganizationId(UUID organizationId);

    Optional<OneWayRoute> findByFromBranchIdAndToBranchId(UUID fromBranchId, UUID toBranchId);

    void deleteByOrganizationId(UUID organizationId);
}
