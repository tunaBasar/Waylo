package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.domain.FlightSchedule;
import com.waylo.flightservice.domain.FlightScheduleCabin;
import com.waylo.flightservice.domain.FlightStatus;
import com.waylo.flightservice.dto.FlightScheduleDtos.CabinDto;
import com.waylo.flightservice.dto.FlightScheduleDtos.GenerationSummary;
import com.waylo.flightservice.dto.FlightScheduleDtos.NextFlight;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleRequest;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleResponse;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleResult;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import com.waylo.flightservice.repository.FlightScheduleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Not transactional on purpose (contract 1): each save is its own repository transaction and generation
 * runs afterwards, never inside the transaction that saves the schedule.
 */
@Service
public class FlightScheduleService {

    private static final Pattern CODE = Pattern.compile("^[A-Z]{2}[0-9]{1,3}$");
    private static final Set<String> SEAT_TYPES = Set.of("ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST");
    static final int MAX_CABIN_SEATS = 600;

    private final FlightScheduleRepository scheduleRepository;
    private final AirportRepository airportRepository;
    private final FlightRepository flightRepository;
    private final FlightScheduleGenerator generator;
    private final Clock clock;

    public FlightScheduleService(FlightScheduleRepository scheduleRepository,
                                 AirportRepository airportRepository,
                                 FlightRepository flightRepository,
                                 FlightScheduleGenerator generator,
                                 Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.airportRepository = airportRepository;
        this.flightRepository = flightRepository;
        this.generator = generator;
        this.clock = clock;
    }

