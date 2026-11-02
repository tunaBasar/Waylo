package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.CancellationPolicy;
import com.waylo.paymentservice.exception.BusinessRuleException;

import java.time.Duration;
import java.time.Instant;

/** Pure policy evaluation. Outcome is all or nothing: FULL_REFUND of the charged (net) amount, or NO_REFUND. */
public final class CancellationEvaluator {

    public enum Decision { FULL_REFUND, NO_REFUND }

    private CancellationEvaluator() {}

    public static Decision decide(CancellationPolicy policy, Integer freeHoursBefore, Instant serviceStartAt, Instant now) {
        if (policy == null) policy = CancellationPolicy.FREE_CANCELLATION;
        // Once the service has started, nothing is refundable any more: the slot was blocked for the customer and the
        // no-show job would release it to the partner anyway (CANCELLATION-CONTRACT.md no-show rule). Without this a
        // FREE_CANCELLATION item could still be refunded in the gap between service start and the no-show run.
        if (serviceStartAt != null && !now.isBefore(serviceStartAt)) {
            return Decision.NO_REFUND;
        }
        return switch (policy) {
            case FREE_CANCELLATION -> Decision.FULL_REFUND;
            case NON_REFUNDABLE -> Decision.NO_REFUND;
            case FREE_UNTIL -> {
                if (freeHoursBefore == null || freeHoursBefore < 0 || serviceStartAt == null) {
                    throw new BusinessRuleException("INVALID_CANCELLATION_POLICY", "FREE_UNTIL needs freeHoursBefore and serviceStartAt");
                }
                Instant deadline = serviceStartAt.minus(Duration.ofHours(freeHoursBefore));
                yield now.isAfter(deadline) ? Decision.NO_REFUND : Decision.FULL_REFUND;
            }
        };
    }
}
