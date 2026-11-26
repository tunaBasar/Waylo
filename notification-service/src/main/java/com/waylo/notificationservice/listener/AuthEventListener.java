package com.waylo.notificationservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.event.AuthEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);
    // Must stay unchanged: a new group id would replay the whole topic.
    private static final String GROUP = "notification-service-group";

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final Duration maxAge;

    public AuthEventListener(EmailService emailService, ObjectMapper objectMapper,
                             ProcessedEventRepository processedEventRepository,
                             @Value("${waylo.mail.auth-event-max-age:PT24H}") Duration maxAge) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.maxAge = maxAge;
    }

    @KafkaListener(topics = "auth-events", groupId = GROUP)
    public void handleAuthEvent(String message) {
        try {
            // Read eventId/occurredAt from the raw JSON: BaseEvent's constructor fills both with fresh defaults
            // (random id, now), which would make a pre-change payload without them look new and re-send its mail.
            JsonNode raw = objectMapper.readTree(message);
            AuthEvent event = objectMapper.treeToValue(raw, AuthEvent.class);
            log.info("Received auth event: type={}, email={}", event.getEventType(), event.getEmail());

            String eventId = text(raw, "eventId");
            if (eventId == null) {
                log.debug("Auth event without eventId (legacy), processing without idempotency: type={}", event.getEventType());
            } else if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, GROUP)) {
                log.debug("Duplicate auth event skipped: {}", eventId);
                return;
            }

            Instant occurredAt = instant(text(raw, "occurredAt"));
            if (isEmailType(event.getEventType()) && isTooOld(occurredAt)) {
                log.info("Skipping old auth event (replay safety): type={}, eventId={}, occurredAt={}",
                        event.getEventType(), eventId, occurredAt);
                markProcessed(eventId);
                return;
            }

            switch (event.getEventType()) {
                case "VERIFICATION" -> emailService.sendVerificationEmail(
                        event.getEmail(), event.getName(), event.getToken());
                case "PASSWORD_RESET" -> emailService.sendPasswordResetEmail(
                        event.getEmail(), event.getName(), event.getToken());
                case "WELCOME" -> emailService.sendWelcomeEmail(
                        event.getEmail(), event.getName());
                case "EMAIL_CHANGE_REQUESTED" -> emailService.sendEmailChangeNotice(
                        event.getEmail(), event.getName());
                case "USER_DELETED", "PROFILE_UPDATED", "USER_UPDATED" -> log.debug("Ignoring non-email auth event: {}", event.getEventType());
                default -> log.warn("Unknown auth event type: {}", event.getEventType());
            }
            markProcessed(eventId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Malformed payload: non-retryable, straight to the DLT
            throw new IllegalArgumentException("Malformed auth event JSON", e);
        } catch (RuntimeException e) {
            // Propagate to the DefaultErrorHandler (retry, then <topic>.DLT)
            log.error("Failed to process auth event: {}", e.getMessage(), e);
            throw e;
        }
    }

    private static boolean isEmailType(String type) {
        return "VERIFICATION".equals(type) || "PASSWORD_RESET".equals(type)
                || "WELCOME".equals(type) || "EMAIL_CHANGE_REQUESTED".equals(type);
    }

    /** Missing or unparsable occurredAt only exists in pre-change history, so it counts as too old. */
    private boolean isTooOld(Instant at) {
        return at == null || at.isBefore(Instant.now().minus(maxAge));
    }

    private static String text(JsonNode raw, String field) {
        JsonNode n = raw.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Instant instant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private void markProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId, GROUP));
        }
    }
}
