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
        sendHtmlMail(to, "Waylo - E-posta Dogrulamasi", html);
        log.info("Verification email sent to {}", to);
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetToken) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("resetLink", verificationBaseUrl + "/reset-password?token=" + resetToken);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = templateEngine.process("password-reset", ctx);
        sendHtmlMail(to, "Waylo - Sifre Sifirlama", html);
        log.info("Password reset email sent to {}", to);
    }

    @Override
    public void sendWelcomeEmail(String to, String name) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = templateEngine.process("welcome", ctx);
        sendHtmlMail(to, "Waylo'ya Hos Geldiniz!", html);
        log.info("Welcome email sent to {}", to);
    }

    @Override
    public void sendGenericEmail(String to, String subject, String htmlContent) {
        sendHtmlMail(to, subject, htmlContent);
    }

    private void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email gonderilemedi: " + e.getMessage(), e);
        }
    }
}
