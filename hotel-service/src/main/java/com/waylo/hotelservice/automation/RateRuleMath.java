package com.waylo.hotelservice.automation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Pure price derivation (contract 2.2). No I/O, no clock. */
public final class RateRuleMath {

    private RateRuleMath() {
    }

    /** Friday and Saturday nights use the weekend base. */
    public static boolean isWeekendNight(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.FRIDAY || d == DayOfWeek.SATURDAY;
    }

    /**
     * The rule that prices {@code date}: a covering season beats the default rule; among seasons the latest
     * valid_from wins, then the latest updated_at. Inactive rules are ignored.
     */
    public static Optional<HotelRateRule> pickRule(Collection<HotelRateRule> rules, LocalDate date) {
        Optional<HotelRateRule> season = rules.stream()
                .filter(HotelRateRule::isActive)
                .filter(HotelRateRule::isSeason)
                .filter(r -> !date.isBefore(r.getValidFrom()) && !date.isAfter(r.getValidTo()))
                .max(Comparator.comparing(HotelRateRule::getValidFrom)
                        .thenComparing(HotelRateRule::getUpdatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())));
        if (season.isPresent()) {
            return season;
        }
        return rules.stream()
                .filter(HotelRateRule::isActive)
                .filter(r -> !r.isSeason())
                .max(Comparator.comparing(HotelRateRule::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public static BigDecimal baseFor(HotelRateRule rule, LocalDate date) {
        if (rule.getWeekendBaseAmount() != null && isWeekendNight(date)) {
            return rule.getWeekendBaseAmount();
        }
        return rule.getBaseAmount();
    }

    /** Amount for one occupancy, scale 2 HALF_UP. May be zero or negative: callers skip those. */
    public static BigDecimal amountFor(HotelRateRule rule, LocalDate date, int occupancy) {
        BigDecimal base = baseFor(rule, date);
        int b = rule.getBaseOccupancy();
        BigDecimal result;
        if (occupancy > b) {
            result = base.add(rule.getExtraPersonAmount().multiply(BigDecimal.valueOf(occupancy - b)));
        } else if (occupancy < b) {
            result = base.subtract(rule.getReducedPersonAmount().multiply(BigDecimal.valueOf(b - occupancy)));
        } else {
            result = base;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    /** Occupancy to amount for 1..maxOccupancy, keeping only positive amounts. */
    public static Map<Integer, BigDecimal> derive(HotelRateRule rule, LocalDate date, int maxOccupancy) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        for (int o = 1; o <= maxOccupancy; o++) {
            BigDecimal amount = amountFor(rule, date, o);
            if (amount.signum() > 0) {
                out.put(o, amount);
            }
        }
        return out;
    }
}
