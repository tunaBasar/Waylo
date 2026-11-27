package com.waylo.hotelservice.automation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateRuleMathTest {

    // 2027-06-04 is a Friday, 06-05 Saturday, 06-06 Sunday, 06-07 Monday
    private static final LocalDate FRI = LocalDate.of(2027, 6, 4);
    private static final LocalDate SAT = LocalDate.of(2027, 6, 5);
    private static final LocalDate SUN = LocalDate.of(2027, 6, 6);
    private static final LocalDate MON = LocalDate.of(2027, 6, 7);

    static HotelRateRule rule(String name, LocalDate from, LocalDate to, String base, Instant updated) {
        HotelRateRule r = new HotelRateRule();
        r.setName(name);
        r.setValidFrom(from);
        r.setValidTo(to);
        r.setCurrency("TRY");
        r.setBaseOccupancy(2);
        r.setBaseAmount(new BigDecimal(base));
        r.setUpdatedAt(updated);
        return r;
    }

    @Test
    void seasonBeatsDefault() {
        HotelRateRule def = rule("default", null, null, "1000", Instant.parse("2027-01-01T00:00:00Z"));
        HotelRateRule season = rule("summer", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 8, 31), "2000", Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(RateRuleMath.pickRule(List.of(def, season), MON)).containsSame(season);
        assertThat(RateRuleMath.pickRule(List.of(def, season), LocalDate.of(2027, 9, 1))).containsSame(def);
    }

    @Test
    void latestValidFromWinsThenLatestUpdatedAt() {
        HotelRateRule early = rule("a", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30), "1", Instant.parse("2027-03-01T00:00:00Z"));
        HotelRateRule late = rule("b", LocalDate.of(2027, 6, 5), LocalDate.of(2027, 6, 30), "2", Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(RateRuleMath.pickRule(List.of(early, late), MON)).containsSame(late);

        HotelRateRule sameFromOlder = rule("c", LocalDate.of(2027, 6, 5), LocalDate.of(2027, 6, 30), "3", Instant.parse("2027-01-01T00:00:00Z"));
        HotelRateRule sameFromNewer = rule("d", LocalDate.of(2027, 6, 5), LocalDate.of(2027, 6, 30), "4", Instant.parse("2027-02-01T00:00:00Z"));
        assertThat(RateRuleMath.pickRule(List.of(sameFromOlder, sameFromNewer), MON)).containsSame(sameFromNewer);
    }

    @Test
    void noCoveringRuleAndInactiveRulesYieldNothing() {
        HotelRateRule season = rule("s", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30), "1", Instant.EPOCH);
        assertThat(RateRuleMath.pickRule(List.of(season), LocalDate.of(2027, 7, 1))).isEmpty();
        season.setActive(false);
        assertThat(RateRuleMath.pickRule(List.of(season), MON)).isEmpty();
    }

    @Test
    void weekendBaseAppliesOnFridayAndSaturdayOnly() {
        HotelRateRule r = rule("r", null, null, "1000.00", Instant.EPOCH);
        r.setWeekendBaseAmount(new BigDecimal("1500.00"));

        assertThat(RateRuleMath.amountFor(r, FRI, 2)).isEqualByComparingTo("1500.00");
        assertThat(RateRuleMath.amountFor(r, SAT, 2)).isEqualByComparingTo("1500.00");
        assertThat(RateRuleMath.amountFor(r, SUN, 2)).isEqualByComparingTo("1000.00");
        assertThat(RateRuleMath.amountFor(r, MON, 2)).isEqualByComparingTo("1000.00");
    }

    @Test
    void weekendFallsBackToBaseWhenUnset() {
        HotelRateRule r = rule("r", null, null, "1000.00", Instant.EPOCH);
        assertThat(RateRuleMath.amountFor(r, FRI, 2)).isEqualByComparingTo("1000.00");
    }

    @Test
    void extraAndReducedPerPerson() {
        HotelRateRule r = rule("r", null, null, "4000.00", Instant.EPOCH);
        r.setExtraPersonAmount(new BigDecimal("800.00"));
        r.setReducedPersonAmount(new BigDecimal("500.00"));

        Map<Integer, BigDecimal> prices = RateRuleMath.derive(r, MON, 4);

        assertThat(prices).containsEntry(1, new BigDecimal("3500.00"))
                .containsEntry(2, new BigDecimal("4000.00"))
                .containsEntry(3, new BigDecimal("4800.00"))
                .containsEntry(4, new BigDecimal("5600.00"));
    }

    @Test
    void nonPositiveAmountsAreSkipped() {
        HotelRateRule r = rule("r", null, null, "100.00", Instant.EPOCH);
        r.setBaseOccupancy(3);
        r.setReducedPersonAmount(new BigDecimal("50.00")); // occ 2 = 50, occ 1 = 0

        Map<Integer, BigDecimal> prices = RateRuleMath.derive(r, MON, 3);

        assertThat(prices.keySet()).containsExactly(2, 3);
    }

    @Test
    void roundsHalfUpToScaleTwo() {
        HotelRateRule r = rule("r", null, null, "100.005", Instant.EPOCH);
        assertThat(RateRuleMath.amountFor(r, MON, 2)).isEqualTo(new BigDecimal("100.01"));
        r.setBaseAmount(new BigDecimal("100.004"));
        assertThat(RateRuleMath.amountFor(r, MON, 2)).isEqualTo(new BigDecimal("100.00"));
    }
}
