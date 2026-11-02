package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.SupplierLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedgerEntry, UUID> {

    Optional<SupplierLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Page<SupplierLedgerEntry> findBySupplierOrderByCreatedAtDesc(String supplier, Pageable pageable);
}
