package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.PaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.id = :id")
    Optional<PaymentIntent> findByIdForUpdate(@Param("id") UUID id);

    /**
     * RM-27: AUTHORIZED intents (the provider holds the money) older than the cutoff that have no escrow item and were not
     * already sent to manual review. Oldest first.
     */
    @Query("select p.id from PaymentIntent p where p.status = com.waylo.paymentservice.domain.PaymentIntentStatus.AUTHORIZED "
            + "and p.providerPaymentId is not null and p.updatedAt < :cutoff "
            + "and not exists (select 1 from EscrowItem e where e.paymentIntentId = p.id) "
            + "and not exists (select 1 from PaymentReconciliation r where r.paymentIntentId = p.id "
            + "and r.status = com.waylo.paymentservice.domain.ReconciliationStatus.MANUAL_REVIEW) "
            + "order by p.updatedAt")
    java.util.List<UUID> findAuthorizedWithoutEscrow(@Param("cutoff") java.time.Instant cutoff,
                                                     org.springframework.data.domain.Pageable pageable);
}
