package com.waylo.busservice.rules;

import com.waylo.busservice.dto.BusDtos.ScheduleStopItem;
import com.waylo.busservice.dto.BusDtos.StopItem;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripScheduleGeneratorTest {

    // Friday 2027-02-05 10:00 Istanbul
    private static final LocalDate TODAY = LocalDate.of(2027, 2, 5);
    private static final Instant NOW = LocalDateTime.of(2027, 2, 5, 10, 0).atZone(ZoneId.of("Europe/Istanbul")).toInstant();
    private static final int FRI_SUN = ScheduleRules.mask(List.of(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY));

    @Test
    void weekdayMaskUsesMondayAsBitZero() {
        assertThat(ScheduleRules.mask(List.of(DayOfWeek.MONDAY))).isEqualTo(1);
        assertThat(ScheduleRules.mask(List.of(DayOfWeek.SUNDAY))).isEqualTo(64);
        assertThat(ScheduleRules.days(FRI_SUN)).containsExactly(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void datesFollowWeekdayMaskAndHorizon() {
        // Horizon 10 from Fri 05.02 = 05.02..14.02 inclusive (contract 3.2: today + horizonDays - 1), Fri/Sun only.
        List<LocalDate> d = TripScheduleGenerator.dates(FRI_SUN, LocalDate.of(2027, 1, 1), null, 10, TODAY);
        assertThat(d).containsExactly(LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 7), LocalDate.of(2027, 2, 12),
                LocalDate.of(2027, 2, 14));
    }

    @Test
    void validRangeClipsBothEnds() {
        List<LocalDate> d = TripScheduleGenerator.dates(127, LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 10), 30, TODAY);
        assertThat(d).containsExactly(LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9), LocalDate.of(2027, 2, 10));
        assertThat(TripScheduleGenerator.dates(127, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), 30, TODAY)).isEmpty();
    }

    @Test
    void departureEarlierThanNowPlusSixtyMinutesIsSkipped() {
        // today 10:30 is only 30 min away, 11:00 is exactly now+60 and allowed
        var early = TripScheduleGenerator.plan(127, LocalTime.of(10, 30), TODAY, null, 1, TODAY, NOW, Set.of());
        assertThat(early.departures()).isEmpty();
        assertThat(early.skippedPast()).isEqualTo(1);
        var ok = TripScheduleGenerator.plan(127, LocalTime.of(11, 0), TODAY, null, 1, TODAY, NOW, Set.of());
        assertThat(ok.departures()).containsExactly(LocalDateTime.of(2027, 2, 5, 11, 0));
    }

    @Test
    void existingDeparturesIncludingCancelledAreSkippedAndSecondRunIsIdempotent() {
        LocalTime t = LocalTime.of(22, 30);
        Instant fri = TripRules.toInstant(LocalDateTime.of(2027, 2, 5, 22, 30)); // may be a CANCELLED trip
        var first = TripScheduleGenerator.plan(FRI_SUN, t, TODAY, null, 10, TODAY, NOW, Set.of(fri));
        assertThat(first.departures()).containsExactly(LocalDateTime.of(2027, 2, 7, 22, 30), LocalDateTime.of(2027, 2, 12, 22, 30),
                LocalDateTime.of(2027, 2, 14, 22, 30));
        assertThat(first.skippedExisting()).isEqualTo(1);
        Set<Instant> after = Set.of(fri, TripRules.toInstant(first.departures().get(0)), TripRules.toInstant(first.departures().get(1)),
                TripRules.toInstant(first.departures().get(2)));
        var second = TripScheduleGenerator.plan(FRI_SUN, t, TODAY, null, 10, TODAY, NOW, after);
        assertThat(second.departures()).isEmpty();
        assertThat(second.skippedExisting()).isEqualTo(4);
    }

    @Test
    void offsetsCrossingMidnightProduceNextDayTimes() {
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID c = UUID.randomUUID();
        List<StopItem> s = TripScheduleGenerator.stops(LocalDateTime.of(2027, 2, 5, 22, 30), List.of(
                new ScheduleStopItem(a, 0, 0), new ScheduleStopItem(b, 150, 165), new ScheduleStopItem(c, 450, 450)));
        assertThat(s.get(1).arrivalAt()).isEqualTo(LocalDateTime.of(2027, 2, 6, 1, 0));
        assertThat(s.get(1).departureAt()).isEqualTo(LocalDateTime.of(2027, 2, 6, 1, 15));
        assertThat(s.get(2).arrivalAt()).isEqualTo(LocalDateTime.of(2027, 2, 6, 6, 0));
    }

    @Test
    void zoneHasNoDstSoOffsetsAreWallClockMinutes() {
        // Turkey is on fixed UTC+3 since 2016: the same local time is the same UTC time all year.
        assertThat(TripRules.toInstant(LocalDateTime.of(2027, 3, 28, 3, 30))).isEqualTo(Instant.parse("2027-03-28T00:30:00Z"));
        assertThat(TripRules.toInstant(LocalDateTime.of(2027, 10, 31, 3, 30))).isEqualTo(Instant.parse("2027-10-31T00:30:00Z"));
    }
}
