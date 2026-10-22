package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.dto.AuthResponse;
import com.waylo.authservice.dto.ChangePasswordRequest;
import com.waylo.authservice.dto.UpdateProfileRequest;
import com.waylo.authservice.dto.UserProfileResponse;
import com.waylo.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "test@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenService refreshTokenService;

    private AccountService accountService;
    private User user;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder, jwtUtil, refreshTokenService);

        user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setFirstName("  John  ");
        user.setLastName("  Doe  ");
        user.setPassword("hashedOldPass");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        try {
            Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, LocalDateTime.of(2026, 1, 1, 0, 0));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set createdAt", e);
        }
    }

    @Test
    void getProfile_found_returnsProfile() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse profile = accountService.getProfile(USER_ID);

        assertEquals(USER_ID, profile.id());
        assertEquals(USER_EMAIL, profile.email());
        assertEquals("  John  ", profile.firstName());
        assertEquals("  Doe  ", profile.lastName());
        assertEquals("USER", profile.role());
        assertTrue(profile.emailVerified());
    }

    @Test
    void getProfile_notFound_returns404() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> accountService.getProfile(USER_ID));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Kullanici bulunamadi", ex.getReason());
    }

    @Test
    void updateProfile_trimsAndSaves() {
        UpdateProfileRequest req = new UpdateProfileRequest("  Jane  ", "  Smith  ");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse profile = accountService.updateProfile(USER_ID, req);

        assertEquals("Jane", profile.firstName());
        assertEquals("Smith", profile.lastName());
        verify(userRepository).save(user);
        assertEquals("Jane", user.getFirstName());
        assertEquals("Smith", user.getLastName());
    }

    @Test
    void updateProfile_notFound_returns404() {
        UpdateProfileRequest req = new UpdateProfileRequest("Jane", "Smith");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> accountService.updateProfile(USER_ID, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void changePassword_wrongCurrent_returns400AndDoesNotSaveOrRevoke() {
        ChangePasswordRequest req = new ChangePasswordRequest("wrongCurrent", "newPass123");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongCurrent", "hashedOldPass")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> accountService.changePassword(USER_ID, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Mevcut sifre hatali", ex.getReason());

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void changePassword_samePassword_returns400() {
        ChangePasswordRequest req = new ChangePasswordRequest("hashedOldPass", "hashedOldPass");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("hashedOldPass", "hashedOldPass")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> accountService.changePassword(USER_ID, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Yeni sifre mevcut sifreden farkli olmali", ex.getReason());

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void changePassword_success_reencodesSavesAndReissuesTokens() {
        String newPlain = "newPass123";
        String newHashed = "newHashedPass";
        ChangePasswordRequest req = new ChangePasswordRequest("hashedOldPass", newPlain);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("hashedOldPass", "hashedOldPass")).thenReturn(true);
        when(passwordEncoder.encode(newPlain)).thenReturn(newHashed);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(USER_ID, USER_EMAIL, "USER")).thenReturn("newAccessToken");
        when(refreshTokenService.issue(USER_ID, null)).thenReturn("newRefreshToken");

        AuthResponse response = accountService.changePassword(USER_ID, req);

        // Password re-encoded and saved
        assertEquals(newHashed, user.getPassword());
        verify(userRepository).save(user);

        // revokeAll called BEFORE issue
        InOrder inOrder = inOrder(refreshTokenService);
        inOrder.verify(refreshTokenService).revokeAll(USER_ID);
        inOrder.verify(refreshTokenService).issue(USER_ID, null);

        // New tokens returned
        assertEquals("newAccessToken", response.token());
        assertEquals("newRefreshToken", response.refreshToken());
    }
}
