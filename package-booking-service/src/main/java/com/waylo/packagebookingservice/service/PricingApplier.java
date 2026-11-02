package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.PricingClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.PricingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Calls pricing-service once per order (PRICING-CONTRACT.md section 6). Without a promo code a pricing failure never
 * blocks the sale (zero discount). With a promo code it is a 503 PRICING_UNAVAILABLE so the customer's code is not dropped.
 */
@Component
public class PricingApplier {

    private static final Logger log = LoggerFactory.getLogger(PricingApplier.class);

    private final PricingClient pricingClient;

    public PricingApplier(PricingClient pricingClient) {
        this.pricingClient = pricingClient;
    }

    public record Priced(BigDecimal gross, BigDecimal discount, String fundedBy, BigDecimal net, BigDecimal tax,
                         String promoCode) {
        public static Priced none(BigDecimal gross) {
            return new Priced(gross, BigDecimal.ZERO.setScale(2), null, gross, null, null);
        }

        /** Writes the pricing columns and sets unit_amount = net. */
        public void applyTo(OrderItem item) {
            item.setGrossAmount(gross);
            item.setDiscountAmount(discount);
            item.setDiscountFundedBy(fundedBy);
            item.setNetAmount(net);
            item.setTaxAmount(tax);
            item.setPromoCode(promoCode);
            item.setUnitAmount(net);
        }
    }

    public Priced price(String vertical, UUID supplierOrganizationId, BigDecimal gross, String currency,
                        UUID customerId, String promoCode, UUID orderId) {
        String promo = promoCode == null || promoCode.isBlank() ? null : promoCode.trim();
        JsonNode quote;
        try {
            quote = pricingClient.quote(new PricingClient.QuoteRequest(vertical, supplierOrganizationId, gross, currency,
                    customerId.toString(), promo, orderId));
        } catch (BusinessRuleException e) {
            if (promo != null) throw e;
            log.warn("Pricing rejected a no-promo quote for order {} ({}), selling at gross", orderId, e.getRuleCode());
            return Priced.none(gross);
        } catch (RuntimeException e) {
            if (promo != null) throw new PricingUnavailableException("Pricing service unavailable: " + e.getMessage());
            log.warn("Pricing unavailable for order {}, selling at gross: {}", orderId, e.getMessage());
            return Priced.none(gross);
        }
        return fromQuote(quote, gross, promo);
    }

    static Priced fromQuote(JsonNode body, BigDecimal gross, String promo) {
        JsonNode q = body != null && body.has("data") && body.get("data").isObject() ? body.get("data") : body;
        if (q == null || !q.hasNonNull("netAmount")) {
            if (promo != null) throw new PricingUnavailableException("Pricing service returned no quote");
            return Priced.none(gross);
        }
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        String fundedBy = null;
        JsonNode lines = q.get("discountLines");
        if (lines != null && lines.isArray()) {
            for (JsonNode line : lines) {
                discount = discount.add(line.get("amount").decimalValue().setScale(2, RoundingMode.HALF_EVEN));
                if (fundedBy == null && line.hasNonNull("fundedBy")) fundedBy = line.get("fundedBy").asString();
            }
        }
        if (discount.signum() == 0) fundedBy = null;
        // gross - discount = net always holds, whatever pricing-service sent
        BigDecimal net = gross.subtract(discount);
        BigDecimal tax = q.hasNonNull("taxAmount") ? q.get("taxAmount").decimalValue().setScale(2, RoundingMode.HALF_EVEN) : null;
        return new Priced(gross, discount, fundedBy, net, tax, promo);
    }

    /** Idempotent, best effort: cancel must not depend on pricing-service. */
    public void release(UUID orderId) {
        try {
            pricingClient.release(orderId);
        } catch (RuntimeException e) {
            log.warn("Could not release promo reservation of order {}: {}", orderId, e.getMessage());
        }
    }
}
