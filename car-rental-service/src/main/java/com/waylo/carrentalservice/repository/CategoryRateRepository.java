package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.CarCategory;
import com.waylo.carrentalservice.domain.CategoryRate;
import com.waylo.carrentalservice.domain.Transmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRateRepository extends JpaRepository<CategoryRate, UUID> {

    List<CategoryRate> findByOrganizationId(UUID organizationId);

    List<CategoryRate> findByBranchId(UUID branchId);

    Optional<CategoryRate> findByBranchIdAndCategoryAndTransmission(UUID branchId, CarCategory category, Transmission transmission);
}
