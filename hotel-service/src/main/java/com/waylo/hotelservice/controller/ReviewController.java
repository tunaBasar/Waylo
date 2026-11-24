package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.domain.ReviewStatus;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.ReviewModerationRequest;
import com.waylo.hotelservice.dto.ReviewRequest;
import com.waylo.hotelservice.dto.ReviewResponse;
import com.waylo.hotelservice.dto.ReviewTextRequest;
import com.waylo.hotelservice.dto.ReviewableItemResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {

    private final ReviewService service;
    private final HotelAccessGuard guard;

    public ReviewController(ReviewService service, HotelAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping("/api/me/hotel-reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                              @RequestHeader(value = "X-User-Role", required = false) String role,
                                                              @Valid @RequestBody ReviewRequest request) {
        if ("ADMIN".equals(role)) throw new AccessDeniedException("Platform administrators cannot write reviews");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.submit(userId, request), "Review created"));
    }

    @GetMapping("/api/me/hotel-reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> own(@RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(service.listOwn(userId)));
    }

    /** Eligible items come from package-booking-service (/internal/orders/items/review-eligible), minus reviewed ones. */
    @GetMapping("/api/me/reviewable-hotel-items")
    public ResponseEntity<ApiResponse<List<ReviewableItemResponse>>> reviewable(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(service.listReviewable(userId)));
    }

    @GetMapping("/api/hotels/{id}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse.Page>> publicList(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                                       @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                       @PathVariable UUID id,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "10") int size) {
        guard.requireReadable(userId, role, id);
        return ResponseEntity.ok(ApiResponse.of(service.listPublic(id, page, size)));
    }

    @GetMapping("/api/partner/organizations/{orgId}/hotel-reviews")
    public ResponseEntity<ApiResponse<ReviewResponse.Page>> partnerList(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID orgId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (!guard.canReadOrganization(userId, role, orgId)) throw new AccessDeniedException("Not allowed to perform this action");
        return ResponseEntity.ok(ApiResponse.of(service.listForOrganization(orgId, page, size)));
    }

    /** Partner may only answer. There is deliberately no endpoint to change the score or delete a review. */
    @PostMapping("/api/partner/organizations/{orgId}/hotel-reviews/{id}/response")
    public ResponseEntity<ApiResponse<ReviewResponse>> respond(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID orgId, @PathVariable UUID id, @Valid @RequestBody ReviewTextRequest request) {
        UUID reviewOrg = service.organizationIdOf(id);
        if (reviewOrg == null || !reviewOrg.equals(orgId)) throw new ResourceNotFoundException("Review", "id", id);
        guard.requireHotelManage(userId, service.hotelIdOf(id));
        return ResponseEntity.ok(ApiResponse.of(service.respond(id, userId, request.text()), "Response saved"));
    }

    @GetMapping("/api/admin/hotel-reviews")
    public ResponseEntity<ApiResponse<ReviewResponse.Page>> adminList(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(service.listAdmin(role, status, page, size)));
    }

    @PostMapping("/api/admin/hotel-reviews/{id}/moderate")
    public ResponseEntity<ApiResponse<ReviewResponse>> moderate(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id, @Valid @RequestBody ReviewModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.of(service.moderate(role, userId, id, request.status(), request.reason()),
                "Review moderated"));
    }
}
