package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.DuplicateResourceException;
import com.waylo.busservice.exception.ResourceNotFoundException;
import com.waylo.busservice.repository.*;
import com.waylo.busservice.rules.SeatRules;
import com.waylo.busservice.rules.Sellability;
import com.waylo.busservice.rules.TripRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Partner side management: buses, trips, status, bookings, plus admin terminals. */
@Service
public class BusService {

    private final AuthGateway auth;
    private final Clock clock;
    private final TerminalRepository terminals;
    private final BusRepository buses;
    private final TripRepository trips;
    private final TripStopRepository stops;
    private final TripFareRepository fares;
    private final BusDocumentRepository documents;
    private final BookingRepository bookings;
    private final TicketRepository tickets;
    private final BusSettings settings;

    public BusService(AuthGateway auth, Clock clock, TerminalRepository terminals, BusRepository buses,
                      TripRepository trips, TripStopRepository stops, TripFareRepository fares,
                      BusDocumentRepository documents, BookingRepository bookings, TicketRepository tickets,
                      BusSettings settings) {
        this.auth = auth;
        this.clock = clock;
        this.terminals = terminals;
        this.buses = buses;
        this.trips = trips;
        this.stops = stops;
        this.fares = fares;
        this.documents = documents;
        this.bookings = bookings;
        this.tickets = tickets;
        this.settings = settings;
    }

    // ---- terminals (platform data, admin managed) ----

    @Transactional(readOnly = true)
    public List<TerminalResponse> listTerminals(boolean onlyActive) {
        return (onlyActive ? terminals.findByActiveTrueOrderByCityAscNameAsc() : terminals.findAllByOrderByCityAscNameAsc())
                .stream().map(TerminalResponse::of).toList();
    }

    @Transactional
    public TerminalResponse createTerminal(TerminalRequest req) {
        terminals.findByCityIgnoreCaseAndNameIgnoreCase(req.city().trim(), req.name().trim()).ifPresent(t -> {
            throw new DuplicateResourceException("Terminal", "name", req.name());
        });
        Terminal t = new Terminal();
        t.id = UUID.randomUUID();
        apply(t, req);
        return TerminalResponse.of(terminals.save(t));
    }

