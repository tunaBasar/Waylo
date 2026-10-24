package com.waylo.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

import static org.mockito.Mockito.*;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new EmailServiceImpl(mailSender, mock(TemplateEngine.class));
        ReflectionTestUtils.setField(service, "fromEmail", "sender@waylo.com");
        ReflectionTestUtils.setField(service, "fromName", "Waylo");
        ReflectionTestUtils.setField(service, "suppressedDomains", "example.com,example.net,example.org,email.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@foo.test", "user@foo.example", "user@foo.invalid", "user@foo.localhost", "user@foo.local"})
    void suppressesReservedTlds(String address) {
        service.sendGenericEmail(address, "subject", "body");

        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "user@example.net", "user@example.org", "user@email.com",
            "user@sub.example.com", "user@sub.example.net", "user@sub.example.org", "user@sub.email.com"})
    void suppressesConfiguredDomainsAndSubdomains(String address) {
        service.sendGenericEmail(address, "subject", "body");

        verifyNoInteractions(mailSender);
    }

    @Test
    void suppressionIsCaseInsensitive() {
        service.sendGenericEmail("user@Sub.Example.COM", "subject", "body");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendsNormalDomain() {
        MimeMessage message = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendGenericEmail("user@gmail.com", "subject", "body");

        verify(mailSender).send(message);
    }

    @Test
    void skipsInvalidAddress() {
        service.sendGenericEmail("not-an-email", "subject", "body");

        verifyNoInteractions(mailSender);
    }
}
