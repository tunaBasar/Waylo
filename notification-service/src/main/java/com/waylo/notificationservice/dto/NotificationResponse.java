package com.waylo.notificationservice.dto;

import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, UUID customerId, UUID bookingId, NotificationType type,
        String recipient, String subject, String body, NotificationStatus status,
        int retryCount, Instant sentAt, Instant createdAt, Instant updatedAt
) {}
