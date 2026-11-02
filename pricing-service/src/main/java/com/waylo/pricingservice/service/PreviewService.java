package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.dto.PreviewRequest;
import com.waylo.pricingservice.dto.PreviewResponse;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.repository.CampaignRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Customer-facing preview service (§5).
 * Reads from Campaign to compute preview with a promo code.
 */
public class PreviewService {

    private final QuoteLogic quoteLogic;
    private final CampaignRepository campaignRepo;

    public PreviewService(QuoteLogic quoteLogic, CampaignRepository campaignRepo) {
        this.quoteLogic = quoteLogic;
        this.campaignRepo = campaignRepo;
    }

    /**
     * Find an active campaign and compute a preview.
     * Does not create reservations.
     */
    public PreviewResponse preview(PreviewRequest request, String promoCode) {
        LocalDateTime now = LocalDateTime.now();
        List<Campaign> candidates = campaignRepo.findActiveForVerticalAndOrg(
                request.vertical(),
                request.supplierOrganizationId(),
                now);

        if (candidates.isEmpty()) {
            // Try platform-level campaigns (no scopeOrgId)
            candidates = campaignRepo.findActivePlatformForVertical(
                    request.vertical(), now);
        }

        if (candidates.isEmpty()) {
            // No campaign found: return zero discount preview (200)
            return new PreviewResponse(
                    request.grossAmount(),
                    Collections.emptyList(),
                    null);
        }

        Campaign campaign = candidates.get(0);

        // Build a request object for quote logic
        QuoteRequest quoteReq = new QuoteRequest(
                request.vertical(),
                request.supplierOrganizationId(),
                request.grossAmount(),
                request.currency(),
                "guest",
                promoCode,
                null,
                null);

        return quoteLogic.computePreview(quoteReq, campaign);
    }
}
