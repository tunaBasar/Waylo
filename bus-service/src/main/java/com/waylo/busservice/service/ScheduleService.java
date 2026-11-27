package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ConflictException;
import com.waylo.busservice.exception.ResourceNotFoundException;
import com.waylo.busservice.repository.*;
import com.waylo.busservice.rules.ScheduleRules;
import com.waylo.busservice.rules.SeatRules;
import com.waylo.busservice.rules.TripScheduleGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Weekly timetables: transactional writes and one transaction per schedule for generation. Generation never runs
 * inside the transaction that saves a schedule: {@link ScheduleRunner} saves first, then applies.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final AuthGateway auth;
    private final Clock clock;
    private final BusService busService;
    private final BusRepository buses;
    private final TerminalRepository terminals;
    private final TripRepository trips;
    private final TripScheduleRepository schedules;
    private final TripScheduleStopRepository scheduleStops;
    private final TripScheduleFareRepository scheduleFares;

    public ScheduleService(AuthGateway auth, Clock clock, BusService busService, BusRepository buses,
                           TerminalRepository terminals, TripRepository trips, TripScheduleRepository schedules,
                           TripScheduleStopRepository scheduleStops, TripScheduleFareRepository scheduleFares) {
        this.auth = auth;
        this.clock = clock;
        this.busService = busService;
        this.buses = buses;
        this.terminals = terminals;
        this.trips = trips;
        this.schedules = schedules;
        this.scheduleStops = scheduleStops;
        this.scheduleFares = scheduleFares;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID userId, UUID orgId) {
        auth.requireBusManager(userId, orgId);
        return schedules.findByOrganizationIdOrderByCode(orgId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID userId, UUID orgId, UUID id) {
        auth.requireBusManager(userId, orgId);
        return response(own(orgId, id));
    }

    @Transactional
    public UUID create(UUID userId, UUID orgId, ScheduleRequest req) {
        auth.requireBusManager(userId, orgId);
        ScheduleRules.validate(req);
        String code = req.code().trim();
        if (schedules.findByOrganizationIdAndCode(orgId, code).isPresent()) codeTaken(code);
        checkBusAndTerminals(orgId, req);
        TripSchedule s = new TripSchedule();
        s.id = UUID.randomUUID();
        s.organizationId = orgId;
        s.active = true;
        fill(s, req, code);
        schedules.saveAndFlush(s);
        saveChildren(s.id, req);
        return s.id;
    }

    /** Replaces the timetable. Only future generation changes: existing trips are never touched. */
    @Transactional
    public UUID update(UUID userId, UUID orgId, UUID id, ScheduleRequest req) {
        auth.requireBusManager(userId, orgId);
        TripSchedule s = own(orgId, id);
        if (req.expectedVersion() != null && !req.expectedVersion().equals(s.version)) {
            throw new ConflictException("CONCURRENT_MODIFICATION", "The schedule was modified concurrently, please reload");
        }
        ScheduleRules.validate(req);
        String code = req.code().trim();
        schedules.findByOrganizationIdAndCode(orgId, code).filter(o -> !o.id.equals(id)).ifPresent(o -> codeTaken(code));
        checkBusAndTerminals(orgId, req);
        fill(s, req, code);
        schedules.saveAndFlush(s);
        scheduleStops.deleteAllByScheduleId(id);
        scheduleFares.deleteAllByScheduleId(id);
        saveChildren(id, req);
        return id;
    }

    @Transactional
    public UUID setActive(UUID userId, UUID orgId, UUID id, boolean active) {
        auth.requireBusManager(userId, orgId);
        TripSchedule s = own(orgId, id);
        s.active = active;
        schedules.save(s);
        return id;
    }

    @Transactional(readOnly = true)
    public List<UUID> activeScheduleIds(UUID orgId) {
        return (orgId == null ? schedules.findByActiveTrue() : schedules.findByOrganizationIdAndActiveTrue(orgId))
                .stream().map(s -> s.id).toList();
    }

    /** Creates the missing departures of one schedule in one transaction. No access check: callers did it. */
    @Transactional
    public ScheduleSummary applyOne(UUID scheduleId) {
        TripSchedule s = schedules.findById(scheduleId).orElse(null);
        if (s == null || !s.active) return ScheduleSummary.ZERO;
        Bus bus = buses.findById(s.busId).orElse(null);
        if (bus == null || !bus.active) {
            log.warn("Schedule {} skipped: bus {} is inactive or missing", s.id, s.busId);
            return ScheduleSummary.ZERO;
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(BusSettings.ZONE));
        Instant from = TripRules0.start(today);
        Instant to = TripRules0.start(today.plusDays(s.horizonDays + 1L));
        Set<Instant> existing = new HashSet<>(trips.findDepartureTimes(s.organizationId, s.code, from, to));
        TripScheduleGenerator.Plan plan = TripScheduleGenerator.plan(s.weekdays, s.departureTime, s.validFrom,
                s.validTo, s.horizonDays, today, now, existing);
        if (plan.departures().isEmpty()) return new ScheduleSummary(0, 0, plan.skippedExisting(), 0, plan.skippedPast());
        List<ScheduleStopItem> offsets = scheduleStops.findByScheduleIdOrderBySeq(s.id).stream()
                .map(o -> new ScheduleStopItem(o.terminalId, o.arrivalOffsetMinutes, o.departureOffsetMinutes)).toList();
        List<FareItem> fareItems = scheduleFares.findByScheduleId(s.id).stream()
                .map(f -> new FareItem(f.fromSeq, f.toSeq, f.price)).toList();
        for (LocalDateTime dep : plan.departures()) {
            busService.createTripFrom(s.organizationId, bus, s.code, s.currency,
                    TripScheduleGenerator.stops(dep, offsets), fareItems, s.id);
        }
        return new ScheduleSummary(plan.departures().size(), 0, plan.skippedExisting(), 0, plan.skippedPast());
    }

    private static void codeTaken(String code) {
        throw new BusinessRuleException("SCHEDULE_CODE_TAKEN", "Schedule code already in use: " + code);
    }

    private void checkBusAndTerminals(UUID orgId, ScheduleRequest req) {
        Bus bus = buses.findById(req.busId()).filter(b -> b.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + req.busId()));
        if (!bus.active) throw new BusinessRuleException("BUS_INACTIVE", "Bus is not active");
        Set<UUID> ids = req.stops().stream().map(ScheduleStopItem::terminalId).collect(Collectors.toSet());
        Map<UUID, Terminal> found = terminals.findAllById(ids).stream().collect(Collectors.toMap(t -> t.id, t -> t));
        for (UUID tid : ids) {
            Terminal t = found.get(tid);
            if (t == null || !t.active) throw new BusinessRuleException("INVALID_TERMINAL", "Unknown or inactive terminal: " + tid);
        }
    }

    private static void fill(TripSchedule s, ScheduleRequest req, String code) {
        s.busId = req.busId();
        s.code = code;
        s.currency = req.currency();
        s.weekdays = (short) ScheduleRules.mask(req.weekdays());
        s.departureTime = req.departureTime().withSecond(0).withNano(0);
        s.validFrom = req.validFrom();
        s.validTo = req.validTo();
        s.horizonDays = req.horizonDays();
    }

    private void saveChildren(UUID scheduleId, ScheduleRequest req) {
        int seq = 0;
        for (ScheduleStopItem i : req.stops()) {
            TripScheduleStop st = new TripScheduleStop();
            st.id = UUID.randomUUID();
            st.scheduleId = scheduleId;
            st.seq = seq++;
            st.terminalId = i.terminalId();
            st.arrivalOffsetMinutes = i.arrivalOffsetMinutes();
            st.departureOffsetMinutes = i.departureOffsetMinutes();
            scheduleStops.save(st);
        }
        for (FareItem f : req.fares()) {
            TripScheduleFare tf = new TripScheduleFare();
            tf.id = UUID.randomUUID();
            tf.scheduleId = scheduleId;
            tf.fromSeq = f.fromSeq();
            tf.toSeq = f.toSeq();
            tf.price = SeatRules.money(f.price());
            scheduleFares.save(tf);
        }
    }

    private TripSchedule own(UUID orgId, UUID id) {
        return schedules.findById(id).filter(s -> s.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
    }

    private ScheduleResponse response(TripSchedule s) {
        Map<UUID, Terminal> tm = terminals.findAll().stream().collect(Collectors.toMap(t -> t.id, t -> t));
        List<ScheduleStopResponse> ss = scheduleStops.findByScheduleIdOrderBySeq(s.id).stream().map(o -> {
            Terminal t = tm.get(o.terminalId);
            return new ScheduleStopResponse(o.seq, o.terminalId, t == null ? null : t.city, t == null ? null : t.name,
                    o.arrivalOffsetMinutes, o.departureOffsetMinutes);
        }).toList();
        List<FareResponse> fs = scheduleFares.findByScheduleId(s.id).stream()
                .sorted(Comparator.comparingInt((TripScheduleFare f) -> f.fromSeq).thenComparingInt(f -> f.toSeq))
                .map(f -> new FareResponse(f.fromSeq, f.toSeq, f.price)).toList();
        List<LocalDateTime> next = trips.findNextDepartures(s.id, clock.instant(), PageRequest.of(0, 5)).stream()
                .map(com.waylo.busservice.dto.BusDtos::local).toList();
        return new ScheduleResponse(s.id, s.organizationId, s.busId, s.code, s.currency,
                ScheduleRules.days(s.weekdays), s.departureTime.format(HHMM), s.validFrom, s.validTo, s.horizonDays,
                s.active, s.version, ss, fs, next, trips.countByScheduleId(s.id));
    }

    /** Start of a local day as an instant (kept private so the rules class stays focused). */
    private static final class TripRules0 {
        static Instant start(LocalDate d) { return d.atStartOfDay(BusSettings.ZONE).toInstant(); }
    }
}
