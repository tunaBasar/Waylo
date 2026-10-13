package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.*;
import com.waylo.authservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${waylo.verification.expiration-hours}")
    private int verificationExpirationHours;

    @Value("${waylo.reset.expiration-hours}")
    private int resetExpirationHours;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApiResponse<AuthResponse> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ApiResponse.error("Bu e-posta adresi zaten kullaniliyor");
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(verificationExpirationHours));

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        // Send verification email via Kafka
        publishAuthEvent("VERIFICATION", user, user.getVerificationToken());

        // Generate tokens (user can login but will see "verify email" prompt)
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        AuthResponse response = new AuthResponse(
                token, refreshToken, user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name(), user.isEmailVerified()
        );

        return ApiResponse.success("Kayit basarili! Lutfen e-postanizi dogrulayin.", response);
    }

    public ApiResponse<AuthResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return ApiResponse.error("E-posta veya sifre hatali");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        AuthResponse response = new AuthResponse(
                token, refreshToken, user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name(), user.isEmailVerified()
        );

        log.info("User logged in: {}", user.getEmail());
        return ApiResponse.success("Giris basarili", response);
    }

    @Transactional
    public ApiResponse<Void> verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElse(null);

        if (user == null) {
            return ApiResponse.error("Gecersiz dogrulama linki");
        }

        if (user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Dogrulama linkinin suresi dolmus. Yeni link isteyiniz.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        // Send welcome email
        publishAuthEvent("WELCOME", user, null);

        log.info("Email verified for user: {}", user.getEmail());
        return ApiResponse.success("E-posta basariyla dogrulandi!");
    }

    @Transactional
    public ApiResponse<Void> resendVerification(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ApiResponse.success("Eger hesabiniz varsa, dogrulama linki gonderildi.");
        }

        if (user.isEmailVerified()) {
            return ApiResponse.error("E-posta zaten dogrulanmis");
        }

        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(verificationExpirationHours));
        userRepository.save(user);

        publishAuthEvent("VERIFICATION", user, user.getVerificationToken());

        return ApiResponse.success("Dogrulama linki tekrar gonderildi");
    }

    @Transactional
    public ApiResponse<Void> forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        // Don't reveal whether the email exists
        if (user == null) {
            return ApiResponse.success("Eger hesabiniz varsa, sifre sifirlama linki gonderildi.");
        }

        user.setResetToken(UUID.randomUUID().toString());
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(resetExpirationHours));
        userRepository.save(user);

        publishAuthEvent("PASSWORD_RESET", user, user.getResetToken());

        log.info("Password reset requested for: {}", user.getEmail());
        return ApiResponse.success("Sifre sifirlama linki e-postaniza gonderildi");
    }

    @Transactional
    public ApiResponse<Void> resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElse(null);

        if (user == null) {
            return ApiResponse.error("Gecersiz sifirlama linki");
        }

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Sifirlama linkinin suresi dolmus");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        log.info("Password reset for user: {}", user.getEmail());
        return ApiResponse.success("Sifreniz basariyla guncellendi");
    }

    public ApiResponse<AuthResponse> refreshToken(String refreshTokenStr) {
        if (!jwtUtil.isValid(refreshTokenStr)) {
            return ApiResponse.error("Gecersiz refresh token");
        }

        String userId = jwtUtil.getUserId(refreshTokenStr);
        User user = userRepository.findById(UUID.fromString(userId)).orElse(null);

        if (user == null) {
            return ApiResponse.error("Kullanici bulunamadi");
        }

        String newToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefresh = jwtUtil.generateRefreshToken(user.getId());

        AuthResponse response = new AuthResponse(
                newToken, newRefresh, user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name(), user.isEmailVerified()
        );

        return ApiResponse.success("Token yenilendi", response);
    }

    private void publishAuthEvent(String eventType, User user, String token) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("userId", user.getId().toString());
            event.put("email", user.getEmail());
            event.put("name", user.getFullName());
            if (token != null) {
                event.put("token", token);
            }

            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("auth-events", user.getId().toString(), json);
            log.info("Published auth event: type={}, email={}", eventType, user.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish auth event: {}", e.getMessage());
        }
    }
}
