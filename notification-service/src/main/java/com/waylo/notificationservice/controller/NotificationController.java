package com.waylo.notificationservice.controller;

import com.waylo.notificationservice.dto.ApiResponse;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.AccessDeniedException;
import com.waylo.notificationservice.exception.AuthenticationRequiredException;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The gateway verifies the JWT and sets X-User-Id / X-User-Role. Rules (NOTIFICATION-CONTRACT.md):
 * ADMIN everything. Customer: only own notifications (GET /me, /customer/{ownId}, /{id} and /booking/{id}
 * filtered to own rows; a foreign id answers 404 so existence is not leaked). Send and retry are admin only.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> send(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody NotificationRequest request) {
        requireAdmin(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(notificationService.send(request), "Notification queued"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> mine(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getByCustomerId(requireUser(userId))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id) {
        UUID caller = requireUser(userId);
        NotificationResponse n = notificationService.getById(id);
        if (!isAdmin(role) && !caller.equals(n.customerId())) {
            throw new ResourceNotFoundException("Notification", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.of(n));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(notificationService.getAll()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByCustomerId(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID customerId) {
        UUID caller = requireUser(userId);
        if (!isAdmin(role) && !caller.equals(customerId)) {
            throw new AccessDeniedException("Notifications of another customer are not accessible");
        }
        return ResponseEntity.ok(ApiResponse.of(notificationService.getByCustomerId(customerId)));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByBookingId(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID bookingId) {
        UUID caller = requireUser(userId);
        List<NotificationResponse> all = notificationService.getByBookingId(bookingId);
        if (!isAdmin(role)) all = all.stream().filter(n -> caller.equals(n.customerId())).toList();
        return ResponseEntity.ok(ApiResponse.of(all));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(notificationService.retry(id), "Notification retrying"));
    }

    private static boolean isAdmin(String role) { return "ADMIN".equals(role); }

    private static UUID requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new AuthenticationRequiredException("Authentication required");
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationRequiredException("Authentication required");
        }
    }

    private static void requireAdmin(String userId, String role) {
        requireUser(userId);
        if (!isAdmin(role)) throw new AccessDeniedException("Admin role required");
    }
}
