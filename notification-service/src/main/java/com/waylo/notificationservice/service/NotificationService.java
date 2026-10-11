package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationResponse send(NotificationRequest request);
    NotificationResponse getById(UUID id);
    List<NotificationResponse> getAll();
    List<NotificationResponse> getByCustomerId(UUID customerId);
    List<NotificationResponse> getByBookingId(UUID bookingId);
    NotificationResponse retry(UUID id);
}
