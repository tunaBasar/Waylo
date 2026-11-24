package com.waylo.notificationservice.repository;

import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByCustomerId(UUID customerId);
    List<Notification> findByBookingId(UUID bookingId);
    List<Notification> findByStatus(NotificationStatus status);
    /** Claims due FAILED rows. Rows locked by other instances are skipped. Must run inside the transaction that sends them. */
    @Query(value = "SELECT * FROM notifications WHERE status = 'FAILED' AND retry_count < :max AND next_attempt_at <= :now "
            + "ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Notification> claimDueForRetry(@Param("now") Instant now, @Param("max") int max, @Param("limit") int limit);
    List<Notification> findByType(NotificationType type);
    List<Notification> findByCustomerIdAndType(UUID customerId, NotificationType type);
}
