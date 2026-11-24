package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.PartnerBankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerBankAccountRepository extends JpaRepository<PartnerBankAccount, UUID> {

    Optional<PartnerBankAccount> findByOrganizationId(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PartnerBankAccount b where b.organizationId = :orgId")
    Optional<PartnerBankAccount> findByOrganizationIdForUpdate(@Param("orgId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PartnerBankAccount b where b.id = :id")
    Optional<PartnerBankAccount> findByIdForUpdate(@Param("id") UUID id);

    List<PartnerBankAccount> findByStatusOrderByUpdatedAtAsc(BankAccountStatus status, Pageable pageable);

    boolean existsByIbanHashAndStatusNotAndOrganizationIdNot(String ibanHash, BankAccountStatus status, UUID organizationId);
}
