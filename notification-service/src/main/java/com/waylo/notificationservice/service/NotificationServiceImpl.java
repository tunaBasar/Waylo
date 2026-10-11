package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationMapper;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public NotificationServiceImpl(NotificationRepository repository, NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = mapper.toEntity(request);
        // Mock: mark as sent immediately
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notification = repository.save(notification);
        log.info("Notification sent: id={}, type={}, recipient={}",
                notification.getId(), notification.getType(), notification.getRecipient());
        return mapper.toResponse(notification);
    }

    @Override @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<NotificationResponse> getByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId).stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<NotificationResponse> getByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public NotificationResponse retry(UUID id) {
        Notification notification = findOrThrow(id);
        notification.setStatus(NotificationStatus.RETRYING);
        notification.setRetryCount(notification.getRetryCount() + 1);
        // Mock: mark as sent after retry
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notification = repository.save(notification);
        log.info("Notification retried: id={}, retryCount={}", notification.getId(), notification.getRetryCount());
        return mapper.toResponse(notification);
    }

    private Notification findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
    }
}
