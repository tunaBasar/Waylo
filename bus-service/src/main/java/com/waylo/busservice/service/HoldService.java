package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ConflictException;
import com.waylo.busservice.exception.ResourceNotFoundException;
import com.waylo.busservice.repository.*;
import com.waylo.busservice.rules.SeatRules;
import com.waylo.busservice.rules.Sellability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Internal hold lifecycle used by package-booking-service. All holds on a trip serialize on the trip row lock. */
@Service
public class HoldService {

    private final AuthGateway auth;
    private final BusSettings settings;
    private final Clock clock;
    private final TripRepository trips;
    private final TripStopRepository stops;
    private final TripFareRepository fares;
    private final BusRepository buses;
    private final TerminalRepository terminals;
    private final BusDocumentRepository documents;
    private final BookingRepository bookings;
    private final TicketRepository tickets;

    public HoldService(AuthGateway auth, BusSettings settings, Clock clock, TripRepository trips,
                       TripStopRepository stops, TripFareRepository fares, BusRepository buses,
                       TerminalRepository terminals, BusDocumentRepository documents, BookingRepository bookings,
                       TicketRepository tickets) {
        this.auth = auth;
        this.settings = settings;
        this.clock = clock;
        this.trips = trips;
        this.stops = stops;
        this.fares = fares;
        this.buses = buses;
        this.terminals = terminals;
        this.documents = documents;
        this.bookings = bookings;
        this.tickets = tickets;
    }

    @Transactional
    public HoldDto hold(HoldRequest req) {
        if (!settings.salesEnabled()) {
            throw new BusinessRuleException("BUS_SALES_DISABLED", "Bus ticket sales are not enabled");
        }
        Optional<Booking> existing = bookings.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) return toDto(existing.get());
        if (req.fromSeq() >= req.toSeq()) {
            throw new BusinessRuleException("INVALID_SEGMENT", "toSeq must be greater than fromSeq");
        }

