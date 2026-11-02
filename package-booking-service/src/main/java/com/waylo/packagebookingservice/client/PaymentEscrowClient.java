package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Internal payment-service escrow API. X-Internal-Token is added by {@link PaymentFeignConfig}. */
@FeignClient(name = "payment-service", contextId = "paymentEscrowClient", path = "/internal/escrow-items",
        configuration = PaymentFeignConfig.class)
public interface PaymentEscrowClient {

    /** HELD -> REFUNDED (idempotent). 422 ESCROW_ALREADY_RELEASED when the supplier was already paid. */
    @PostMapping("/{orderItemId}/refund")
    Envelope<Object> refund(@PathVariable("orderItemId") UUID orderItemId);

    /** HELD -> RELEASED (idempotent). Pays the supplier share. */
    @PostMapping("/{orderItemId}/release")
    Envelope<EscrowDto> release(@PathVariable("orderItemId") UUID orderItemId);

    /** Read only: what cancelling would do. Policy is the order item's stored snapshot. */
    @PostMapping("/{orderItemId}/cancellation-preview")
    Envelope<CancellationDto> preview(@PathVariable("orderItemId") UUID orderItemId, @org.springframework.web.bind.annotation.RequestBody PolicyDto policy);

    /** FULL_REFUND: HELD -> REFUNDED. NO_REFUND: HELD -> RELEASED to the partner. Idempotent. 422 when already released. */
    @PostMapping("/{orderItemId}/cancel")
    Envelope<CancellationDto> cancel(@PathVariable("orderItemId") UUID orderItemId, @org.springframework.web.bind.annotation.RequestBody PolicyDto policy);

    /** Voucher window ended without a scan: HELD -> RELEASED to the partner. Idempotent. */
    @PostMapping("/{orderItemId}/no-show")
    Envelope<CancellationDto> noShow(@PathVariable("orderItemId") UUID orderItemId);

    record PolicyDto(String policy, Integer freeHoursBefore, java.time.Instant serviceStartAt) {}

    record CancellationDto(UUID orderItemId, String outcome, boolean cancellable, String blockedReason,
                           java.math.BigDecimal chargedAmount, java.math.BigDecimal refundAmount, java.math.BigDecimal supplierAmount,
                           java.math.BigDecimal commissionAmount, String currency, String status) {}

    record EscrowDto(UUID orderItemId, java.math.BigDecimal supplierAmount, String currency, String status) {}

    record Envelope<T>(boolean success, T data, String message) {}
}