    public List<ScheduleResponse> list() {
        return scheduleRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    public ScheduleResult create(ScheduleRequest request) {
        String code = normalizeCode(request.code());
        if (scheduleRepository.existsByCode(code)) {
            throw new BusinessRuleException("SCHEDULE_CODE_TAKEN", "Schedule code " + code + " is already used");
        }
        FlightSchedule schedule = new FlightSchedule();
        schedule.setCode(code);
        schedule.setActive(true);
        fill(schedule, request);
        FlightSchedule saved = scheduleRepository.save(schedule);
        return new ScheduleResult(toResponse(saved), generator.apply(saved));
    }

    public ScheduleResult update(UUID id, ScheduleRequest request) {
        FlightSchedule schedule = find(id);
        if (request.expectedVersion() != null && !request.expectedVersion().equals(schedule.getVersion())) {
            throw new BusinessRuleException("CONCURRENT_MODIFICATION",
                    "Schedule was modified by someone else. Reload and retry.");
        }
        String code = normalizeCode(request.code());
        if (!code.equals(schedule.getCode()) && scheduleRepository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessRuleException("SCHEDULE_CODE_TAKEN", "Schedule code " + code + " is already used");
        }
        schedule.setCode(code);
        fill(schedule, request);
        FlightSchedule saved = scheduleRepository.save(schedule);
        GenerationSummary summary = saved.isActive() ? generator.apply(saved) : GenerationSummary.empty();
        return new ScheduleResult(toResponse(saved), summary);
    }

    public ScheduleResult activate(UUID id) {
        FlightSchedule schedule = find(id);
        schedule.setActive(true);
        FlightSchedule saved = scheduleRepository.save(schedule);
        return new ScheduleResult(toResponse(saved), generator.apply(saved));
    }

    public ScheduleResult deactivate(UUID id) {
        FlightSchedule schedule = find(id);
        schedule.setActive(false);
        FlightSchedule saved = scheduleRepository.save(schedule);
        return new ScheduleResult(toResponse(saved), null);
    }

    public GenerationSummary applyAll() {
        return generator.applyAll();
    }

    // ---------- validation and mapping ----------

    private FlightSchedule find(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightSchedule", "id", id));
    }

    private static String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw invalid("Code must be 2 letters followed by 1 to 3 digits");
        }
        return code;
    }

    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("INVALID_SCHEDULE", message);
    }

    private static BusinessRuleException invalidCabins(String message) {
        return new BusinessRuleException("INVALID_CABINS", message);
    }

    private void fill(FlightSchedule schedule, ScheduleRequest r) {
        if (r.airline() == null || r.airline().isBlank() || r.airline().trim().length() > 100) {
            throw invalid("Airline is required (max 100 characters)");
        }
        if (r.weekdays() == null || r.weekdays().isEmpty()) {
            throw invalid("At least one weekday is required");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : r.weekdays()) {
            if (d == null || !days.add(d)) {
                throw invalid("Weekdays must be distinct");
            }
        }
        if (r.departureTime() == null) {
            throw invalid("Departure time is required");
        }
        if (r.durationMinutes() == null || r.durationMinutes() < 20 || r.durationMinutes() > 1200) {
            throw invalid("Duration must be between 20 and 1200 minutes");
        }
        if (r.validFrom() == null) {
            throw invalid("validFrom is required");
        }
        if (r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw invalid("validTo must not be before validFrom");
        }
        int horizon = r.horizonDays() == null ? 30 : r.horizonDays();
        if (horizon < 1 || horizon > 120) {
            throw invalid("horizonDays must be between 1 and 120");
        }
        String from = r.departureAirportCode() == null ? "" : r.departureAirportCode().trim().toUpperCase(Locale.ROOT);
        String to = r.arrivalAirportCode() == null ? "" : r.arrivalAirportCode().trim().toUpperCase(Locale.ROOT);
        if (from.equals(to) && !from.isEmpty()) {
            throw new BusinessRuleException("SAME_AIRPORT", "Departure and arrival airports must differ");
        }
        List<FlightScheduleCabin> cabins = buildCabins(r.cabins());
        Airport dep = airport(from);
        Airport arr = airport(to);

        schedule.setAirline(r.airline().trim());
        schedule.setDepartureAirport(dep);
        schedule.setArrivalAirport(arr);
        schedule.setWeekdays(FlightSchedule.toMask(days));
        schedule.setDepartureTime(r.departureTime());
        schedule.setDurationMinutes(r.durationMinutes());
        schedule.setValidFrom(r.validFrom());
        schedule.setValidTo(r.validTo());
        schedule.setHorizonDays(horizon);
        schedule.getCabins().clear();
        schedule.getCabins().addAll(cabins);
    }

    private Airport airport(String code) {
        return airportRepository.findByIataCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "iataCode", code));
    }

    private static List<FlightScheduleCabin> buildCabins(List<CabinDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw invalidCabins("At least one cabin is required");
        }
        List<FlightScheduleCabin> out = new ArrayList<>();
        Set<String> types = new HashSet<>();
        for (CabinDto d : dtos) {
            String type = d.seatType() == null ? "" : d.seatType().trim().toUpperCase(Locale.ROOT);
            if (!SEAT_TYPES.contains(type)) {
                throw invalidCabins("Seat type must be one of ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST");
            }
            if (!types.add(type)) {
                throw invalidCabins("Seat type " + type + " is used more than once");
            }
            if (d.price() == null || d.price().compareTo(BigDecimal.ZERO) <= 0
                    || d.price().scale() > 2 || d.price().precision() - d.price().scale() > 8) {
                throw invalidCabins("Price must be positive with at most 2 decimals");
            }
            if (d.fromRow() == null || d.toRow() == null || d.fromRow() < 1 || d.toRow() > 999
                    || d.fromRow() > d.toRow()) {
                throw invalidCabins("Rows must satisfy 1 <= fromRow <= toRow <= 999");
            }
            String letters = d.letters() == null ? "" : d.letters().trim().toUpperCase(Locale.ROOT);
            Set<Character> distinct = new HashSet<>();
            for (char c : letters.toCharArray()) {
                if (c < 'A' || c > 'K' || !distinct.add(c)) {
                    throw invalidCabins("Letters must be distinct characters between A and K");
                }
            }
            if (letters.isEmpty()) {
                throw invalidCabins("Letters are required");
            }
            if ((long) (d.toRow() - d.fromRow() + 1) * letters.length() > MAX_CABIN_SEATS) {
                throw invalidCabins("A cabin cannot have more than " + MAX_CABIN_SEATS + " seats");
            }
            FlightScheduleCabin c = new FlightScheduleCabin();
            c.setSeatType(type);
            c.setPrice(d.price());
            c.setFromRow(d.fromRow());
            c.setToRow(d.toRow());
            c.setLetters(letters);
            out.add(c);
        }
        List<FlightScheduleCabin> sorted = out.stream()
                .sorted(Comparator.comparingInt(FlightScheduleCabin::getFromRow)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getFromRow() <= sorted.get(i - 1).getToRow()) {
                throw invalidCabins("Cabin row ranges must not overlap");
            }
        }
        return out;
    }

    private ScheduleResponse toResponse(FlightSchedule s) {
        List<NextFlight> next = flightRepository
                .findTop5ByScheduleIdAndStatusNotAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAsc(
                        s.getId(), FlightStatus.CANCELLED, clock.instant())
                .stream().map(f -> new NextFlight(f.getFlightNumber(), f.getDepartureTime())).toList();
        List<CabinDto> cabins = s.getCabins().stream()
                .sorted(Comparator.comparingInt(FlightScheduleCabin::getFromRow))
                .map(c -> new CabinDto(c.getSeatType(), c.getPrice(), c.getFromRow(), c.getToRow(), c.getLetters()))
                .toList();
        return new ScheduleResponse(s.getId(), s.getCode(), s.getAirline(),
                s.getDepartureAirport().getIataCode(), s.getArrivalAirport().getIataCode(),
                s.getWeekdaySet().stream().sorted().toList(), s.getDepartureTime(), s.getDurationMinutes(),
                s.getValidFrom(), s.getValidTo(), s.getHorizonDays(), s.isActive(), s.getVersion(), cabins, next);
    }
}
