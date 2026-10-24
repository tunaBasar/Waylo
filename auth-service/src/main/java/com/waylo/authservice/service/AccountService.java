package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.AuthResponse;
import com.waylo.authservice.dto.ChangePasswordRequest;
import com.waylo.authservice.dto.UpdateProfileRequest;
import com.waylo.authservice.dto.UserProfileResponse;
import com.waylo.authservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.domain.MembershipStatus;
import com.waylo.authservice.domain.OrganizationMembership;
import com.waylo.authservice.repository.OrganizationMembershipRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;

import static org.springframework.http.HttpStatus.*;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil, RefreshTokenService refreshTokenService, ApplicationEventPublisher eventPublisher,
                          OrganizationMembershipRepository membershipRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.membershipRepository = membershipRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kullanici bulunamadi"));
        if (user.getDeletedAt() != null) throw new ResponseStatusException(UNAUTHORIZED, "Kimlik dogrulanamadi");
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kullanici bulunamadi"));
        if (user.getDeletedAt() != null) throw new ResponseStatusException(UNAUTHORIZED, "Kimlik dogrulanamadi");
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        userRepository.save(user);
        eventPublisher.publishEvent(new AuthEvent("PROFILE_UPDATED", user.getId(), user.getEmail(), user.getFullName(), null));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Kimlik dogrulanamadi"));
        if (user.getDeletedAt() != null)
            throw new ResponseStatusException(UNAUTHORIZED, "Kimlik dogrulanamadi");
        // Wrong password is a form error, not an expired session: 400 like POST /api/me/password,
        // so the client does not treat it as a logout-worthy 401.
        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new ResponseStatusException(BAD_REQUEST, "Mevcut sifre hatali");
        {
            for (OrganizationMembership membership : membershipRepository.findByUserId(userId)) {
                if (membership.getStatus() == MembershipStatus.ACTIVE
                        && (membership.getOrganization().getStatus() == OrganizationStatus.ACTIVE || membership.getOrganization().getStatus() == OrganizationStatus.PENDING)
                        && roleAssignmentRepository.findByMembershipId(membership.getId()).stream()
                                .anyMatch(a -> a.getRole() == com.waylo.authservice.domain.Role.ORGANIZATION_OWNER)
                        && roleAssignmentRepository.countActiveOrganizationOwners(membership.getOrganization().getId()) <= 1) {
                    throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "SOLE_OWNER: Organizasyonda baska aktif sahip bulunmuyor");
                }
            }
        }
        user.setEmail("deleted+" + userId + "@deleted.invalid");
        user.setFirstName("Silinmis"); user.setLastName("Kullanici");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setVerificationToken(null); user.setVerificationTokenExpiry(null); user.setResetToken(null); user.setResetTokenExpiry(null);
        user.setEmailVerified(false);
        user.setRole(UserRole.USER); user.setDeletedAt(LocalDateTime.now());
        membershipRepository.findByUserId(userId).forEach(m -> m.setStatus(MembershipStatus.REVOKED));
        userRepository.save(user); refreshTokenService.revokeAll(userId);
        eventPublisher.publishEvent(new AuthEvent("USER_DELETED", userId, null, null, null));
    }

    public record AuthEvent(String eventType, UUID userId, String email, String name, String token) {}

    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kullanici bulunamadi"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Mevcut sifre hatali");
        }

        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Yeni sifre mevcut sifreden farkli olmali");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Revoke all sessions
        refreshTokenService.revokeAll(userId);

        // Issue fresh tokens
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user.getId(), null);

        return new AuthResponse(
                token, refreshToken, user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name(), user.isEmailVerified()
        );
    }
}
