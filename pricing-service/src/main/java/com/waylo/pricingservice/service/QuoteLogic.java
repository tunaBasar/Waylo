package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.domain.DiscountType;
import com.waylo.pricingservice.domain.FundedBy;
import com.waylo.pricingservice.dto.PreviewResponse;
import com.waylo.pricingservice.dto.PreviewDiscountLine;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pure quote computation logic. No database access.
 * All monetary fields use HALF_EVEN rounding.
 * Times use Europe/Istanbul timezone (§1).
 */
public class QuoteLogic {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final Optional<BigDecimal> commissionRate;

    public QuoteLogic(Optional<BigDecimal> commissionRate) {
        this.commissionRate = commissionRate;
    }

    /**
     * Compute a quote (with reservation possible).
     */
    public QuoteResponse computeQuote(QuoteRequest request, Campaign campaign) {
        if (request.promoCode() == null || request.promoCode().isEmpty()) {
            return zeroDiscountQuote(request);
        }
        return quotedWithPromo(request, campaign);
    }

    /**
     * Compute a preview (no reservation).
     */
    public PreviewResponse computePreview(QuoteRequest request, Campaign campaign) {
        if (request.promoCode() == null || request.promoCode().isEmpty()) {
            return zeroDiscountPreview(request);
        }
        return previewWithPromo(request, campaign);
    }

    // -- no promo code --

    private QuoteResponse zeroDiscountQuote(QuoteRequest request) {
        return new QuoteResponse(
                request.grossAmount(),
                Collections.emptyList(),
                null,
                null);
    }

    private PreviewResponse zeroDiscountPreview(QuoteRequest request) {
        return new PreviewResponse(
                request.grossAmount(),
                Collections.emptyList(),
                null);
    }

    // -- with promo code --

    private QuoteResponse quotedWithPromo(QuoteRequest request, Campaign campaign) {
        BigDecimal discount = computeDiscount(campaign, request.grossAmount());
        BigDecimal net;
        String fundedBy = null;

        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            // Discount is zero or negative — net = gross, no lines
            net = request.grossAmount();
        } else if (discount.compareTo(request.grossAmount()) >= 0) {
            // Discount covers entire gross — net = 0, no lines
            net = BigDecimal.ZERO;
        } else {
            // Normal case: net = gross - discount
            net = request.grossAmount().subtract(discount);
            fundedBy = campaign.getFundedBy().name();

            if (FundedBy.PARTNER.equals(campaign.getFundedBy()) && commissionRate.isPresent()) {
                BigDecimal partnerShare = net.subtract(
                        request.grossAmount()
                                .multiply(commissionRate.get())
                                .setScale(2, RoundingMode.HALF_EVEN));
                if (partnerShare.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessRuleException(
                            "DISCOUNT_EXCEEDS_PARTNER_SHARE",
                            "Discount exceeds partner payout share. net=" + net
                                    + " gross*rate=" + request.grossAmount()
                                            .multiply(commissionRate.get())
                                            .setScale(2, RoundingMode.HALF_EVEN));
                }
            }
        }

        List<QuoteResponse.DiscountLine> lines = new ArrayList<>();
        if (discount.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(request.grossAmount()) < 0) {
            String desc = buildDiscountDescription(campaign, discount);
            lines.add(new QuoteResponse.DiscountLine(desc, discount, fundedBy));
        }

        return new QuoteResponse(net, lines, null, null);
    }

    private PreviewResponse previewWithPromo(QuoteRequest request, Campaign campaign) {
        BigDecimal discount = computeDiscount(campaign, request.grossAmount());
        BigDecimal net = request.grossAmount().subtract(discount).max(BigDecimal.ZERO);
        String fundedBy = null;

        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            fundedBy = campaign.getFundedBy().name();
        }

        List<PreviewDiscountLine> lines = new ArrayList<>();
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new PreviewDiscountLine(
                    buildDiscountDescription(campaign, discount), discount, fundedBy));
        } else {
            fundedBy = null;
        }

        return new PreviewResponse(net, lines, null);
    }

    // -- helpers --

    private BigDecimal computeDiscount(Campaign campaign, BigDecimal gross) {
        BigDecimal value = campaign.getDiscountValue();
        BigDecimal discount;

        switch (campaign.getDiscountType()) {
            case PERCENTAGE -> discount = gross.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
            case FIXED -> discount = value;
            default -> throw new IllegalArgumentException(
                    "Unknown discount type: " + campaign.getDiscountType());
        }

        return discount.max(BigDecimal.ZERO);
    }

    private String buildDiscountDescription(Campaign campaign, BigDecimal amount) {
        if (DiscountType.PERCENTAGE.equals(campaign.getDiscountType())) {
            return campaign.getName() + " (" + campaign.getDiscountValue().toPlainString() + "%)";
        }
        return campaign.getName() + " (" + amount + ")";
    }
}
