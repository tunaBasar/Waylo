package com.waylo.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Internal package-booking-service order API. X-Internal-Token is added by {@link OrderFeignConfig}. */
@FeignClient(name = "package-booking-service", contextId = "orderClient", path = "/internal/orders",
        configuration = OrderFeignConfig.class)
public interface OrderClient {

    @GetMapping("/{orderId}")
    Envelope<OrderDto> getOrder(@PathVariable("orderId") UUID orderId);

    /** Idempotent. Package-booking confirms the hotel hold(s) and sets the order CONFIRMED. */
    @PostMapping("/{orderId}/paid")
    Envelope<OrderDto> markPaid(@PathVariable("orderId") UUID orderId);

    record Envelope<T>(boolean success, T data, String message) {}

    record OrderDto(UUID id, UUID customerId, String status, String currency, BigDecimal totalAmount,
                    List<OrderItemDto> items, List<UUID> refundItemIds) {
        public OrderDto(UUID id, UUID customerId, String status, String currency, BigDecimal totalAmount, List<OrderItemDto> items) {
            this(id, customerId, status, currency, totalAmount, items, List.of());
        }
    }

    /** amount is the item subtotal = net, what the customer pays. grossAmount is the supplier price (commission base). vertical is the package-booking vertical name. */
    record OrderItemDto(UUID orderItemId, String vertical, UUID supplierOrganizationId, BigDecimal amount, String currency,
                        BigDecimal grossAmount, BigDecimal discountAmount, String discountFundedBy) {
        /** No discount: gross = amount. */
        public OrderItemDto(UUID orderItemId, String vertical, UUID supplierOrganizationId, BigDecimal amount, String currency) {
            this(orderItemId, vertical, supplierOrganizationId, amount, currency, amount, BigDecimal.ZERO, null);
        }

        public BigDecimal effectiveGross() { return grossAmount != null ? grossAmount : amount; }
        public BigDecimal effectiveDiscount() { return discountAmount != null ? discountAmount : BigDecimal.ZERO; }
    }
}
