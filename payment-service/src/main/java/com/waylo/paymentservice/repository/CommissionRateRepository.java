package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.CommissionRate;
import com.waylo.paymentservice.domain.Vertical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRateRepository extends JpaRepository<CommissionRate, UUID> {

    Optional<CommissionRate> findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
            Vertical vertical, UUID supplierOrganizationId, Instant at);

    Optional<CommissionRate> findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
            Vertical vertical, Instant at);

    java.util.List<CommissionRate> findAllByOrderByVerticalAscValidFromDesc();
}
