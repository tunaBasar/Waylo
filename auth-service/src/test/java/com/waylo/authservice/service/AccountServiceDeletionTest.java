package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.*;
import com.waylo.authservice.dto.UpdateProfileRequest;
import com.waylo.authservice.repository.OrganizationMembershipRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;
import com.waylo.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceDeletionTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private UserRepository userRepository;
    private PasswordEncoder encoder;
    private RefreshTokenService refreshTokenService;
    private ApplicationEventPublisher publisher;
    private OrganizationMembershipRepository membershipRepository;
    private RoleAssignmentRepository roleRepository;
    private AccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        publisher = mock(ApplicationEventPublisher.class);
        membershipRepository = mock(OrganizationMembershipRepository.class);
        roleRepository = mock(RoleAssignmentRepository.class);
        service = new AccountService(userRepository, encoder, mock(JwtUtil.class), refreshTokenService,
                publisher, membershipRepository, roleRepository);
        user = new User();
        user.setId(userId);
        user.setEmail("a@b.com");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setPassword("hash");
        user.setRole(UserRole.ADMIN);
        user.setEmailVerified(true);
        user.setResetToken("rt");
        user.setVerificationToken("vt");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "hash")).thenReturn(true);
        when(encoder.encode(any())).thenReturn("newhash");
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of());
    }

    private OrganizationMembership ownerMembership(long otherOwners) {
        Organization org = new Organization("Org");
        ReflectionTestUtils.setField(org, "id", orgId);
        org.setStatus(OrganizationStatus.ACTIVE);
        OrganizationMembership m = new OrganizationMembership(user, org);
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        RoleAssignment ra = mock(RoleAssignment.class);
        when(ra.getRole()).thenReturn(Role.ORGANIZATION_OWNER);
        when(roleRepository.findByMembershipId(m.getId())).thenReturn(List.of(ra));
        when(roleRepository.countActiveOrganizationOwners(orgId)).thenReturn(1 + otherOwners);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(m));
        return m;
    }

    private void assertNoSideEffects(OrganizationMembership m) {
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
        verify(publisher, never()).publishEvent(any(Object.class));
        assertEquals("a@b.com", user.getEmail());
        assertNull(user.getDeletedAt());
        if (m != null) assertEquals(MembershipStatus.ACTIVE, m.getStatus());
    }

    @Test
    void deleteAccount_wrongPassword_401NoSideEffects() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount(userId, "bad"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertNoSideEffects(null);
    }

    @Test
    void deleteAccount_soleOwner_422NoSideEffects() {
        OrganizationMembership m = ownerMembership(0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount(userId, "pw"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertNoSideEffects(m);
    }

    @Test
    void deleteAccount_otherActiveOwner_allowed() {
        OrganizationMembership m = ownerMembership(1);
        service.deleteAccount(userId, "pw");
        assertEquals(MembershipStatus.REVOKED, m.getStatus());
        assertNotNull(user.getDeletedAt());
    }

    @Test
    void deleteAccount_success_anonymizesRevokesAndPublishes() {
        OrganizationMembership m = ownerMembership(1);
        service.deleteAccount(userId, "pw");

        assertEquals("deleted+" + userId + "@deleted.invalid", user.getEmail());
        assertEquals("Silinmis", user.getFirstName());
        assertEquals("Kullanici", user.getLastName());
        assertEquals("newhash", user.getPassword());
        assertEquals(UserRole.USER, user.getRole());
        assertFalse(user.isEmailVerified());
        assertNull(user.getResetToken());
        assertNull(user.getVerificationToken());
        assertNotNull(user.getDeletedAt());
        assertEquals(MembershipStatus.REVOKED, m.getStatus());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAll(userId);
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(cap.capture());
        AccountService.AuthEvent ev = (AccountService.AuthEvent) cap.getValue();
        assertEquals("USER_DELETED", ev.eventType());
        assertEquals(userId, ev.userId());
    }

    @Test
    void deleteAccount_alreadyDeleted_401() {
        user.setDeletedAt(java.time.LocalDateTime.now());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount(userId, "pw"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getProfile_deletedUser_rejected() {
        user.setDeletedAt(java.time.LocalDateTime.now());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getProfile(userId));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void updateProfile_publishesProfileUpdated() {
        service.updateProfile(userId, new UpdateProfileRequest(" Grace ", " Hopper "));
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(cap.capture());
        AccountService.AuthEvent ev = (AccountService.AuthEvent) cap.getValue();
        assertEquals("PROFILE_UPDATED", ev.eventType());
        assertEquals("a@b.com", ev.email());
        assertEquals("Grace Hopper", ev.name());
    }

    @Test
    void updateProfile_deletedUser_rejected() {
        user.setDeletedAt(java.time.LocalDateTime.now());
        assertThrows(ResponseStatusException.class,
                () -> service.updateProfile(userId, new UpdateProfileRequest("A", "B")));
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