        Trip trip = trips.lockById(req.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId()));
        // re-check the key now that we hold the trip lock: a concurrent same-key hold has committed by now
        existing = bookings.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) return toDto(existing.get());

        Instant now = clock.instant();
        if (trip.status != TripStatus.PUBLISHED) {
            throw new BusinessRuleException("TRIP_NOT_AVAILABLE", "Trip is not available");
        }
        List<TripStop> tripStops = stops.findByTripIdOrderBySeq(trip.id);
        if (req.toSeq() >= tripStops.size()) {
            throw new BusinessRuleException("INVALID_SEGMENT", "Segment is outside the route of the trip");
        }
        TripStop boarding = tripStops.get(req.fromSeq());
        if (!boarding.departureAt.minusSeconds(settings.salesCloseMinutes() * 60L).isAfter(now)) {
            throw new BusinessRuleException("SALES_CLOSED", "Ticket sales for this departure are closed");
        }
        OrgInfo org = auth.lookupOrEmpty(trip.organizationId)
                .filter(o -> o.active() && o.bus())
                .orElseThrow(() -> new BusinessRuleException("PARTNER_NOT_SELLABLE", "Partner is not sellable"));
        LocalDate on = LocalDate.ofInstant(boarding.departureAt, BusSettings.ZONE);
        if (!Sellability.missingOrgDocuments(documents.findByOrganizationIdAndBusIdIsNull(org.id()), on).isEmpty()) {
            throw new BusinessRuleException("PARTNER_NOT_SELLABLE", "Partner documents are missing or expired");
        }
        Bus bus = buses.findById(trip.busId).orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + trip.busId));
        if (!bus.active || !Sellability.missingBusDocuments(documents.findByBusId(bus.id), on).isEmpty()) {
            throw new BusinessRuleException("PARTNER_NOT_SELLABLE", "Bus is not sellable");
        }
        TripFare fare = fares.findByTripIdAndFromSeqAndToSeq(trip.id, req.fromSeq(), req.toSeq())
                .orElseThrow(() -> new BusinessRuleException("SEGMENT_NOT_OFFERED", "This segment has no fare"));

        List<Ticket> blocking = tickets.findBlocking(trip.id, now);
        SeatRules.validate(bus, req.fromSeq(), req.toSeq(), req.passengers(), blocking);

        Instant expires = now.plusSeconds(settings.holdTtlMinutes() * 60L);
        Booking b = new Booking();
        b.id = UUID.randomUUID();
        b.organizationId = trip.organizationId;
        b.customerId = req.principalUserId();
        b.tripId = trip.id;
        b.fromSeq = req.fromSeq();
        b.toSeq = req.toSeq();
        b.currency = trip.currency;
        b.totalAmount = SeatRules.money(fare.price.multiply(BigDecimal.valueOf(req.passengers().size())));
        b.status = BookingStatus.HELD;
        b.holdExpiresAt = expires;
        b.idempotencyKey = req.idempotencyKey();
        b.orderCorrelationId = req.orderCorrelationId();
        bookings.saveAndFlush(b);
        List<Ticket> created = new ArrayList<>();
        for (PassengerItem p : req.passengers()) {
            Ticket t = new Ticket();
            t.id = UUID.randomUUID();
            t.bookingId = b.id;
            t.tripId = trip.id;
            t.seatNo = p.seatNo();
            t.fromSeq = req.fromSeq();
            t.toSeq = req.toSeq();
            t.passengerName = p.name().trim();
            t.gender = p.gender();
            t.identityNumber = p.identityNumber();
            t.price = SeatRules.money(fare.price);
            t.status = BookingStatus.HELD;
            t.holdExpiresAt = expires;
            created.add(t);
        }
        tickets.saveAll(created);
        return toDto(b, created);
    }

    @Transactional
    public HoldDto confirm(UUID id) {
        Booking b = bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + id));
        if (b.status == BookingStatus.CONFIRMED) return toDto(b);
        if (b.status != BookingStatus.HELD || !b.holdExpiresAt.isAfter(clock.instant())) {
            throw new ConflictException("HOLD_EXPIRED", "The hold has expired or was released");
        }
        b.status = BookingStatus.CONFIRMED;
        bookings.save(b);
        List<Ticket> ts = tickets.findByBookingId(id);
        ts.forEach(t -> t.status = BookingStatus.CONFIRMED);
        tickets.saveAll(ts);
        return toDto(b, ts);
    }

    @Transactional
    public HoldDto release(UUID id) {
        Booking b = bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + id));
        if (b.status == BookingStatus.HELD || b.status == BookingStatus.CONFIRMED) {
            b.status = BookingStatus.CANCELLED;
            bookings.save(b);
            List<Ticket> ts = tickets.findByBookingId(id);
            ts.forEach(t -> t.status = BookingStatus.CANCELLED);
            tickets.saveAll(ts);
            return toDto(b, ts);
        }
        return toDto(b);
    }

    /** Marks HELD bookings whose TTL passed as EXPIRED. Returns the number expired. */
    @Transactional
    public int expireOverdue() {
        List<Booking> overdue = bookings.findExpiredHolds(clock.instant());
        for (Booking b : overdue) {
            b.status = BookingStatus.EXPIRED;
            List<Ticket> ts = tickets.findByBookingId(b.id);
            ts.forEach(t -> t.status = BookingStatus.EXPIRED);
            tickets.saveAll(ts);
        }
        bookings.saveAll(overdue);
        return overdue.size();
    }

    // ---- customer reads ----

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(UUID userId) {
        List<Booking> list = bookings.findByCustomerIdOrderByCreatedAtDesc(userId).stream()
                .filter(b -> b.status != BookingStatus.EXPIRED).toList();
        return toResponses(list);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id, UUID userId, boolean admin) {
        Booking b = bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        if (!admin && !b.customerId.equals(userId)) throw new ResourceNotFoundException("Booking not found: " + id);
        return toResponses(List.of(b)).get(0);
    }

    private List<BookingResponse> toResponses(List<Booking> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<Ticket>> byBooking = tickets.findByBookingIdIn(list.stream().map(b -> b.id).toList()).stream()
                .collect(Collectors.groupingBy(t -> t.bookingId));
        Map<UUID, Trip> tripMap = trips.findAllById(list.stream().map(b -> b.tripId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        return list.stream().map(b -> BusService.bookingResponse(b, byBooking.getOrDefault(b.id, List.of()), tripMap.get(b.tripId))).toList();
    }

    private HoldDto toDto(Booking b) { return toDto(b, tickets.findByBookingId(b.id)); }

    private HoldDto toDto(Booking b, List<Ticket> ts) {
        List<TripStop> tripStops = stops.findByTripIdOrderBySeq(b.tripId);
        TripStop from = tripStops.get(b.fromSeq);
        TripStop to = tripStops.get(b.toSeq);
        Map<UUID, Terminal> tm = terminals.findAllById(List.of(from.terminalId, to.terminalId)).stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        Terminal ft = tm.get(from.terminalId);
        Terminal tt = tm.get(to.terminalId);
        String company = auth.lookupOrEmpty(b.organizationId).map(OrgInfo::name).orElse(null);
        List<Integer> seats = ts.stream().map(t -> t.seatNo).sorted().toList();
        return new HoldDto(b.id, b.organizationId, b.tripId, b.fromSeq, b.toSeq, from.terminalId, to.terminalId,
                ft == null ? null : ft.city, tt == null ? null : tt.city, ft == null ? null : ft.name,
                tt == null ? null : tt.name, company, BusDtos.local(from.departureAt), BusDtos.local(to.arrivalAt),
                seats, b.currency, b.totalAmount, b.status, b.holdExpiresAt);
    }
}
