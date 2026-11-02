package com.waylo.pricingservice.controller;

import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal service-to-service endpoints (§3).
 * Protected by InternalTokenFilter.
 */
@RestController
@RequestMapping("/internal/pricing")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * Compute a pricing quote with optional promo code.
     * POST /internal/pricing/quote
     */
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(@RequestBody @Valid QuoteRequest request) {
        return ResponseEntity.ok(quoteService.computeQuote(request));
    }

    /**
     * Release a promo reservation by orderId. Idempotent: 200 even if no reservation exists.
     * POST /internal/pricing/reservations/{orderId}/release
     */
    @PostMapping("/reservations/{orderId}/release")
    public ResponseEntity<Void> releaseReservation(
            @PathVariable UUID orderId) {
        quoteService.releaseReservationByOrderId(orderId);
        return ResponseEntity.ok().build();
    }
}
