package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.FlightSchedule;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightScheduleDtos.GenerationSummary;
import com.waylo.flightservice.event.FlightCabinSpec;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.repository.FlightRepository;
import com.waylo.flightservice.repository.FlightScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Opens future departures for active schedules (contract 4.3). Idempotent: an existing flight number
 * (any status) is skipped. Not transactional: every flight is created through {@link FlightService}
 * in its own transaction, the same path as POST /api/flights.
 */
@Component
public class FlightScheduleGenerator {

    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    static final Duration MIN_LEAD = Duration.ofHours(3);

    private static final Logger log = LoggerFactory.getLogger(FlightScheduleGenerator.class);

    private final FlightScheduleRepository scheduleRepository;
    private final FlightRepository flightRepository;
    private final FlightService flightService;
    private final Clock clock;

    public FlightScheduleGenerator(FlightScheduleRepository scheduleRepository,
                                   FlightRepository flightRepository,
                                   FlightService flightService,
                                   Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.flightRepository = flightRepository;
        this.flightService = flightService;
        this.clock = clock;
    }

    /** code + yy + day of year (3 digits), e.g. WD1 on 2026-11-27 gives WD126331. */
    public static String flightNumber(String code, LocalDate date) {
        return code + String.format("%02d%03d", date.getYear() % 100, date.getDayOfYear());
    }

    public GenerationSummary applyAll() {
        GenerationSummary total = GenerationSummary.empty();
        for (FlightSchedule schedule : scheduleRepository.findByActiveTrueOrderByCodeAsc()) {
            try {
                total = total.plus(apply(schedule));
            } catch (RuntimeException ex) {
                log.error("Flight schedule generation failed: scheduleId={}, code={}", schedule.getId(), schedule.getCode(), ex);
            }
        }
        return total;
    }

    public GenerationSummary apply(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).map(this::apply).orElse(GenerationSummary.empty());
    }

    public GenerationSummary apply(FlightSchedule schedule) {
        if (!schedule.isActive()) {
            return GenerationSummary.empty();
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate from = max(today, schedule.getValidFrom());
        LocalDate to = today.plusDays(schedule.getHorizonDays() - 1L);
        if (schedule.getValidTo() != null && schedule.getValidTo().isBefore(to)) {
            to = schedule.getValidTo();
        }
        List<FlightCabinSpec> cabins = cabinSpecs(schedule);

        int created = 0;
        int skippedExisting = 0;
        int skippedPast = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!schedule.runsOn(date.getDayOfWeek())) {
                continue;
            }
            Instant departure = date.atTime(schedule.getDepartureTime()).atZone(ZONE).toInstant();
            if (departure.isBefore(now.plus(MIN_LEAD))) {
                skippedPast++;
                continue;
            }
            String number = flightNumber(schedule.getCode(), date);
            if (flightRepository.existsByFlightNumber(number)) {
                skippedExisting++;
                continue;
            }
            Instant arrival = departure.plus(Duration.ofMinutes(schedule.getDurationMinutes()));
            FlightRequest request = new FlightRequest(number,
                    schedule.getDepartureAirport().getIataCode(), schedule.getArrivalAirport().getIataCode(),
                    schedule.getAirline(), departure, arrival);
            try {
                flightService.createScheduled(request, schedule.getId(), cabins);
                created++;
            } catch (DuplicateResourceException ex) {
                skippedExisting++;
            } catch (RuntimeException ex) {
                log.error("Scheduled flight creation failed: scheduleId={}, flightNumber={}", schedule.getId(), number, ex);
            }
        }
        GenerationSummary summary = new GenerationSummary(created, 0, skippedExisting, 0, skippedPast);
        log.info("Flight schedule applied: scheduleId={}, code={}, summary={}", schedule.getId(), schedule.getCode(), summary);
        return summary;
    }

    static List<FlightCabinSpec> cabinSpecs(FlightSchedule schedule) {
        List<FlightCabinSpec> specs = new ArrayList<>();
        schedule.getCabins().stream()
                .sorted(Comparator.comparingInt(c -> c.getFromRow()))
                .forEach(c -> specs.add(new FlightCabinSpec(c.getSeatType(), c.getPrice(),
                        c.getFromRow(), c.getToRow(), c.getLetters())));
        return specs;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
