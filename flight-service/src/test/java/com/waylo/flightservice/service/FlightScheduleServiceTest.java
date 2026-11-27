package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.dto.FlightScheduleDtos.CabinDto;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleRequest;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import com.waylo.flightservice.repository.FlightScheduleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightScheduleServiceTest {

    private final FlightScheduleRepository scheduleRepo = mock(FlightScheduleRepository.class);
    private final AirportRepository airportRepo = mock(AirportRepository.class);
    private final FlightRepository flightRepo = mock(FlightRepository.class);
    private final FlightScheduleGenerator generator = mock(FlightScheduleGenerator.class);
    private final FlightScheduleService service = new FlightScheduleService(scheduleRepo, airportRepo, flightRepo,
            generator, Clock.fixed(Instant.parse("2026-11-27T07:00:00Z"), ZoneOffset.UTC));

    private static CabinDto business() {
        return new CabinDto("BUSINESS", new BigDecimal("5000.00"), 1, 2, "AC");
    }

    private static CabinDto economy() {
        return new CabinDto("ECONOMY", new BigDecimal("1500.00"), 3, 7, "ABCD");
    }

    private static ScheduleRequest request(String code, String from, String to, List<CabinDto> cabins) {
        return new ScheduleRequest(code, "Waylo Air", from, to, List.of(DayOfWeek.FRIDAY), LocalTime.of(18, 0),
                120, 30 > 0 ? LocalDate.of(2026, 11, 27) : null, null, 30, cabins, null);
    }

    private void airportsExist() {
        when(airportRepo.findByIataCode("IST")).thenReturn(Optional.of(new Airport()));
        when(airportRepo.findByIataCode("ADB")).thenReturn(Optional.of(new Airport()));
    }

    private static String ruleCode(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException e) {
            return e.getRuleCode();
        }
        return null;
    }

    @Test
    void codeTaken() {
        when(scheduleRepo.existsByCode("WD1")).thenReturn(true);
        assertThat(ruleCode(() -> service.create(request("wd1", "IST", "ADB", List.of(economy())))))
                .isEqualTo("SCHEDULE_CODE_TAKEN");
        verify(scheduleRepo, never()).save(any());
    }

    @Test
    void invalidCodeAndOtherScheduleFieldsAreInvalidSchedule() {
        assertThat(ruleCode(() -> service.create(request("W1", "IST", "ADB", List.of(economy())))))
                .isEqualTo("INVALID_SCHEDULE");
        assertThat(ruleCode(() -> service.create(request("WD1234", "IST", "ADB", List.of(economy())))))
                .isEqualTo("INVALID_SCHEDULE");
        ScheduleRequest noDays = new ScheduleRequest("WD1", "Waylo Air", "IST", "ADB", List.of(),
                LocalTime.of(18, 0), 120, LocalDate.of(2026, 11, 27), null, 30, List.of(economy()), null);
        assertThat(ruleCode(() -> service.create(noDays))).isEqualTo("INVALID_SCHEDULE");
        ScheduleRequest dupDays = new ScheduleRequest("WD1", "Waylo Air", "IST", "ADB",
                List.of(DayOfWeek.FRIDAY, DayOfWeek.FRIDAY), LocalTime.of(18, 0), 120,
                LocalDate.of(2026, 11, 27), null, 30, List.of(economy()), null);
        assertThat(ruleCode(() -> service.create(dupDays))).isEqualTo("INVALID_SCHEDULE");
        ScheduleRequest shortFlight = new ScheduleRequest("WD1", "Waylo Air", "IST", "ADB",
                List.of(DayOfWeek.FRIDAY), LocalTime.of(18, 0), 10, LocalDate.of(2026, 11, 27), null, 30,
                List.of(economy()), null);
        assertThat(ruleCode(() -> service.create(shortFlight))).isEqualTo("INVALID_SCHEDULE");
        ScheduleRequest badHorizon = new ScheduleRequest("WD1", "Waylo Air", "IST", "ADB",
                List.of(DayOfWeek.FRIDAY), LocalTime.of(18, 0), 120, LocalDate.of(2026, 11, 27), null, 121,
                List.of(economy()), null);
        assertThat(ruleCode(() -> service.create(badHorizon))).isEqualTo("INVALID_SCHEDULE");
        ScheduleRequest badRange = new ScheduleRequest("WD1", "Waylo Air", "IST", "ADB",
                List.of(DayOfWeek.FRIDAY), LocalTime.of(18, 0), 120, LocalDate.of(2026, 11, 27),
                LocalDate.of(2026, 11, 1), 30, List.of(economy()), null);
        assertThat(ruleCode(() -> service.create(badRange))).isEqualTo("INVALID_SCHEDULE");
    }

    @Test
    void overlappingCabinRowsAreInvalidCabins() {
        CabinDto overlap = new CabinDto("ECONOMY", new BigDecimal("1500.00"), 2, 7, "ABCD");
        assertThat(ruleCode(() -> service.create(request("WD1", "IST", "ADB", List.of(business(), overlap)))))
                .isEqualTo("INVALID_CABINS");
    }

    @Test
    void duplicateSeatTypeBadLettersAndEmptyCabinsAreInvalidCabins() {
        CabinDto sameType = new CabinDto("BUSINESS", new BigDecimal("1.00"), 5, 6, "AB");
        assertThat(ruleCode(() -> service.create(request("WD1", "IST", "ADB", List.of(business(), sameType)))))
                .isEqualTo("INVALID_CABINS");
        CabinDto badLetters = new CabinDto("ECONOMY", new BigDecimal("1.00"), 5, 6, "AAB");
        assertThat(ruleCode(() -> service.create(request("WD1", "IST", "ADB", List.of(badLetters)))))
                .isEqualTo("INVALID_CABINS");
        CabinDto zeroPrice = new CabinDto("ECONOMY", BigDecimal.ZERO, 5, 6, "AB");
        assertThat(ruleCode(() -> service.create(request("WD1", "IST", "ADB", List.of(zeroPrice)))))
                .isEqualTo("INVALID_CABINS");
        assertThat(ruleCode(() -> service.create(request("WD1", "IST", "ADB", List.of()))))
                .isEqualTo("INVALID_CABINS");
    }

    @Test
    void sameAirport() {
        assertThat(ruleCode(() -> service.create(request("WD1", "ist", "IST", List.of(economy())))))
                .isEqualTo("SAME_AIRPORT");
    }

    @Test
    void unknownAirport() {
        when(airportRepo.findByIataCode("XXX")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request("WD1", "XXX", "ADB", List.of(business(), economy()))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(scheduleRepo, never()).save(any());
    }

    @Test
    void validRequestSavesThenGenerates() {
        airportsExist();
        when(scheduleRepo.save(any())).thenAnswer(i -> {
            var s = (com.waylo.flightservice.domain.FlightSchedule) i.getArgument(0);
            return s;
        });
        var result = service.create(request("wd1", "IST", "ADB", List.of(business(), economy())));
        assertThat(result.schedule().code()).isEqualTo("WD1");
        assertThat(result.schedule().cabins()).hasSize(2);
        verify(generator).apply(any(com.waylo.flightservice.domain.FlightSchedule.class));
    }

    @Test
    void createBuildsTheResponseAfterGenerating() {
        // Live run 27.11: nextFlights was empty because the response was read before apply created the flights.
        airportsExist();
        when(scheduleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(generator, flightRepo);
        service.create(request("WD1", "IST", "ADB", List.of(business(), economy())));
        order.verify(generator).apply(any(com.waylo.flightservice.domain.FlightSchedule.class));
        order.verify(flightRepo).findTop5ByScheduleIdAndStatusNotAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAsc(
                any(), any(), any());
    }
}
