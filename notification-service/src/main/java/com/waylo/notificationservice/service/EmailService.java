package com.waylo.notificationservice.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String verificationToken);
    void sendPasswordResetEmail(String to, String name, String resetToken);
    void sendWelcomeEmail(String to, String name);
    void sendGenericEmail(String to, String subject, String htmlContent);

    /** Outcome of a delivery attempt. SUPPRESSED = reserved/test domain or invalid address, nothing was sent. */
    enum Delivery { SENT, SUPPRESSED }

    /** Sends and reports what happened. Throws on SMTP failure so the caller can record FAILED. */
    Delivery deliver(String to, String subject, String htmlContent);
}
