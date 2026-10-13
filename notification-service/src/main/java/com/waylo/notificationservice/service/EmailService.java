package com.waylo.notificationservice.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String verificationToken);
    void sendPasswordResetEmail(String to, String name, String resetToken);
    void sendWelcomeEmail(String to, String name);
    void sendGenericEmail(String to, String subject, String htmlContent);
}
