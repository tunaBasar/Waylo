package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.repository.OrganizationMembershipRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;
import com.waylo.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailChangeTest {

    private final UUID userId = UUID.randomUUID();
    private UserRepository users;
    private PasswordEncoder encoder;
    private RefreshTokenService refreshTokens;
    private AccountEventPublisher publisher;
    private AccountService accountService;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        refreshTokens = mock(RefreshTokenService.class);
        publisher = mock(AccountEventPublisher.class);
        accountService = new AccountService(users, encoder, mock(JwtUtil.class), refreshTokens, publisher,
                mock(OrganizationMembershipRepository.class), mock(RoleAssignmentRepository.class));
        authService = new AuthService(users, encoder, mock(JwtUtil.class), publisher, refreshTokens);
        user = new User();
        user.setId(userId);
        user.setEmail("old@waylo.test");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "hash")).thenReturn(true);
    }

    @Test
    void request_storesPendingEmail_sendsLinkToNewAndNoticeToOld_withoutChangingEmail() {
        accountService.requestEmailChange(userId, "  New@Waylo.TEST ", "pw");

        assertThat(user.getEmail()).isEqualTo("old@waylo.test");
        assertThat(user.getPendingEmail()).isEqualTo("new@waylo.test");
        assertThat(user.getVerificationToken()).isNotNull();
        assertThat(user.getVerificationTokenExpiry()).isAfter(LocalDateTime.now());
        ArgumentCaptor<AccountService.AuthEvent> cap = ArgumentCaptor.forClass(AccountService.AuthEvent.class);
        verify(publisher, times(2)).publish(cap.capture());
        List<AccountService.AuthEvent> evs = cap.getAllValues();
        assertThat(evs.get(0).eventType()).isEqualTo("VERIFICATION");
        assertThat(evs.get(0).email()).isEqualTo("new@waylo.test");
        assertThat(evs.get(0).token()).isEqualTo(user.getVerificationToken());
        assertThat(evs.get(1).eventType()).isEqualTo("EMAIL_CHANGE_REQUESTED");
        assertThat(evs.get(1).email()).isEqualTo("old@waylo.test");
        verify(refreshTokens, never()).revokeAll(any());
    }

    @Test
    void request_wrongPassword_400() {
        assertThatThrownBy(() -> accountService.requestEmailChange(userId, "new@waylo.test", "bad"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publish(any());
    }

    @Test
    void request_duplicateEmail_409() {
        when(users.existsByEmailIgnoreCase("taken@waylo.test")).thenReturn(true);
        assertThatThrownBy(() -> accountService.requestEmailChange(userId, "taken@waylo.test", "pw"))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(user.getPendingEmail()).isNull();
        verify(publisher, never()).publish(any());
    }

    @Test
    void request_sameEmail_400() {
        assertThatThrownBy(() -> accountService.requestEmailChange(userId, "OLD@waylo.test", "pw"))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirm_switchesEmail_revokesSessions_emitsUserUpdated() {
        user.setPendingEmail("new@waylo.test");
        user.setVerificationToken("tok");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
        when(users.findByVerificationToken("tok")).thenReturn(Optional.of(user));

        assertThat(authService.verifyEmail("tok").success()).isTrue();

        assertThat(user.getEmail()).isEqualTo("new@waylo.test");
        assertThat(user.getPendingEmail()).isNull();
        assertThat(user.getVerificationToken()).isNull();
        verify(refreshTokens).revokeAll(userId);
        ArgumentCaptor<AccountService.AuthEvent> cap = ArgumentCaptor.forClass(AccountService.AuthEvent.class);
        verify(publisher).publish(cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("USER_UPDATED");
        assertThat(cap.getValue().email()).isEqualTo("new@waylo.test");
        assertThat(cap.getValue().name()).isEqualTo("Ada Lovelace");
    }

    @Test
    void confirm_emailTakenMeanwhile_failsAndKeepsOldEmail() {
        user.setPendingEmail("new@waylo.test");
        user.setVerificationToken("tok");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
        when(users.findByVerificationToken("tok")).thenReturn(Optional.of(user));
        when(users.existsByEmailIgnoreCase("new@waylo.test")).thenReturn(true);

        assertThat(authService.verifyEmail("tok").success()).isFalse();
        assertThat(user.getEmail()).isEqualTo("old@waylo.test");
        assertThat(user.getPendingEmail()).isNull();
        verify(refreshTokens, never()).revokeAll(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void registrationVerification_stillSendsWelcome() {
        user.setEmailVerified(false);
        user.setVerificationToken("tok");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
        when(users.findByVerificationToken("tok")).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(authService, "verificationExpirationHours", 24);

        assertThat(authService.verifyEmail("tok").success()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        ArgumentCaptor<AccountService.AuthEvent> cap = ArgumentCaptor.forClass(AccountService.AuthEvent.class);
        verify(publisher).publish(cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("WELCOME");
        verify(refreshTokens, never()).revokeAll(any());
    }
}
