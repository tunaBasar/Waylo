package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.QuoteCreateRequest;
import com.waylo.hotelservice.dto.QuoteResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal quote management endpoints.
 * Requires X-User-Id header from gateway.
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuoteResponse>> createQuote(
            @RequestHeader(value = "X-User-Id", required = true) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody QuoteCreateRequest request) {

        if ("ADMIN".equals(role)) {
            throw new AccessDeniedException("Platform administrators cannot request quotes, use a customer account",
                    AccessDeniedException.ADMIN_CANNOT_PURCHASE);
        }

        if (userId == null) {
            throw new AuthenticationRequiredException("X-User-Id header is required");
        }

        QuoteResponse response = quoteService.createQuote(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Quote created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(
            @RequestHeader(value = "X-User-Id", required = true) UUID userId,
            @PathVariable UUID id) {

        if (userId == null) {
            throw new AuthenticationRequiredException("X-User-Id header is required");
        }

        QuoteResponse response = quoteService.getQuote(id, userId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
