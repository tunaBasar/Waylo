package com.waylo.notificationservice.repository;

import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByCustomerId(UUID customerId);
    List<Notification> findByBookingId(UUID bookingId);
    List<Notification> findByStatus(NotificationStatus status);
    List<Notification> findByType(NotificationType type);
    List<Notification> findByCustomerIdAndType(UUID customerId, NotificationType type);
}
