package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.publication.PublicationDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Platform admin side. The gateway verifies the JWT and sets X-User-Role; the service re-checks the value. */
@RestController
public class AdminHotelPublicationController {

    private final HotelPublicationService service;

    public AdminHotelPublicationController(HotelPublicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/hotel-publications")
    public ResponseEntity<ApiResponse<AdminPage>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminList(status, kind, hotelId, page, size)));
    }

    @GetMapping("/api/admin/hotel-publications/{requestId}")
    public ResponseEntity<ApiResponse<AdminDetail>> detail(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID requestId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminDetail(requestId)));
    }

    @PostMapping("/api/admin/hotel-publications/{requestId}/decisions")
    public ResponseEntity<ApiResponse<RequestView>> decide(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID requestId,
            @RequestBody AdminDecisionRequest body) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminDecide(userId, requestId, body), "Decision recorded"));
    }

    @PostMapping("/api/admin/hotels/{hotelId}/publication-status")
    public ResponseEntity<ApiResponse<HotelResponse>> setStatus(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID hotelId,
            @RequestBody AdminStatusRequest body) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminSetStatus(userId, hotelId, body), "Publication status updated"));
    }

    static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }
}
