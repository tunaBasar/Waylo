package com.waylo.notificationservice.controller;

import com.waylo.notificationservice.dto.ApiResponse;
import com.waylo.notificationservice.dto.DeviceTokenRequest;
import com.waylo.notificationservice.dto.DeviceTokenResponse;
import com.waylo.notificationservice.exception.AuthenticationRequiredException;
import com.waylo.notificationservice.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Push device token registry. The caller is always the gateway-verified X-User-Id. */
@RestController
@RequestMapping("/api/me/devices")
public class DeviceController {
    private final DeviceTokenService service;
    public DeviceController(DeviceTokenService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> register(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody DeviceTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.of(service.register(requireUser(userId), request), "Device registered"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceTokenResponse>>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.of(service.list(requireUser(userId))));
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> unregister(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String token) {
        service.unregister(requireUser(userId), token);
        return ResponseEntity.noContent().build();
    }

    private static UUID requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new AuthenticationRequiredException("Authentication required");
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationRequiredException("Authentication required");
        }
    }
}
