package com.waylo.pricingservice.controller;

import com.waylo.pricingservice.dto.PreviewRequest;
import com.waylo.pricingservice.dto.PreviewResponse;
import com.waylo.pricingservice.service.PreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing preview endpoint (§5).
 */
@RestController
@RequestMapping("/api/pricing")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * Compute a pricing preview (no reservation).
     * POST /api/pricing/preview
     */
    @PostMapping("/preview")
    public ResponseEntity<PreviewResponse> preview(@RequestBody PreviewRequest request) {
        return ResponseEntity.ok(previewService.preview(request, request.promoCode()));
    }
}
