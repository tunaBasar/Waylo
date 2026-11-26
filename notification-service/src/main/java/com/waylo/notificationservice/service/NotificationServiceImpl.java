package com.waylo.notificationservice.service;

import com.waylo.observability.BusinessMetrics;
import com.waylo.notificationservice.domain.DeviceToken;
import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationMapper;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.BusinessRuleException;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.DeviceTokenRepository;
import com.waylo.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${waylo.notifications.retry.max-attempts:5}")
    private int maxAttempts = 5;

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
        deliver(notification);
        String outcome = null;
        if (notification.getStatus() == NotificationStatus.SENT) {
            notification.setNextAttemptAt(null);
            notification.setLastError(null);
            outcome = "sent";
        } else if (notification.getStatus() == NotificationStatus.FAILED) {
            if (notification.getRetryCount() >= maxAttempts) {
                notification.setStatus(NotificationStatus.GAVE_UP);
                notification.setNextAttemptAt(null);
                outcome = "gave_up";
            } else {
                notification.setNextAttemptAt(Instant.now().plus(DeliveryBackoff.delay(notification.getRetryCount())));
                outcome = "failed";
            }
        }
        BusinessMetrics.increment("waylo.notifications", "channel", String.valueOf(notification.getType()),
                "status", String.valueOf(notification.getStatus()));
        if (outcome != null) {
            BusinessMetrics.increment("waylo.notifications.delivery", "channel", String.valueOf(notification.getType()),
                    "outcome", outcome);
        }
    }

    private void deliver(Notification notification) {
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
            log.warn("Notification delivery failed: id={}, error={}", notification.getId(), ex.getClass().getSimpleName());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(ex.getClass().getSimpleName());
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
            log.warn("Push delivery failed: id={}, error={}", notification.getId(), ex.getClass().getSimpleName());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(ex.getClass().getSimpleName());
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
        return repository.findTop500ByOrderByCreatedAtDesc().stream().map(mapper::toResponse).toList();
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
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new BusinessRuleException("Notification already sent");
        }
        notification.setNextAttemptAt(Instant.now());
        retryAttempt(notification);
        log.info("Notification retried: id={}, retryCount={}, status={}", notification.getId(), notification.getRetryCount(), notification.getStatus());
        return mapper.toResponse(notification);
    }

    @Override
    public int retryDue(int batchSize) {
        List<Notification> due = repository.claimDueForRetry(Instant.now(), maxAttempts, batchSize);
        for (Notification n : due) {
            retryAttempt(n);
            log.info("Notification auto-retried: id={}, retryCount={}, status={}", n.getId(), n.getRetryCount(), n.getStatus());
        }
        return due.size();
    }

    /** One retry through the same send path as the first attempt. */
    private void retryAttempt(Notification notification) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        attempt(notification);
        repository.save(notification);
    }

    @Override @Transactional(readOnly = true)
    public List<NotificationResponse> getByStatus(NotificationStatus status) {
        return repository.findTop500ByStatusOrderByCreatedAtDesc(status).stream().map(mapper::toResponse).toList();
    }

    private Notification findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
    }
}
