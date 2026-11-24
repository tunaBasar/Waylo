package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.PaymentReconciliation;
import com.waylo.paymentservice.domain.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Insert and read only. Update and delete are also blocked by a database trigger. */
public interface PaymentReconciliationRepository extends JpaRepository<PaymentReconciliation, UUID> {

    @Query("select r from PaymentReconciliation r where (:status is null or r.status = :status) order by r.createdAt desc, r.id")
    Page<PaymentReconciliation> search(@Param("status") ReconciliationStatus status, Pageable pageable);

    @Override
    default void delete(PaymentReconciliation entity) { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteById(UUID id) { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAll() { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAll(Iterable<? extends PaymentReconciliation> entities) { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAllInBatch() { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAllInBatch(Iterable<PaymentReconciliation> entities) { throw new UnsupportedOperationException("reconciliation log is append-only"); }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException("reconciliation log is append-only"); }
}
