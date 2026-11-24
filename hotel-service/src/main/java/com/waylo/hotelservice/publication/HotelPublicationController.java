package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.publication.PublicationDtos.*;
import com.waylo.hotelservice.security.HotelAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Partner side of the publication workflow. The guard runs here, before the service opens its transaction. */
@RestController
@RequestMapping("/api/hotels/{hotelId}")
public class HotelPublicationController {

    private final HotelPublicationService service;
    private final HotelAccessGuard accessGuard;

    public HotelPublicationController(HotelPublicationService service, HotelAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/publication-requests")
    public ResponseEntity<ApiResponse<RequestView>> submit(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody(required = false) SubmitRequest body) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.submit(userId, hotelId, body), "Publication request submitted"));
    }

    @GetMapping("/publication-requests")
    public ResponseEntity<ApiResponse<List<PartnerRequestItem>>> list(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.listForHotel(hotelId)));
    }

    @PostMapping("/publication-requests/{requestId}/withdraw")
    public ResponseEntity<ApiResponse<RequestView>> withdraw(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId, @PathVariable UUID requestId) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.withdraw(userId, hotelId, requestId), "Request withdrawn"));
    }

    @GetMapping("/content")
    public ResponseEntity<ApiResponse<ContentView>> content(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.getContent(hotelId)));
    }

    @PatchMapping("/content")
    public ResponseEntity<ApiResponse<RevisionView>> patchContent(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestBody PatchContentRequest body) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.patchContent(userId, hotelId, body), "Revision saved"));
    }

    @DeleteMapping("/content/revision")
    public ResponseEntity<Void> discardRevision(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {
        accessGuard.requireHotelManage(userId, hotelId);
        service.discardRevision(hotelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/archive")
    public ResponseEntity<ApiResponse<HotelResponse>> archive(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @RequestBody ReasonRequest body) {
        accessGuard.requireHotelManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.archiveByPartner(userId, hotelId, body.reason()), "Hotel archived"));
    }
}
