package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalRulesTest {

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(20, 0);

    private int validate(LocalDateTime p, LocalDateTime r) {
        return RentalRules.validateTimes(NOW, p, r, RentalRules.toInstant(p), RentalRules.toInstant(r), OPEN, CLOSE, OPEN, CLOSE);
    }

    @Test
    void chargeableDays_24h01m_isTwoDays() {
        Instant p = Instant.parse("2027-02-01T10:00:00Z");
        assertThat(RentalRules.chargeableDays(p, p.plusSeconds(24 * 3600 + 60))).isEqualTo(2);
        assertThat(RentalRules.chargeableDays(p, p.plusSeconds(24 * 3600))).isEqualTo(1);
        assertThat(RentalRules.chargeableDays(p, p.plusSeconds(1800))).isEqualTo(1);
    }

    @Test
    void total_multipliesDaysAndAddsOneWayFee() {
        assertThat(RentalRules.total(3, new BigDecimal("100.00"), new BigDecimal("50.00"))).isEqualByComparingTo("350.00");
    }

    @Test
    void pickupLessThanTwoHoursAhead_isInvalidTime() {
        LocalDateTime p = LocalDateTime.of(2027, 1, 1, 4, 0);
        assertThatThrownBy(() -> RentalRules.validateTimes(NOW, p, p.plusDays(1), RentalRules.toInstant(p),
                RentalRules.toInstant(p.plusDays(1)), LocalTime.MIN, LocalTime.MAX, LocalTime.MIN, LocalTime.MAX))
                .isInstanceOf(BusinessRuleException.class).extracting("ruleCode").isEqualTo("INVALID_TIME");
    }

    @Test
    void returnNotAfterPickup_isInvalidTime() {
        LocalDateTime p = LocalDateTime.of(2027, 2, 1, 10, 0);
        assertThatThrownBy(() -> validate(p, p)).extracting("ruleCode").isEqualTo("INVALID_TIME");
    }

    @Test
    void minutesMustBeZeroOrThirty() {
        LocalDateTime p = LocalDateTime.of(2027, 2, 1, 10, 15);
        assertThatThrownBy(() -> validate(p, p.plusDays(1))).extracting("ruleCode").isEqualTo("INVALID_TIME");
        assertThat(validate(LocalDateTime.of(2027, 2, 1, 10, 30), LocalDateTime.of(2027, 2, 2, 10, 30))).isEqualTo(1);
    }

    @Test
    void outsideBranchHours_isRejected() {
        LocalDateTime p = LocalDateTime.of(2027, 2, 1, 7, 30);
        assertThatThrownBy(() -> validate(p, p.plusDays(1))).extracting("ruleCode").isEqualTo("OUTSIDE_BRANCH_HOURS");
        LocalDateTime p2 = LocalDateTime.of(2027, 2, 1, 10, 0);
        assertThatThrownBy(() -> validate(p2, LocalDateTime.of(2027, 2, 2, 20, 30)))
                .extracting("ruleCode").isEqualTo("OUTSIDE_BRANCH_HOURS");
    }

    @Test
    void moreThanThirtyDays_isInvalidTime() {
        LocalDateTime p = LocalDateTime.of(2027, 2, 1, 10, 0);
        assertThatThrownBy(() -> validate(p, p.plusDays(31))).extracting("ruleCode").isEqualTo("INVALID_TIME");
        assertThat(validate(p, p.plusDays(30))).isEqualTo(30);
    }
}
