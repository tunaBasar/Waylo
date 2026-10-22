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

import static org.springframework.http.HttpStatus.*;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kullanici bulunamadi"));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kullanici bulunamadi"));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        userRepository.save(user);
        return UserProfileResponse.from(user);
    }

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
