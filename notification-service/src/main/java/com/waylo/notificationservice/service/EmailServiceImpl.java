package com.waylo.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${waylo.mail.from}")
    private String fromEmail;

    @Value("${waylo.mail.from-name}")
    private String fromName;

    @Value("${waylo.mail.verification-url}")
    private String verificationBaseUrl;

    @Value("${waylo.mail.suppressed-domains:example.com,example.net,example.org,email.com}")
    private String suppressedDomains;

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendVerificationEmail(String to, String name, String verificationToken) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("verificationLink", verificationBaseUrl + "/verify?token=" + verificationToken);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = templateEngine.process("email-verification", ctx);
        if (sendHtmlMail(to, "Waylo - E-posta Dogrulamasi", html)) {
            log.info("Verification email sent to {}", to);
        }
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetToken) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("resetLink", verificationBaseUrl + "/reset-password?token=" + resetToken);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = templateEngine.process("password-reset", ctx);
        if (sendHtmlMail(to, "Waylo - Sifre Sifirlama", html)) {
            log.info("Password reset email sent to {}", to);
        }
    }

    @Override
    public void sendWelcomeEmail(String to, String name) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = templateEngine.process("welcome", ctx);
        if (sendHtmlMail(to, "Waylo'ya Hos Geldiniz!", html)) {
            log.info("Welcome email sent to {}", to);
        }
    }

    @Override
    public void sendGenericEmail(String to, String subject, String htmlContent) {
        sendHtmlMail(to, subject, htmlContent);
    }

    private boolean sendHtmlMail(String to, String subject, String htmlContent) {
        String domain = mailDomain(to);
        if (domain == null) {
            log.warn("Mail skipped because address is invalid: {}", to);
            return false;
        }
        if (isSuppressedDomain(domain)) {
            log.info("Mail suppressed for reserved/test domain: {}", domain);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email gonderilemedi: " + e.getMessage(), e);
        }
    }

    private String mailDomain(String address) {
        if (address == null) {
            return null;
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return null;
        }
        return address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isSuppressedDomain(String domain) {
        Set<String> configured = Arrays.stream(suppressedDomains.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return configured.stream().anyMatch(domain::equals)
                || configured.stream().anyMatch(value -> domain.endsWith("." + value))
                || domain.endsWith(".test")
                || domain.endsWith(".example")
                || domain.endsWith(".invalid")
                || domain.endsWith(".localhost")
                || domain.endsWith(".local")
                || domain.equals("test")
                || domain.equals("example")
                || domain.equals("invalid")
                || domain.equals("localhost")
                || domain.equals("local");
    }
}
