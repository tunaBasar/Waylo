package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.CancellationPolicy;
import com.waylo.paymentservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.waylo.paymentservice.service.CancellationEvaluator.Decision.FULL_REFUND;
import static com.waylo.paymentservice.service.CancellationEvaluator.Decision.NO_REFUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
    private static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));

    @Test
    void freeCancellationBeforeStartRefunds() {
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_CANCELLATION, null, TOMORROW, NOW)).isEqualTo(FULL_REFUND);
    }

    @Test
    void nullPolicyMeansFreeCancellation() {
        assertThat(CancellationEvaluator.decide(null, null, TOMORROW, NOW)).isEqualTo(FULL_REFUND);
    }

    @Test
    void nonRefundableNeverRefunds() {
        assertThat(CancellationEvaluator.decide(CancellationPolicy.NON_REFUNDABLE, null, TOMORROW, NOW)).isEqualTo(NO_REFUND);
    }

    @Test
    void freeUntilRefundsBeforeDeadlineOnly() {
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_UNTIL, 12, TOMORROW, NOW)).isEqualTo(FULL_REFUND);
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_UNTIL, 48, TOMORROW, NOW)).isEqualTo(NO_REFUND);
    }

    @Test
    void freeUntilWithoutHoursIsRejected() {
        assertThatThrownBy(() -> CancellationEvaluator.decide(CancellationPolicy.FREE_UNTIL, null, TOMORROW, NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nothingIsRefundableOnceTheServiceHasStarted() {
        // Gap between service start and the no-show job: a FREE_CANCELLATION item must not be refundable any more.
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_CANCELLATION, null, YESTERDAY, NOW)).isEqualTo(NO_REFUND);
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_CANCELLATION, null, NOW, NOW)).isEqualTo(NO_REFUND);
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_UNTIL, 0, YESTERDAY, NOW)).isEqualTo(NO_REFUND);
    }

    @Test
    void unknownServiceStartFallsBackToPolicy() {
        assertThat(CancellationEvaluator.decide(CancellationPolicy.FREE_CANCELLATION, null, null, NOW)).isEqualTo(FULL_REFUND);
    }
}
