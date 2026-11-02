package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Pure time and pricing rules from the car rental contract. */
public final class RentalRules {

    public static final int MAX_DAYS = 30;
    public static final Duration MIN_LEAD = Duration.ofHours(2);

    private RentalRules() { }

    /** ceil(minutes / 1440), at least 1. */
    public static int chargeableDays(Instant pickupAt, Instant returnAt) {
        long minutes = Duration.between(pickupAt, returnAt).toMinutes();
        long days = (minutes + 1439) / 1440;
        return (int) Math.max(1, days);
    }

    public static BigDecimal total(int days, BigDecimal dailyRate, BigDecimal oneWayFee) {
        return dailyRate.multiply(BigDecimal.valueOf(days)).add(oneWayFee).setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Validates instants and returns chargeable days. Throws 422 INVALID_TIME / OUTSIDE_BRANCH_HOURS. */
    public static int validateTimes(Instant now, LocalDateTime pickupLocal, LocalDateTime returnLocal,
                                    Instant pickupAt, Instant returnAt,
                                    LocalTime pickupOpen, LocalTime pickupClose,
                                    LocalTime returnOpen, LocalTime returnClose) {
        if (pickupAt.isBefore(now.plus(MIN_LEAD))) {
            throw new BusinessRuleException("INVALID_TIME", "Pickup must be at least 2 hours from now");
        }
        if (!returnAt.isAfter(pickupAt)) {
            throw new BusinessRuleException("INVALID_TIME", "Return must be after pickup");
        }
        requireHalfHour(pickupLocal);
        requireHalfHour(returnLocal);
        int days = chargeableDays(pickupAt, returnAt);
        if (days > MAX_DAYS) {
            throw new BusinessRuleException("INVALID_TIME", "Rental cannot exceed " + MAX_DAYS + " days");
        }
        if (!within(pickupLocal.toLocalTime(), pickupOpen, pickupClose)
                || !within(returnLocal.toLocalTime(), returnOpen, returnClose)) {
            throw new BusinessRuleException("OUTSIDE_BRANCH_HOURS", "Time is outside the branch opening hours");
        }
        return days;
    }

    private static void requireHalfHour(LocalDateTime t) {
        if ((t.getMinute() != 0 && t.getMinute() != 30) || t.getSecond() != 0 || t.getNano() != 0) {
            throw new BusinessRuleException("INVALID_TIME", "Minutes must be 00 or 30");
        }
    }

    private static boolean within(LocalTime t, LocalTime open, LocalTime close) {
        return !t.isBefore(open) && !t.isAfter(close);
    }

    public static Instant toInstant(LocalDateTime local) {
        return local.atZone(CarSettings.ZONE).toInstant();
    }
}
