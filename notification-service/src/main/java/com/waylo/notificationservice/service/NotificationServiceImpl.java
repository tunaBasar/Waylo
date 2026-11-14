package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.DeviceToken;
import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationMapper;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.DeviceTokenRepository;
import com.waylo.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final EmailService emailService;
    private final PushSender pushSender;
    private final DeviceTokenRepository deviceTokens;

    public NotificationServiceImpl(NotificationRepository repository, NotificationMapper mapper, EmailService emailService,
                                   PushSender pushSender, DeviceTokenRepository deviceTokens) {
        this.repository = repository;
        this.mapper = mapper;
        this.emailService = emailService;
        this.pushSender = pushSender;
        this.deviceTokens = deviceTokens;
    }

    /**
     * Records the real delivery outcome. EMAIL goes through {@link EmailService#deliver}: SENT only when SMTP accepted it,
     * SUPPRESSED for reserved/test domains, FAILED on SMTP error (retry can try again). PUSH goes through {@link PushSender}. SMS has no provider yet,
     * so they stay PENDING instead of claiming to be sent.
     */
    private void attempt(Notification notification) {
        if (notification.getType() == NotificationType.PUSH) {
            attemptPush(notification);
            return;
        }
        if (notification.getType() != NotificationType.EMAIL) {
            notification.setStatus(NotificationStatus.PENDING);
            return;
        }
        try {
            String html = "<p>" + escape(notification.getBody()).replace("\n", "<br>") + "</p>";
            EmailService.Delivery d = emailService.deliver(notification.getRecipient(), notification.getSubject(), html);
            notification.setStatus(d == EmailService.Delivery.SENT ? NotificationStatus.SENT : NotificationStatus.SUPPRESSED);
            if (d == EmailService.Delivery.SENT) notification.setSentAt(Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Notification delivery failed: id={}, reason={}", notification.getId(), ex.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
        }
    }

    /** PUSH: SUPPRESSED when the provider is disabled or the customer has no device, FAILED on transport error. */
    private void attemptPush(Notification notification) {
        if (!pushSender.isEnabled()) {
            notification.setStatus(NotificationStatus.SUPPRESSED);
            return;
        }
        try {
            List<String> tokens = deviceTokens.findByUserId(notification.getCustomerId()).stream().map(DeviceToken::getToken).toList();
            if (tokens.isEmpty()) {
                notification.setStatus(NotificationStatus.SUPPRESSED);
                return;
            }
            Set<String> invalid = pushSender.send(tokens, notification.getSubject(), notification.getBody());
            if (!invalid.isEmpty()) deviceTokens.deleteAll(deviceTokens.findByTokenIn(invalid));
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Push delivery failed: id={}, reason={}", notification.getId(), ex.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
        }
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = mapper.toEntity(request);
        attempt(notification);
        notification = repository.save(notification);
        log.info("Notification recorded: id={}, type={}, status={}",
                notification.getId(), notification.getType(), notification.getStatus());
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
        notification.setRetryCount(notification.getRetryCount() + 1);
        attempt(notification);
        notification = repository.save(notification);
        log.info("Notification retried: id={}, retryCount={}, status={}", notification.getId(), notification.getRetryCount(), notification.getStatus());
        return mapper.toResponse(notification);
    }

    private Notification findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
    }
}
