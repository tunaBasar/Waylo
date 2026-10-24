package com.waylo.authservice.controller;

import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.dto.ChangeUserRoleRequest;
import com.waylo.authservice.repository.UserRepository;
import com.waylo.authservice.service.AdminUserService;
import com.waylo.authservice.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    private UserRepository repo;
    private RefreshTokenService tokens;
    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        tokens = mock(RefreshTokenService.class);
        controller = new AdminUserController(new AdminUserService(repo, tokens));
    }

    private User user(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail("x@y.com");
        u.setFirstName("X");
        u.setLastName("Y");
        u.setRole(role);
        return u;
    }

    private int statusOf(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void nonAdminGets403() {
        var req = new ChangeUserRoleRequest(UserRole.ADMIN);
        assertEquals(403, statusOf(() -> controller.changeRole(TARGET_ID, req, ADMIN_ID, "USER")));
        assertEquals(403, statusOf(() -> controller.findByEmail("a@b.com", ADMIN_ID, "USER")));
        assertEquals(403, statusOf(() -> controller.list(null, 0, 20, ADMIN_ID, "USER")));
        verifyNoInteractions(repo, tokens);
    }

    @Test
    void selfDemoteIs422() {
        when(repo.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN)));
        var req = new ChangeUserRoleRequest(UserRole.USER);

        assertEquals(422, statusOf(() -> controller.changeRole(ADMIN_ID, req, ADMIN_ID, "ADMIN")));
        verify(tokens, never()).revokeAll(any());
    }

    @Test
    void lastAdminDemoteIs422() {
        when(repo.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, UserRole.ADMIN)));
        when(repo.countByRole(UserRole.ADMIN)).thenReturn(1L);
        var req = new ChangeUserRoleRequest(UserRole.USER);

        assertEquals(422, statusOf(() -> controller.changeRole(TARGET_ID, req, ADMIN_ID, "ADMIN")));
        verify(tokens, never()).revokeAll(any());
    }

    @Test
    void successPromotesAndRevokesTokens() {
        User target = user(TARGET_ID, UserRole.USER);
        when(repo.findById(TARGET_ID)).thenReturn(Optional.of(target));

        var res = controller.changeRole(TARGET_ID, new ChangeUserRoleRequest(UserRole.ADMIN), ADMIN_ID, "ADMIN");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("ADMIN", res.getBody().data().role());
        assertEquals(UserRole.ADMIN, target.getRole());
        verify(tokens).revokeAll(TARGET_ID);
    }

    @Test
    void demoteWithOtherAdminsSucceeds() {
        User target = user(TARGET_ID, UserRole.ADMIN);
        when(repo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(repo.countByRole(UserRole.ADMIN)).thenReturn(2L);

        controller.changeRole(TARGET_ID, new ChangeUserRoleRequest(UserRole.USER), ADMIN_ID, "ADMIN");

        assertEquals(UserRole.USER, target.getRole());
        verify(tokens).revokeAll(TARGET_ID);
    }

    @Test
    void findByEmailNormalizes() {
        when(repo.findByEmailIgnoreCase("x@y.com")).thenReturn(Optional.of(user(TARGET_ID, UserRole.USER)));

        var res = controller.findByEmail("  X@Y.com ", ADMIN_ID, "ADMIN");

        assertEquals("x@y.com", res.getBody().data().email());
    }

    @Test
    void list_flagsDeletedAndCapsSize() {
        User deleted = user(UUID.randomUUID(), UserRole.USER);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(repo.search(eq("jo"), captor.capture())).thenReturn(new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(user(TARGET_ID, UserRole.USER), deleted),
                org.springframework.data.domain.PageRequest.of(0, 2), 42));

        var res = controller.list("  Jo ", 0, 1000, ADMIN_ID, "ADMIN");

        assertEquals(42L, res.getBody().total());
        assertEquals(2, res.getBody().data().size());
        assertNull(res.getBody().data().get(0).deletedAt());
        assertNotNull(res.getBody().data().get(1).deletedAt());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void list_blankQueryMeansNoFilter() {
        when(repo.search(isNull(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        assertEquals(0L, controller.list("  ", 0, 20, ADMIN_ID, "ADMIN").getBody().total());
    }
}
