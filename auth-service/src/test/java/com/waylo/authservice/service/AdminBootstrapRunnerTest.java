package com.waylo.authservice.service;

import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {

    private UserRepository repo;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("HASH");
    }

    private AdminBootstrapRunner runner(String email, String pw) {
        return new AdminBootstrapRunner(repo, encoder, email, pw);
    }

    @Test
    void createsAdminWithNormalizedEmail() {
        when(repo.findByEmailIgnoreCase("admin@waylo.io")).thenReturn(Optional.empty());

        runner("  Admin@Waylo.IO ", "secret1").run(null);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(repo).save(cap.capture());
        User u = cap.getValue();
        assertEquals("admin@waylo.io", u.getEmail());
        assertEquals(UserRole.ADMIN, u.getRole());
        assertTrue(u.isEmailVerified());
        assertEquals("HASH", u.getPassword());
    }

    @Test
    void promotesExistingUserWithoutChangingPassword() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setPassword("OLD");
        u.setRole(UserRole.USER);
        when(repo.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));

        runner("a@b.com", "secret1").run(null);

        assertEquals(UserRole.ADMIN, u.getRole());
        assertEquals("OLD", u.getPassword());
        verify(repo).save(u);
        verifyNoInteractions(encoder);
    }

    @Test
    void existingAdminIsUnchanged() {
        User u = new User();
        u.setRole(UserRole.ADMIN);
        when(repo.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));

        runner("a@b.com", "secret1").run(null);

        verify(repo, never()).save(any());
    }

    @Test
    void weakPasswordDoesNothing() {
        runner("a@b.com", "123").run(null);

        verifyNoInteractions(repo);
    }

    @Test
    void onlyOneVarSetDoesNothing() {
        runner("a@b.com", "").run(null);
        runner("", "secret1").run(null);

        verifyNoInteractions(repo);
    }

    @Test
    void repositoryFailureDoesNotCrash() {
        when(repo.findByEmailIgnoreCase(any())).thenThrow(new RuntimeException("db"));

        assertDoesNotThrow(() -> runner("a@b.com", "secret1").run(null));
    }
}
