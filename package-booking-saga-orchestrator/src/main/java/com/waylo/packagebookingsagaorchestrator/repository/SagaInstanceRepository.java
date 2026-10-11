package com.waylo.packagebookingsagaorchestrator.repository;

import com.waylo.packagebookingsagaorchestrator.domain.SagaInstance;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByBookingId(UUID bookingId);
    List<SagaInstance> findByCustomerId(UUID customerId);
    List<SagaInstance> findByStatus(SagaStatus status);
    boolean existsByBookingId(UUID bookingId);
}
