package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.EscrowItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowItemRepository extends JpaRepository<EscrowItem, UUID> {

    Optional<EscrowItem> findByOrderItemId(UUID orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EscrowItem e where e.orderItemId = :orderItemId")
    Optional<EscrowItem> findByOrderItemIdForUpdate(@Param("orderItemId") UUID orderItemId);

    List<EscrowItem> findByPaymentIntentId(UUID paymentIntentId);

    List<EscrowItem> findByOrderId(UUID orderId);
}
