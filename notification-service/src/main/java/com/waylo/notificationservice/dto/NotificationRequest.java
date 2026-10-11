package com.waylo.notificationservice.dto;

import com.waylo.notificationservice.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NotificationRequest(
        @NotNull(message = "Customer ID is required") UUID customerId,
        UUID bookingId,
        @NotNull(message = "Notification type is required") NotificationType type,
        @NotBlank(message = "Recipient is required") String recipient,
        String subject,
        @NotBlank(message = "Body is required") String body
) {}
