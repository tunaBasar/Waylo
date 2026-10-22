package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.AuthResponse;
import com.waylo.authservice.dto.ChangePasswordRequest;
import com.waylo.authservice.dto.UpdateProfileRequest;
import com.waylo.authservice.dto.UserProfileResponse;
import com.waylo.authservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accountService;

    public MeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        UserProfileResponse profile = accountService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("OK", profile));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest req) {
        UserProfileResponse profile = accountService.updateProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Profil guncellendi", profile));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChangePasswordRequest req) {
        AuthResponse authResponse = accountService.changePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Sifre guncellendi", authResponse));
    }
}
