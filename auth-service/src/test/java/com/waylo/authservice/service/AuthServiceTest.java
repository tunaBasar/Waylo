package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.*;
import com.waylo.authservice.exception.DuplicateRegistrationException;
import com.waylo.authservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtUtil,
                kafkaTemplate, new ObjectMapper(), refreshTokenService
        );
        ReflectionTestUtils.setField(authService, "verificationExpirationHours", 24);
        ReflectionTestUtils.setField(authService, "resetExpirationHours", 1);
        ReflectionTestUtils.setField(authService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockMinutes", 15);
        lenient().when(jwtUtil.generateToken(any(UUID.class), any(String.class), any(String.class)))
                .thenReturn("fake.jwt.token");
        lenient().when(refreshTokenService.issue(any(UUID.class), any(UUID.class)))
                .thenReturn("fake.refresh.token");
    }

    // ==================== login ====================

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Unknown email -> generic error, no save")
        void unknownEmail_errorNoSave() {
            LoginRequest req = new LoginRequest("nonexistent@example.com", "password");
            when(userRepository.findByEmailIgnoreCase("nonexistent@example.com"))
                    .thenReturn(Optional.empty());

            ApiResponse<AuthResponse> result = authService.login(req);

            assertFalse(result.success());
            assertEquals("E-posta veya sifre hatali", result.message());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Wrong password 4 times increments, 5th sets lockedUntil and resets counter")
        void wrongPasswordFiveTimes_locked() {
            User user = createLockedUser(null, 0);
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed"))
                    .thenReturn(false);

            for (int i = 1; i <= 4; i++) {
                authService.login(new LoginRequest("test@example.com", "wrong"));
            }

            // After 4 wrong: failedLoginAttempts = 4, lockedUntil = null
            assertEquals(4, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil());

            // 5th wrong -> lock (counter resets to 0, lockedUntil set)
            authService.login(new LoginRequest("test@example.com", "wrong"));

            assertEquals(0, user.getFailedLoginAttempts());
            assertNotNull(user.getLockedUntil());
            assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
            verify(userRepository, times(5)).save(any());
        }

        @Test
        @DisplayName("Locked user with CORRECT password -> LOCKED_MESSAGE, passwordEncoder.matches never called")
        void lockedUser_correctPassword_stillLocked() {
            User user = createLockedUser(
                    LocalDateTime.now().plusMinutes(10),  // locked for 10 more minutes
                    0
            );
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));
            // passwordEncoder.matches should NOT be called at all
            lenient().when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

            ApiResponse<AuthResponse> result = authService.login(
                    new LoginRequest("test@example.com", "correct"));

            assertFalse(result.success());
            assertEquals(AuthService.LOCKED_MESSAGE, result.message());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Expired lock + correct password -> success and counters cleared")
        void expiredLock_correctPassword_success() {
            User user = createLockedUser(
                    LocalDateTime.now().minusMinutes(10),  // lock expired
                    2
            );
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

            ApiResponse<AuthResponse> result = authService.login(
                    new LoginRequest("test@example.com", "correct"));

            assertTrue(result.success());
            ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedCaptor.capture());
            User saved = savedCaptor.getValue();
            assertEquals(0, saved.getFailedLoginAttempts());
            assertNull(saved.getLockedUntil());
        }

        @Test
        @DisplayName("Login with normalized email (mixed case) finds existing user")
        void login_normalizesEmail() {
            User user = createUser(USER_ID, "TEST@EXAMPLE.COM", "hashed", null, 0);
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

            ApiResponse<AuthResponse> result = authService.login(
                    new LoginRequest("  TEST@EXAMPLE.COM  ", "correct"));

            assertTrue(result.success());
            // save is NOT called because counters are already clean
            verify(userRepository, never()).save(any());
        }
    }

    // ==================== register ====================

    @Nested
    @DisplayName("Register")
    class RegisterTests {

        @Test
        @DisplayName("Register stores lowercase trimmed email")
        void register_storesNormalizedEmail() {
            when(userRepository.existsByEmailIgnoreCase("test@example.com"))
                    .thenReturn(false);
            when(passwordEncoder.encode("pass123")).thenReturn("hashed");
            User saved = createUser(USER_ID, "TEST@EXAMPLE.COM", "hashed", null, 0);
            when(userRepository.save(any(User.class))).thenReturn(saved);

            RegisterRequest req = new RegisterRequest("John", "Doe", "  TEST@EXAMPLE.COM  ", "pass123");
            ApiResponse<AuthResponse> result = authService.register(req);

            assertTrue(result.success());
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals("test@example.com", captor.getValue().getEmail());
        }

        @Test
        @DisplayName("Rejects existing 'A@B.com' when 'a@b.com' exists (case insensitive)")
        void register_caseInsensitiveReject() {
            when(userRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

            RegisterRequest req = new RegisterRequest("John", "Doe", "A@B.COM", "pass123");
            DuplicateRegistrationException exception = assertThrows(
                    DuplicateRegistrationException.class, () -> authService.register(req));

            assertEquals("Bu e-posta adresi zaten kullaniliyor", exception.getMessage());
            verify(userRepository, never()).save(any());
        }
    }

    // ==================== resetPassword ====================

    @Nested
    @DisplayName("Reset password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Reset success clears lockout and calls revokeAll")
        void resetSuccess_clearsLockoutAndRevokes() {
            User user = createUser(USER_ID, "test@example.com", "hashed", null, 0);
            user.setFailedLoginAttempts(5);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            user.setResetToken("reset-token-xyz");
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));

            when(userRepository.findByResetToken("reset-token-xyz"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newpass")).thenReturn("newhashed");

            ResetPasswordRequest req = new ResetPasswordRequest("reset-token-xyz", "newpass");
            ApiResponse<Void> result = authService.resetPassword(req);

            assertTrue(result.success());
            assertEquals(0, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil());
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals(0, captor.getValue().getFailedLoginAttempts());
            assertNull(captor.getValue().getLockedUntil());
            verify(refreshTokenService).revokeAll(USER_ID);
        }
    }

    // ==================== resendVerification ====================

    @Nested
    @DisplayName("Resend verification")
    class ResendVerificationTests {

        @Test
        @DisplayName("Resend with mixed-case email finds user via ignoreCase")
        void resend_normalizesEmail() {
            User user = createUser(USER_ID, "TEST@EXAMPLE.COM", "hashed", null, 0);
            user.setEmailVerified(false);
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));

            ApiResponse<Void> result = authService.resendVerification("  TEST@EXAMPLE.COM  ");

            assertTrue(result.success());
            verify(userRepository).save(any());
        }
    }

    // ==================== forgotPassword ====================

    @Nested
    @DisplayName("Forgot password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Forgot password normalizes email")
        void forgotPassword_normalizesEmail() {
            User user = createUser(USER_ID, "TEST@EXAMPLE.COM", "hashed", null, 0);
            when(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .thenReturn(Optional.of(user));

            ForgotPasswordRequest req = new ForgotPasswordRequest("  TEST@EXAMPLE.COM  ");
            ApiResponse<Void> result = authService.forgotPassword(req);

            assertTrue(result.success());
            verify(userRepository).save(any());
        }
    }

    @Test
    void login_deletedUser_sameErrorAsWrongCredentials() {
        User user = createUser(USER_ID, "x@example.com", "hashed", null, 0);
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.of(user));
        ApiResponse<AuthResponse> resp = authService.login(new LoginRequest("x@example.com", "pw"));
        assertFalse(resp.success());
        assertEquals("E-posta veya sifre hatali", resp.message());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshToken_deletedUser_rejected() {
        User user = createUser(USER_ID, "x@example.com", "hashed", null, 0);
        user.setDeletedAt(LocalDateTime.now());
        when(refreshTokenService.rotate("rt")).thenReturn(new RotationResult(USER_ID, "new"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        ApiResponse<AuthResponse> resp = authService.refreshToken("rt");
        assertFalse(resp.success());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    // ==================== Helpers ====================

    private User createUser(UUID id, String email, String password, LocalDateTime lockedUntil, int failedAttempts) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setLockedUntil(lockedUntil);
        user.setFailedLoginAttempts(failedAttempts);
        return user;
    }

    private User createLockedUser(LocalDateTime lockedUntil, int failedAttempts) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setPassword("hashed");
        user.setLockedUntil(lockedUntil);
        user.setFailedLoginAttempts(failedAttempts);
        return user;
    }
}
