package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.SupplierAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierAccountRepository extends JpaRepository<SupplierAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SupplierAccount a where a.supplier = :supplier")
    Optional<SupplierAccount> findForUpdate(@Param("supplier") String supplier);

    List<SupplierAccount> findAllByOrderBySupplierAsc();
}
