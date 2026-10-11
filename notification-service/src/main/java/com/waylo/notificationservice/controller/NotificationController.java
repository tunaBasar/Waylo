package com.waylo.notificationservice.controller;

import com.waylo.notificationservice.dto.ApiResponse;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> send(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(notificationService.send(request), "Notification queued"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getAll()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getByCustomerId(customerId)));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getByBookingId(bookingId)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.retry(id), "Notification retrying"));
    }
}
