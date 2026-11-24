package com.waylo.packagebookingservice.repository;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {

    List<Order> findByCustomerId(UUID customerId);

    List<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerIdAndQuoteId(UUID customerId, UUID quoteId);

    /** Ids of PENDING orders whose holds expired before the cutoff, oldest first (RM-27). */
    @org.springframework.data.jpa.repository.Query("select o.id from Order o where o.status = com.waylo.packagebookingservice.domain.OrderStatus.PENDING "
            + "and o.holdExpiresAt is not null and o.holdExpiresAt < :cutoff order by o.holdExpiresAt")
    List<UUID> findExpiredPendingIds(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
                                     org.springframework.data.domain.Pageable pageable);

    /** Row lock that skips rows another transaction already holds (SELECT ... FOR UPDATE SKIP LOCKED). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdateSkipLocked(@org.springframework.data.repository.query.Param("id") UUID id);
}
