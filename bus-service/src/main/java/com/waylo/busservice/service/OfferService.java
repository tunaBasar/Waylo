package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.exception.ResourceNotFoundException;
import com.waylo.busservice.repository.*;
import com.waylo.busservice.rules.SeatRules;
import com.waylo.busservice.rules.Sellability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Public search. Sellability is computed at query time; nothing is returned while the legal gate is closed. */
@Service
public class OfferService {

    private final AuthGateway auth;
    private final BusSettings settings;
    private final Clock clock;
    private final TripRepository trips;
    private final TripStopRepository stops;
    private final TripFareRepository fares;
    private final BusRepository buses;
    private final TerminalRepository terminals;
    private final BusDocumentRepository documents;
    private final TicketRepository tickets;

    public OfferService(AuthGateway auth, BusSettings settings, Clock clock, TripRepository trips,
                        TripStopRepository stops, TripFareRepository fares, BusRepository buses,
                        TerminalRepository terminals, BusDocumentRepository documents, TicketRepository tickets) {
        this.auth = auth;
        this.settings = settings;
        this.clock = clock;
        this.trips = trips;
        this.stops = stops;
        this.fares = fares;
        this.buses = buses;
        this.terminals = terminals;
        this.documents = documents;
        this.tickets = tickets;
    }

    @Transactional(readOnly = true)
    public List<TripOffer> search(String fromCity, String toCity, LocalDate date) {
        if (!settings.salesEnabled()) return List.of();
        Instant now = clock.instant();
        Instant dayStart = date.atStartOfDay(BusSettings.ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(BusSettings.ZONE).toInstant();
        List<Trip> candidates = trips.findByStatusAndDepartureAtBetweenOrderByDepartureAt(TripStatus.PUBLISHED,
                dayStart.minusSeconds(86400L), dayEnd);
        if (candidates.isEmpty()) return List.of();
        List<UUID> ids = candidates.stream().map(t -> t.id).toList();
        Map<UUID, List<TripStop>> stopMap = stops.findByTripIdInOrderBySeq(ids).stream().collect(Collectors.groupingBy(s -> s.tripId));
        Map<UUID, List<TripFare>> fareMap = fares.findByTripIdIn(ids).stream().collect(Collectors.groupingBy(f -> f.tripId));
        Map<UUID, List<Ticket>> ticketMap = tickets.findBlockingForTrips(ids, now).stream().collect(Collectors.groupingBy(t -> t.tripId));
        Map<UUID, Terminal> tm = terminals.findAll().stream().collect(Collectors.toMap(t -> t.id, t -> t));
        Map<UUID, Optional<OrgInfo>> orgs = new HashMap<>();
        Map<UUID, Boolean> orgOk = new HashMap<>();
        List<TripOffer> result = new ArrayList<>();
        for (Trip trip : candidates) {
            List<TripStop> ss = stopMap.getOrDefault(trip.id, List.of());
            int from = indexOfCity(ss, tm, fromCity, 0);
            if (from < 0) continue;
            int to = indexOfCity(ss, tm, toCity, from + 1);
            if (to < 0) continue;
            TripStop boarding = ss.get(from);
            if (boarding.departureAt.isBefore(dayStart) || !boarding.departureAt.isBefore(dayEnd)) continue;
            if (!boarding.departureAt.minusSeconds(settings.salesCloseMinutes() * 60L).isAfter(now)) continue;
            Optional<TripFare> fare = fareMap.getOrDefault(trip.id, List.of()).stream()
                    .filter(f -> f.fromSeq == from && f.toSeq == to).findFirst();
            if (fare.isEmpty()) continue;
            Bus bus = buses.findById(trip.busId).orElse(null);
            if (bus == null || !bus.active) continue;
            LocalDate on = LocalDate.ofInstant(boarding.departureAt, BusSettings.ZONE);
            Optional<OrgInfo> org = orgs.computeIfAbsent(trip.organizationId, auth::lookupOrEmpty);
            if (org.isEmpty() || !org.get().active() || !org.get().bus()) continue;
            if (!orgOk.computeIfAbsent(trip.organizationId, k -> Sellability.missingOrgDocuments(
                    documents.findByOrganizationIdAndBusIdIsNull(k), on).isEmpty())) continue;
            if (!Sellability.missingBusDocuments(documents.findByBusId(bus.id), on).isEmpty()) continue;
            int taken = SeatRules.occupied(from, to, ticketMap.getOrDefault(trip.id, List.of())).size();
            Terminal ft = tm.get(ss.get(from).terminalId);
            Terminal tt = tm.get(ss.get(to).terminalId);
            result.add(new TripOffer(trip.id, trip.organizationId, org.get().name(), trip.code, from, to,
                    ft.id, tt.id, ft.city, tt.city, BusDtos.local(boarding.departureAt),
                    BusDtos.local(ss.get(to).arrivalAt), fare.get().price, trip.currency,
                    bus.seatCount - taken, bus.brand, bus.model, bus.layout));
        }
        result.sort(Comparator.comparing(TripOffer::departureAt));
        return result;
    }

    private static int indexOfCity(List<TripStop> ss, Map<UUID, Terminal> tm, String city, int start) {
        for (int i = start; i < ss.size(); i++) {
            Terminal t = tm.get(ss.get(i).terminalId);
            if (t != null && t.city.equalsIgnoreCase(city.trim())) return i;
        }
        return -1;
    }

    @Transactional(readOnly = true)
    public SeatMap seatMap(UUID tripId, int fromSeq, int toSeq) {
        if (!settings.salesEnabled()) throw new ResourceNotFoundException("Trip not found: " + tripId);
        Trip trip = trips.findById(tripId).filter(t -> t.status == TripStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        Bus bus = buses.findById(trip.busId).orElseThrow();
        Map<Integer, Gender> occupied = SeatRules.occupied(fromSeq, toSeq, tickets.findBlocking(tripId, clock.instant()));
        List<SeatState> seats = new ArrayList<>();
        for (int n = 1; n <= bus.seatCount; n++) {
            seats.add(new SeatState(n, !occupied.containsKey(n), occupied.get(n)));
        }
        return new SeatMap(tripId, bus.layout, bus.rowCount, fromSeq, toSeq, seats);
    }
}
