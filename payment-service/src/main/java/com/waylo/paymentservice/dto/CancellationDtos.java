package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.CancellationPolicy;
import com.waylo.paymentservice.domain.EscrowStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CancellationDtos {
    private CancellationDtos() {}

    /** Policy snapshot of the order item, sent by package-booking-service. */
    public record PolicyRequest(@NotNull CancellationPolicy policy, Integer freeHoursBefore, Instant serviceStartAt) {}

    /** outcome: REFUND | NO_REFUND. refundAmount is the charged net amount on REFUND, else 0. */
    public record CancellationResult(UUID orderItemId, String outcome, boolean cancellable, String blockedReason,
                                     BigDecimal chargedAmount, BigDecimal refundAmount, BigDecimal supplierAmount,
                                     BigDecimal commissionAmount, String currency, EscrowStatus status) {}
}
