package com.waylo.notificationservice.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthEventListenerTest {
    private final EmailService emailService = mock(EmailService.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    // findAndRegisterModules: like the Spring Jackson2 bean in production (Java time support for occurredAt)
    private final AuthEventListener listener = new AuthEventListener(emailService, new ObjectMapper().findAndRegisterModules(), processed, Duration.ofHours(24));
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthEventListener.class);

    @BeforeEach
    void attach() { appender.start(); logger.addAppender(appender); }

    @AfterEach
    void detach() { logger.detachAppender(appender); }

    private void assertIgnored(String type) {
        listener.handleAuthEvent("{\"eventType\":\"" + type + "\",\"userId\":\"" + java.util.UUID.randomUUID()
                + "\",\"email\":\"a@b.com\",\"name\":\"A B\"}");
        verifyNoInteractions(emailService);
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("Unknown")));
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN)));
    }

    @Test
    void userDeleted_sendsNoEmailAndNoUnknownLog() { assertIgnored("USER_DELETED"); }

    @Test
    void profileUpdated_sendsNoEmailAndNoUnknownLog() { assertIgnored("PROFILE_UPDATED"); }

    @Test
    void userUpdated_sendsNoEmailAndNoUnknownLog() { assertIgnored("USER_UPDATED"); }

    private static String json(String type, String eventId, Instant occurredAt) {
        return "{\"eventType\":\"" + type + "\",\"userId\":\"" + java.util.UUID.randomUUID() + "\""
                + (eventId == null ? "" : ",\"eventId\":\"" + eventId + "\"")
                + (occurredAt == null ? "" : ",\"occurredAt\":\"" + occurredAt + "\"")
                + ",\"email\":\"a@b.com\",\"name\":\"A B\",\"token\":\"tok\"}";
    }

    @Test
    void newEvent_sendsEmailAndMarksProcessed() {
        listener.handleAuthEvent(json("VERIFICATION", "e1", Instant.now()));
        verify(emailService).sendVerificationEmail("a@b.com", "A B", "tok");
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEventId_isSkipped() {
        when(processed.existsByEventIdAndConsumerGroup("e2", "notification-service-group")).thenReturn(true);
        listener.handleAuthEvent(json("VERIFICATION", "e2", Instant.now()));
        verifyNoInteractions(emailService);
        verify(processed, never()).save(any());
    }

    @Test
    void oldEvent_notEmailedButMarkedProcessed() {
        listener.handleAuthEvent(json("VERIFICATION", "e3", Instant.now().minus(Duration.ofDays(2))));
        verifyNoInteractions(emailService);
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void missingOccurredAt_notEmailed() {
        listener.handleAuthEvent(json("WELCOME", "e4", null));
        verifyNoInteractions(emailService);
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void freshPasswordReset_isSent() {
        listener.handleAuthEvent(json("PASSWORD_RESET", "e5", Instant.now()));
        verify(emailService).sendPasswordResetEmail("a@b.com", "A B", "tok");
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void malformedJson_goesToDlt() {
        assertThrows(IllegalArgumentException.class, () -> listener.handleAuthEvent("{nope"));
    }
}
