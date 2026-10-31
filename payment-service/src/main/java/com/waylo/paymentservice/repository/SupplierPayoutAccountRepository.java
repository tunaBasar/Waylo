package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.SupplierPayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierPayoutAccountRepository extends JpaRepository<SupplierPayoutAccount, UUID> {

    Optional<SupplierPayoutAccount> findByOrganizationId(UUID organizationId);
}
