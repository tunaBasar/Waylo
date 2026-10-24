package com.waylo.notificationservice.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.notificationservice.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthEventListenerTest {
    private final EmailService emailService = mock(EmailService.class);
    private final AuthEventListener listener = new AuthEventListener(emailService, new ObjectMapper());
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
}
