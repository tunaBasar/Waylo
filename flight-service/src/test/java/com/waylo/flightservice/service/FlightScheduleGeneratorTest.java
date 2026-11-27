package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.domain.FlightSchedule;
import com.waylo.flightservice.domain.FlightScheduleCabin;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightScheduleDtos.GenerationSummary;
import com.waylo.flightservice.repository.FlightRepository;
import com.waylo.flightservice.repository.FlightScheduleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightScheduleGeneratorTest {

    private final FlightScheduleRepository scheduleRepo = mock(FlightScheduleRepository.class);
    private final FlightRepository flightRepo = mock(FlightRepository.class);
    private final FlightService flightService = mock(FlightService.class);

    // 2026-11-27 07:00 UTC = 10:00 Istanbul (UTC+3), a Friday
    private FlightScheduleGenerator generator(String nowUtc) {
        Clock clock = Clock.fixed(Instant.parse(nowUtc), ZoneOffset.UTC);
        return new FlightScheduleGenerator(scheduleRepo, flightRepo, flightService, clock);
    }

    private Airport airport(String code) {
        Airport a = new Airport();
        a.setIataCode(code);
        a.setTimezone("Europe/Istanbul");
        return a;
    }

    private FlightSchedule schedule(EnumSet<DayOfWeek> days, String time, int duration, int horizon,
                                    LocalDate validFrom, LocalDate validTo) {
        FlightSchedule s = new FlightSchedule();
        s.setId(UUID.randomUUID());
        s.setCode("WD1");
        s.setAirline("Waylo Air");
        s.setDepartureAirport(airport("IST"));
        s.setArrivalAirport(airport("ADB"));
        s.setWeekdays(FlightSchedule.toMask(days));
        s.setDepartureTime(LocalTime.parse(time));
        s.setDurationMinutes(duration);
        s.setHorizonDays(horizon);
        s.setValidFrom(validFrom);
        s.setValidTo(validTo);
        FlightScheduleCabin c = new FlightScheduleCabin();
        c.setSeatType("ECONOMY");
        c.setPrice(new BigDecimal("1000.00"));
        c.setFromRow(1);
        c.setToRow(2);
        c.setLetters("AB");
        s.getCabins().add(c);
        return s;
    }

    private List<FlightRequest> created(int expected, UUID scheduleId) {
        ArgumentCaptor<FlightRequest> captor = ArgumentCaptor.forClass(FlightRequest.class);
        verify(flightService, times(expected)).createScheduled(captor.capture(), eq(scheduleId), anyList());
        return captor.getAllValues();
    }

    @Test
    void flightNumberFormat() {
        assertThat(FlightScheduleGenerator.flightNumber("WD1", LocalDate.of(2026, 11, 27))).isEqualTo("WD126331");
        assertThat(FlightScheduleGenerator.flightNumber("WD1", LocalDate.of(2027, 1, 1))).isEqualTo("WD127001");
        assertThat(FlightScheduleGenerator.flightNumber("WD1", LocalDate.of(2026, 12, 31))).isEqualTo("WD126365");
        assertThat(FlightScheduleGenerator.flightNumber("WD123", LocalDate.of(2028, 12, 31))).isEqualTo("WD12328366");
    }

    @Test
    void weekdayMaskPicksOnlyMatchingDays() {
        // Fri 2026-11-27 .. 7 days: Fri 27, Sun 29, Fri 4 Dec
        FlightSchedule s = schedule(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), "18:00", 120, 8,
                LocalDate.of(2026, 1, 1), null);

        GenerationSummary summary = generator("2026-11-27T07:00:00Z").apply(s);

        assertThat(summary.created()).isEqualTo(3);
        assertThat(created(3, s.getId())).extracting(FlightRequest::flightNumber)
                .containsExactly("WD126331", "WD126333", "WD126338");
    }

    @Test
    void validFromAndValidToBoundTheRange() {
        FlightSchedule s = schedule(EnumSet.allOf(DayOfWeek.class), "18:00", 120, 30,
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 2));

        generator("2026-11-27T07:00:00Z").apply(s);

        assertThat(created(3, s.getId())).extracting(FlightRequest::flightNumber)
                .containsExactly("WD126334", "WD126335", "WD126336");
    }

    @Test
    void horizonLimitsDays() {
        FlightSchedule s = schedule(EnumSet.allOf(DayOfWeek.class), "18:00", 120, 3,
                LocalDate.of(2026, 1, 1), null);

        generator("2026-11-27T07:00:00Z").apply(s);

        assertThat(created(3, s.getId())).extracting(FlightRequest::flightNumber)
                .containsExactly("WD126331", "WD126332", "WD126333");
    }

    @Test
    void departureEarlierThanNowPlus3hIsSkipped() {
        // now = 10:00 Istanbul. 12:59 is inside the 3 h lead, 13:00 is exactly now + 3h and allowed.
        FlightSchedule early = schedule(EnumSet.of(DayOfWeek.FRIDAY), "12:59", 120, 1,
                LocalDate.of(2026, 1, 1), null);
        GenerationSummary skipped = generator("2026-11-27T07:00:00Z").apply(early);
        assertThat(skipped.created()).isZero();
        assertThat(skipped.skippedPast()).isEqualTo(1);
        verify(flightService, never()).createScheduled(any(), any(), any());

        FlightSchedule ok = schedule(EnumSet.of(DayOfWeek.FRIDAY), "13:00", 120, 1,
                LocalDate.of(2026, 1, 1), null);
        assertThat(generator("2026-11-27T07:00:00Z").apply(ok).created()).isEqualTo(1);
    }

    @Test
    void existingFlightNumberIsSkippedAndSecondRunCreatesNothing() {
        FlightSchedule s = schedule(EnumSet.allOf(DayOfWeek.class), "18:00", 120, 2,
                LocalDate.of(2026, 1, 1), null);
        when(flightRepo.existsByFlightNumber("WD126331")).thenReturn(true);

        GenerationSummary summary = generator("2026-11-27T07:00:00Z").apply(s);

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.skippedExisting()).isEqualTo(1);
        assertThat(created(1, s.getId())).extracting(FlightRequest::flightNumber).containsExactly("WD126332");
    }

    @Test
    void arrivalIsDeparturePlusDurationAcrossMidnight() {
        FlightSchedule s = schedule(EnumSet.of(DayOfWeek.FRIDAY), "23:30", 90, 1,
                LocalDate.of(2026, 1, 1), null);

        generator("2026-11-27T07:00:00Z").apply(s);

        FlightRequest r = created(1, s.getId()).get(0);
        assertThat(r.departureTime()).isEqualTo(Instant.parse("2026-11-27T20:30:00Z"));
        assertThat(r.arrivalTime()).isEqualTo(Instant.parse("2026-11-27T22:00:00Z"));
        assertThat(r.departureAirportCode()).isEqualTo("IST");
        assertThat(r.arrivalAirportCode()).isEqualTo("ADB");
    }

    @Test
    void inactiveScheduleCreatesNothing() {
        FlightSchedule s = schedule(EnumSet.allOf(DayOfWeek.class), "18:00", 120, 5,
                LocalDate.of(2026, 1, 1), null);
        s.setActive(false);

        assertThat(generator("2026-11-27T07:00:00Z").apply(s).created()).isZero();
        verify(flightService, never()).createScheduled(any(), any(), any());
    }
}