    @Transactional
    public TerminalResponse updateTerminal(UUID id, TerminalRequest req) {
        Terminal t = terminals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + id));
        apply(t, req);
        return TerminalResponse.of(terminals.save(t));
    }

    private static void apply(Terminal t, TerminalRequest req) {
        t.city = req.city().trim();
        t.name = req.name().trim();
        t.active = req.active() == null || req.active();
    }

    // ---- buses ----

    @Transactional(readOnly = true)
    public List<BusResponse> listBuses(UUID userId, UUID orgId) {
        OrgInfo org = auth.requireBusManager(userId, orgId);
        LocalDate today = today();
        List<Bus> list = buses.findByOrganizationIdOrderByPlateNumber(orgId);
        boolean orgOk = Sellability.orgSellable(org, documents.findByOrganizationIdAndBusIdIsNull(orgId), today);
        Map<UUID, List<BusDocument>> docs = docsByBus(list);
        return list.stream().map(b -> toResponse(b, orgOk, docs.getOrDefault(b.id, List.of()), today)).toList();
    }

    @Transactional
    public BusResponse createBus(UUID userId, UUID orgId, BusRequest req) {
        auth.requireBusManager(userId, orgId);
        String plate = normalizePlate(req.plateNumber());
        buses.findByPlateNumber(plate).ifPresent(b -> {
            throw new DuplicateResourceException("Bus", "plateNumber", plate);
        });
        Bus b = new Bus();
        b.id = UUID.randomUUID();
        b.organizationId = orgId;
        applyBus(b, req, plate);
        return toResponse(buses.save(b), false, List.of(), today());
    }

    @Transactional
    public BusResponse updateBus(UUID userId, UUID orgId, UUID id, BusRequest req) {
        auth.requireBusManager(userId, orgId);
        Bus b = ownBus(orgId, id);
        String plate = normalizePlate(req.plateNumber());
        buses.findByPlateNumber(plate).filter(o -> !o.id.equals(id)).ifPresent(o -> {
            throw new DuplicateResourceException("Bus", "plateNumber", plate);
        });
        if ((b.layout != req.layout() || b.rowCount != req.rowCount())
                && trips.existsByBusIdAndStatus(id, TripStatus.PUBLISHED)) {
            throw new BusinessRuleException("BUS_HAS_TRIPS", "Seat plan cannot change while the bus has published trips");
        }
        applyBus(b, req, plate);
        return toResponse(buses.save(b), false, List.of(), today());
    }

    @Transactional
    public BusResponse setBusActive(UUID userId, UUID orgId, UUID id, boolean active) {
        auth.requireBusManager(userId, orgId);
        Bus b = ownBus(orgId, id);
        b.active = active;
        return toResponse(buses.save(b), false, List.of(), today());
    }

    private void applyBus(Bus b, BusRequest req, String plate) {
        b.plateNumber = plate;
        b.brand = req.brand().trim();
        b.model = req.model().trim();
        b.year = req.year();
        b.layout = req.layout();
        b.rowCount = req.rowCount();
        b.seatCount = req.layout().seatCount(req.rowCount());
        b.active = req.active() == null || req.active();
    }

    static String normalizePlate(String raw) { return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }

    private Bus ownBus(UUID orgId, UUID id) {
        return buses.findById(id).filter(b -> b.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + id));
    }

    private Map<UUID, List<BusDocument>> docsByBus(List<Bus> list) {
        if (list.isEmpty()) return Map.of();
        return documents.findByBusIdIn(list.stream().map(b -> b.id).toList()).stream()
                .collect(Collectors.groupingBy(d -> d.busId));
    }

    private BusResponse toResponse(Bus b, boolean orgOk, List<BusDocument> docs, LocalDate on) {
        List<DocumentType> missing = Sellability.missingBusDocuments(docs, on);
        return BusResponse.of(b, orgOk && b.active && missing.isEmpty(), missing);
    }

    // ---- trips ----

    @Transactional
    public TripResponse createTrip(UUID userId, UUID orgId, TripRequest req) {
        auth.requireBusManager(userId, orgId);
        Bus bus = ownBus(orgId, req.busId());
        if (!bus.active) throw new BusinessRuleException("BUS_INACTIVE", "Bus is not active");
        TripRules.validateStops(clock.instant(), req.stops());
        TripRules.validateFares(req.stops().size(), req.fares());
        Set<UUID> terminalIds = req.stops().stream().map(StopItem::terminalId).collect(Collectors.toSet());
        Map<UUID, Terminal> terminalMap = terminals.findAllById(terminalIds).stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        for (UUID tid : terminalIds) {
            Terminal t = terminalMap.get(tid);
            if (t == null || !t.active) throw new BusinessRuleException("INVALID_TERMINAL", "Unknown or inactive terminal: " + tid);
        }
        Trip trip = new Trip();
        trip.id = UUID.randomUUID();
        trip.organizationId = orgId;
        trip.busId = bus.id;
        trip.code = req.code().trim();
        trip.status = TripStatus.PUBLISHED;
        trip.departureAt = TripRules.toInstant(req.stops().get(0).departureAt());
        trip.currency = req.currency();
        trips.saveAndFlush(trip);
        int seq = 0;
        for (StopItem s : req.stops()) {
            TripStop st = new TripStop();
            st.id = UUID.randomUUID();
            st.tripId = trip.id;
            st.seq = seq++;
            st.terminalId = s.terminalId();
            st.arrivalAt = TripRules.toInstant(s.arrivalAt());
            st.departureAt = TripRules.toInstant(s.departureAt());
            stops.save(st);
        }
        for (FareItem f : req.fares()) {
            TripFare tf = new TripFare();
            tf.id = UUID.randomUUID();
            tf.tripId = trip.id;
            tf.fromSeq = f.fromSeq();
            tf.toSeq = f.toSeq();
            tf.price = SeatRules.money(f.price());
            fares.save(tf);
        }
        return tripResponse(trip, bus);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> listTrips(UUID userId, UUID orgId) {
        auth.requireBusManager(userId, orgId);
        return trips.findByOrganizationIdOrderByDepartureAtDesc(orgId).stream()
                .map(t -> tripResponse(t, buses.findById(t.busId).orElseThrow())).toList();
    }

    /** Cancels a trip without paid tickets. Held bookings are cancelled too. */
    @Transactional
    public TripResponse cancelTrip(UUID userId, UUID orgId, UUID tripId) {
        auth.requireBusManager(userId, orgId);
        Trip trip = trips.lockById(tripId).filter(t -> t.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        Bus bus = buses.findById(trip.busId).orElseThrow();
        if (trip.status == TripStatus.CANCELLED) return tripResponse(trip, bus);
        if (!bookings.findByTripIdAndStatusIn(tripId, List.of(BookingStatus.CONFIRMED)).isEmpty()) {
            throw new BusinessRuleException("TRIP_HAS_TICKETS",
                    "Trip has paid tickets: contact the platform to cancel with refunds");
        }
        trip.status = TripStatus.CANCELLED;
        for (Booking b : bookings.findByTripIdAndStatusIn(tripId, List.of(BookingStatus.HELD))) {
            b.status = BookingStatus.CANCELLED;
            bookings.save(b);
            tickets.findByBookingId(b.id).forEach(t -> {
                t.status = BookingStatus.CANCELLED;
                tickets.save(t);
            });
        }
        return tripResponse(trips.save(trip), bus);
    }

    TripResponse tripResponse(Trip trip, Bus bus) {
        Map<UUID, Terminal> tm = terminals.findAll().stream().collect(Collectors.toMap(t -> t.id, t -> t));
        List<StopResponse> ss = stops.findByTripIdOrderBySeq(trip.id).stream().map(s -> {
            Terminal t = tm.get(s.terminalId);
            return new StopResponse(s.seq, s.terminalId, t == null ? null : t.city, t == null ? null : t.name,
                    s.arrivalAt == null ? null : BusDtos.local(s.arrivalAt),
                    s.departureAt == null ? null : BusDtos.local(s.departureAt));
        }).toList();
        List<FareResponse> fs = fares.findByTripId(trip.id).stream()
                .sorted(Comparator.comparingInt((TripFare f) -> f.fromSeq).thenComparingInt(f -> f.toSeq))
                .map(f -> new FareResponse(f.fromSeq, f.toSeq, f.price)).toList();
        long sold = tickets.findBlocking(trip.id, clock.instant()).stream().map(t -> t.seatNo).distinct().count();
        return new TripResponse(trip.id, trip.organizationId, trip.busId, trip.code, trip.status, trip.currency,
                ss, fs, sold, bus.seatCount);
    }

    // ---- status, bookings, admin ----

    @Transactional(readOnly = true)
    public PartnerStatus status(UUID userId, UUID orgId) {
        OrgInfo org = auth.requireBusManager(userId, orgId);
        List<DocumentType> missing = Sellability.missingOrgDocuments(documents.findByOrganizationIdAndBusIdIsNull(orgId), today());
        return new PartnerStatus(org.active() && missing.isEmpty(), missing, settings.salesEnabled());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> partnerBookings(UUID userId, UUID orgId) {
        auth.requireBusManager(userId, orgId);
        return toBookingResponses(bookings.findByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED)));
    }

    @Transactional(readOnly = true)
    public List<AdminBusResponse> adminBuses() {
        LocalDate today = today();
        Map<UUID, Optional<OrgInfo>> orgCache = new HashMap<>();
        List<Bus> all = buses.findAllByOrderByOrganizationIdAscPlateNumberAsc();
        Map<UUID, List<BusDocument>> docs = docsByBus(all);
        return all.stream().map(b -> {
            Optional<OrgInfo> org = orgCache.computeIfAbsent(b.organizationId, auth::lookupOrEmpty);
            boolean orgOk = org.isPresent()
                    && Sellability.orgSellable(org.get(), documents.findByOrganizationIdAndBusIdIsNull(b.organizationId), today);
            return new AdminBusResponse(toResponse(b, orgOk, docs.getOrDefault(b.id, List.of()), today),
                    org.map(OrgInfo::name).orElse(null));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> toBookingResponses(List<Booking> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<Ticket>> byBooking = tickets.findByBookingIdIn(list.stream().map(b -> b.id).toList()).stream()
                .collect(Collectors.groupingBy(t -> t.bookingId));
        Map<UUID, Trip> tripMap = trips.findAllById(list.stream().map(b -> b.tripId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        return list.stream().map(b -> bookingResponse(b, byBooking.getOrDefault(b.id, List.of()), tripMap.get(b.tripId))).toList();
    }

    static BookingResponse bookingResponse(Booking b, List<Ticket> ts, Trip trip) {
        List<TicketResponse> tr = ts.stream().sorted(Comparator.comparingInt(t -> t.seatNo))
                .map(t -> new TicketResponse(t.id, t.seatNo, t.passengerName, t.gender, t.price, t.status)).toList();
        return new BookingResponse(b.id, b.organizationId, b.customerId, b.tripId, b.fromSeq, b.toSeq, b.currency,
                b.totalAmount, b.status, b.holdExpiresAt, trip == null ? null : BusDtos.local(trip.departureAt),
                tr, b.createdAt);
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(BusSettings.ZONE)); }
}
