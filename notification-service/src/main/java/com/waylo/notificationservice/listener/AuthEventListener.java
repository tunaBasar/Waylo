package com.waylo.notificationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.notificationservice.event.AuthEvent;
import com.waylo.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public AuthEventListener(EmailService emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "auth-events", groupId = "notification-service-group")
    public void handleAuthEvent(String message) {
        try {
            AuthEvent event = objectMapper.readValue(message, AuthEvent.class);
            log.info("Received auth event: type={}, email={}", event.getEventType(), event.getEmail());

            switch (event.getEventType()) {
                case "VERIFICATION" -> emailService.sendVerificationEmail(
                        event.getEmail(), event.getName(), event.getToken());
                case "PASSWORD_RESET" -> emailService.sendPasswordResetEmail(
                        event.getEmail(), event.getName(), event.getToken());
                case "WELCOME" -> emailService.sendWelcomeEmail(
                        event.getEmail(), event.getName());
                case "USER_DELETED", "PROFILE_UPDATED" -> log.debug("Ignoring non-email auth event: {}", event.getEventType());
                default -> log.warn("Unknown auth event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process auth event: {}", e.getMessage(), e);
        }
    }
}
