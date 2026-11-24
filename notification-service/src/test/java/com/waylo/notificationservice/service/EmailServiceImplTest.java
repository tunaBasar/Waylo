package com.waylo.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    // Regression: the old setting already ended in /verify and the code appended /verify again (/verify/verify -> 404).
    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "http://localhost:3000/", "https://waylo.example.app//"})
    void verificationAndResetLinksAreBuiltFromTheBaseUrl(String base) {
        TemplateEngine engine = mock(TemplateEngine.class);
        service = new EmailServiceImpl(mailSender, engine);
        ReflectionTestUtils.setField(service, "fromEmail", "sender@waylo.com");
        ReflectionTestUtils.setField(service, "fromName", "Waylo");
        ReflectionTestUtils.setField(service, "suppressedDomains", "example.com");
        ReflectionTestUtils.setField(service, "appBaseUrl", base);
        when(engine.process(anyString(), any(IContext.class))).thenReturn("<p>x</p>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        String root = base.replaceAll("/+$", "");

        service.sendVerificationEmail("user@gmail.com", "Ada", "tok123");
        service.sendPasswordResetEmail("user@gmail.com", "Ada", "rst456");
        service.sendWelcomeEmail("user@gmail.com", "Ada");

        ArgumentCaptor<IContext> ctx = ArgumentCaptor.forClass(IContext.class);
        verify(engine).process(eq("email-verification"), ctx.capture());
        assertThat(ctx.getValue().getVariable("verificationLink")).isEqualTo(root + "/verify?token=tok123");
        verify(engine).process(eq("password-reset"), ctx.capture());
        assertThat(ctx.getValue().getVariable("resetLink")).isEqualTo(root + "/reset-password?token=rst456");
        verify(engine).process(eq("welcome"), ctx.capture());
        assertThat(ctx.getValue().getVariable("appUrl")).isEqualTo(root + "/");
    }
}
