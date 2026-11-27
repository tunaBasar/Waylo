package com.waylo.flightservice.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class FlightScheduleDtos {

    private FlightScheduleDtos() {}

    public record CabinDto(String seatType, BigDecimal price, Integer fromRow, Integer toRow, String letters) {}

    public record ScheduleRequest(
            String code,
            String airline,
            String departureAirportCode,
            String arrivalAirportCode,
            List<DayOfWeek> weekdays,
            LocalTime departureTime,
            Integer durationMinutes,
            LocalDate validFrom,
            LocalDate validTo,
            Integer horizonDays,
            List<CabinDto> cabins,
            Long expectedVersion
    ) {}

    public record NextFlight(String flightNumber, Instant departureTime) {}

    public record ScheduleResponse(
            UUID id,
            String code,
            String airline,
            String departureAirportCode,
            String arrivalAirportCode,
            List<DayOfWeek> weekdays,
            LocalTime departureTime,
            int durationMinutes,
            LocalDate validFrom,
            LocalDate validTo,
            int horizonDays,
            boolean active,
            Long version,
            List<CabinDto> cabins,
            List<NextFlight> nextFlights
    ) {}

    public record GenerationSummary(int created, int updated, int skippedExisting, int skippedManual, int skippedPast) {
        public static GenerationSummary empty() {
            return new GenerationSummary(0, 0, 0, 0, 0);
        }

        public GenerationSummary plus(GenerationSummary o) {
            return new GenerationSummary(created + o.created, updated + o.updated,
                    skippedExisting + o.skippedExisting, skippedManual + o.skippedManual,
                    skippedPast + o.skippedPast);
        }
    }

    public record ScheduleResult(ScheduleResponse schedule, GenerationSummary summary) {}
}
