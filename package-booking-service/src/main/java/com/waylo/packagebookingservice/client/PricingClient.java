package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.UUID;

/** Internal pricing-service API (PRICING-CONTRACT.md section 3). X-Internal-Token is added by {@link PricingFeignConfig}. */
@FeignClient(name = "pricing-service", contextId = "pricingClient", path = "/internal/pricing",
        configuration = PricingFeignConfig.class)
public interface PricingClient {

    /** Quote body, bare or wrapped in an ApiResponse envelope (both are accepted by PricingApplier). */
    @PostMapping("/quote")
    JsonNode quote(@RequestBody QuoteRequest request);

    /** Idempotent. */
    @PostMapping("/reservations/{orderId}/release")
    void release(@PathVariable("orderId") UUID orderId);

    record QuoteRequest(String vertical, UUID supplierOrganizationId, BigDecimal grossAmount, String currency,
                        String customerId, String promoCode, UUID orderId) {}
}
