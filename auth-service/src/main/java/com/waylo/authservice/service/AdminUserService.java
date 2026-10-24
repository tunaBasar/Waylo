package com.waylo.authservice.service;

import com.waylo.authservice.domain.User;
import com.waylo.authservice.domain.UserRole;
import com.waylo.authservice.dto.AdminUserResponse;
import com.waylo.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminUserService(UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findByEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse changeRole(UUID targetId, UserRole newRole, UUID adminId) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            if (targetId.equals(adminId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Admins cannot demote themselves");
            }
            if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot demote the last admin");
            }
        }

        if (user.getRole() != newRole) {
            user.setRole(newRole);
            userRepository.save(user);
        }
        refreshTokenService.revokeAll(targetId);
        log.info("User {} role set to {} by admin {}", targetId, newRole, adminId);
        return toResponse(user);
    }

    private static AdminUserResponse toResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.isEmailVerified());
    }
}
