package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos.HoldDto;
import com.waylo.busservice.dto.BusDtos.HoldRequest;
import com.waylo.busservice.dto.BusDtos.PassengerItem;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ConflictException;
import com.waylo.busservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HoldServiceTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final TripStopRepository stops = mock(TripStopRepository.class);
    private final TripFareRepository fares = mock(TripFareRepository.class);
    private final BusRepository buses = mock(BusRepository.class);
    private final TerminalRepository terminals = mock(TerminalRepository.class);
    private final BusDocumentRepository documents = mock(BusDocumentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC);

    private final UUID orgId = UUID.randomUUID();
    private final ReentrantLock tripLock = new ReentrantLock();
    // in-memory "database", guarded by the trip lock like the real FOR UPDATE
    private final Map<String, Booking> byKey = new ConcurrentHashMap<>();
    private final Map<UUID, Booking> byId = new ConcurrentHashMap<>();
    private final List<Ticket> ticketStore = new CopyOnWriteArrayList<>();
    private Trip trip;
    private Bus bus;
    private HoldService service;

    @BeforeEach
    void setUp() { build(); }

    private void build() {
        trip = new Trip();
        trip.id = UUID.randomUUID();
        trip.organizationId = orgId;
        trip.busId = UUID.randomUUID();
        trip.status = TripStatus.PUBLISHED;
        trip.currency = "TRY";
        trip.departureAt = Instant.parse("2027-02-02T19:00:00Z");
        bus = new Bus();
        bus.id = trip.busId;
        bus.organizationId = orgId;
        bus.layout = SeatLayout.TWO_PLUS_TWO;
        bus.rowCount = 10;
        bus.seatCount = 40;
        bus.active = true;
        service = new HoldService(auth, new BusSettings(true, 15, 30), clock, trips, stops, fares, buses,
                terminals, documents, bookings, tickets);

        UUID t1 = UUID.randomUUID(), t2 = UUID.randomUUID(), t3 = UUID.randomUUID();
        List<TripStop> ss = List.of(stop(0, t1, "2027-02-02T19:00:00Z"), stop(1, t2, "2027-02-02T22:00:00Z"),
                stop(2, t3, "2027-02-03T01:00:00Z"));
        when(stops.findByTripIdOrderBySeq(trip.id)).thenReturn(ss);
        when(terminals.findAllById(any())).thenReturn(List.of(terminal(t1, "Ankara"), terminal(t2, "Bolu"), terminal(t3, "Istanbul")));
        when(trips.lockById(trip.id)).thenAnswer(i -> {
            tripLock.lock(); // released by the test thread wrapper, like a commit
            return Optional.of(trip);
        });
        when(buses.findById(bus.id)).thenReturn(Optional.of(bus));
        when(auth.lookupOrEmpty(orgId)).thenReturn(Optional.of(new OrgInfo(orgId, "Acme Bus", "ACTIVE", "BUS", "COMPANY")));
        LocalDate far = LocalDate.of(2030, 1, 1);
        when(documents.findByOrganizationIdAndBusIdIsNull(orgId)).thenReturn(List.of(
                doc(DocumentType.TRANSPORT_AUTHORIZATION, far), doc(DocumentType.TAX_CERTIFICATE, null)));
        when(documents.findByBusId(bus.id)).thenReturn(List.of(doc(DocumentType.VEHICLE_REGISTRATION, far),
                doc(DocumentType.VEHICLE_CARD, far), doc(DocumentType.TRAFFIC_INSURANCE, far),
                doc(DocumentType.SEAT_ACCIDENT_INSURANCE, far)));
        TripFare full = fare(0, 2, "500.00");
        TripFare first = fare(0, 1, "300.00");
        TripFare second = fare(1, 2, "250.00");
        for (TripFare f : List.of(full, first, second)) {
            when(fares.findByTripIdAndFromSeqAndToSeq(trip.id, f.fromSeq, f.toSeq)).thenReturn(Optional.of(f));
        }
        when(bookings.findByIdempotencyKey(any())).thenAnswer(i -> Optional.ofNullable(byKey.get(i.getArgument(0))));
        when(bookings.findById(any(UUID.class))).thenAnswer(i -> Optional.ofNullable(byId.get(i.getArgument(0))));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(i -> store(i.getArgument(0)));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> store(i.getArgument(0)));
        when(tickets.findBlocking(any(), any())).thenAnswer(i -> new ArrayList<>(ticketStore.stream()
                .filter(t -> t.status == BookingStatus.CONFIRMED
                        || (t.status == BookingStatus.HELD && t.holdExpiresAt.isAfter(i.getArgument(1)))).toList()));
        when(tickets.saveAll(any())).thenAnswer(i -> {
            Iterable<Ticket> in = i.getArgument(0);
            for (Ticket t : in) if (!ticketStore.contains(t)) ticketStore.add(t);
            return in;
        });
        when(tickets.findByBookingId(any())).thenAnswer(i -> ticketStore.stream()
                .filter(t -> t.bookingId.equals(i.getArgument(0))).toList());
    }

    private Booking store(Booking b) {
        byKey.put(b.idempotencyKey, b);
        byId.put(b.id, b);
        return b;
    }

    private TripStop stop(int seq, UUID terminal, String dep) {
        TripStop s = new TripStop();
        s.id = UUID.randomUUID();
        s.tripId = trip.id;
        s.seq = seq;
        s.terminalId = terminal;
        s.arrivalAt = Instant.parse(dep);
        s.departureAt = Instant.parse(dep);
        return s;
    }

    private Terminal terminal(UUID id, String city) {
        Terminal t = new Terminal();
        t.id = id;
        t.city = city;
        t.name = city + " Otogar";
        t.active = true;
        return t;
    }

    private TripFare fare(int from, int to, String price) {
        TripFare f = new TripFare();
        f.tripId = trip.id;
        f.fromSeq = from;
        f.toSeq = to;
        f.price = new BigDecimal(price);
        return f;
    }

    private BusDocument doc(DocumentType type, LocalDate until) {
        BusDocument d = new BusDocument();
        d.type = type;
        d.status = DocumentStatus.APPROVED;
        d.validUntil = until;
        d.createdAt = Instant.now();
        return d;
    }

    private HoldRequest req(String key, int from, int to, PassengerItem... ps) {
        return new HoldRequest(trip.id, from, to, List.of(ps), UUID.randomUUID(), key, "corr");
    }

    private PassengerItem p(int seat, Gender g) { return new PassengerItem(seat, "Ayse Yilmaz", g, null); }

    /** Runs hold() the way a transaction would: trip lock held until the call returns. */
    private HoldDto tx(HoldRequest r) {
        try {
            return service.hold(r);
        } finally {
            while (tripLock.isHeldByCurrentThread()) tripLock.unlock();
        }
    }

    @Test
    void holdsSeatsAndPricesFromFare() {
        HoldDto h = tx(req("k1", 0, 2, p(1, Gender.FEMALE), p(2, Gender.FEMALE)));
        assertThat(h.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(h.seatNos()).containsExactly(1, 2);
        assertThat(h.status()).isEqualTo(BookingStatus.HELD);
        assertThat(h.fromCity()).isEqualTo("Ankara");
        assertThat(h.toCity()).isEqualTo("Istanbul");
        assertThat(h.companyName()).isEqualTo("Acme Bus");
        assertThat(h.expiresAt()).isEqualTo(Instant.parse("2027-02-01T00:15:00Z"));
    }

    @Test
    void salesDisabledRejectsHoldWith422Code() {
        service = new HoldService(auth, new BusSettings(false, 15, 30), clock, trips, stops, fares, buses,
                terminals, documents, bookings, tickets);
        assertThatThrownBy(() -> tx(req("k1", 0, 2, p(1, Gender.MALE))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("BUS_SALES_DISABLED");
    }

    @Test
    void sameIdempotencyKeyReturnsSameHold() {
        HoldDto a = tx(req("same", 0, 2, p(3, Gender.MALE)));
        HoldDto b = tx(req("same", 0, 2, p(3, Gender.MALE)));
        assertThat(b.holdId()).isEqualTo(a.holdId());
        assertThat(ticketStore).hasSize(1);
    }

    @Test
    void sameSeatOnOverlappingSegmentIsTaken() {
        tx(req("a", 0, 1, p(5, Gender.MALE)));
        assertThatThrownBy(() -> tx(req("b", 0, 2, p(5, Gender.MALE)))).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SEAT_TAKEN");
        tx(req("c", 1, 2, p(5, Gender.MALE))); // freed after Bolu
    }

    @Test
    void genderRuleAppliesAcrossBookings() {
        tx(req("a", 0, 2, p(1, Gender.FEMALE)));
        assertThatThrownBy(() -> tx(req("b", 0, 2, p(2, Gender.MALE)))).isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("SEAT_GENDER_CONFLICT");
    }

    @Test
    void closedSalesAndUnknownSegmentAndMissingDocs() {
        trip.departureAt = Instant.parse("2027-02-01T00:10:00Z");
        List<TripStop> ss = stops.findByTripIdOrderBySeq(trip.id);
        ss.get(0).departureAt = Instant.parse("2027-02-01T00:10:00Z");
        assertThatThrownBy(() -> tx(req("a", 0, 2, p(1, Gender.MALE)))).hasMessageContaining("closed");
        ss.get(0).departureAt = Instant.parse("2027-02-02T19:00:00Z");
        assertThatThrownBy(() -> tx(req("b", 0, 5, p(1, Gender.MALE)))).hasMessageContaining("outside the route");
        when(documents.findByBusId(bus.id)).thenReturn(List.of());
        assertThatThrownBy(() -> tx(req("c", 0, 2, p(1, Gender.MALE)))).hasMessageContaining("Bus is not sellable");
    }

    @Test
    void confirmReleaseAndExpire() {
        HoldDto h = tx(req("k", 0, 2, p(1, Gender.MALE)));
        assertThat(service.confirm(h.holdId()).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(ticketStore).allMatch(t -> t.status == BookingStatus.CONFIRMED);
        assertThat(service.release(h.holdId()).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(service.release(h.holdId()).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThatThrownBy(() -> service.confirm(h.holdId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void expiredHoldCannotBeConfirmedAndSeatIsFreeAgain() {
        HoldDto h = tx(req("k", 0, 2, p(1, Gender.MALE)));
        byId.get(h.holdId()).holdExpiresAt = Instant.parse("2027-01-31T23:00:00Z");
        ticketStore.forEach(t -> t.holdExpiresAt = Instant.parse("2027-01-31T23:00:00Z"));
        assertThatThrownBy(() -> service.confirm(h.holdId())).isInstanceOf(ConflictException.class);
        tx(req("other", 0, 2, p(1, Gender.MALE)));
        when(bookings.findExpiredHolds(any())).thenReturn(List.of(byId.get(h.holdId())));
        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(byId.get(h.holdId()).status).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void parallelHoldsOnOneSeatYieldExactlyOneSuccess() throws Exception {
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger taken = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String key = "par-" + i;
            fs.add(pool.submit(() -> {
                start.await();
                try {
                    tx(req(key, 0, 2, p(7, Gender.MALE)));
                    ok.incrementAndGet();
                } catch (ConflictException ex) {
                    if ("SEAT_TAKEN".equals(ex.getCode())) taken.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(20, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(taken.get()).isEqualTo(n - 1);
        assertThat(ticketStore).hasSize(1);
    }

    @Test
    void parallelSameKeyCreatesOneBooking() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HoldDto>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                return tx(req("dup", 0, 2, p(9, Gender.FEMALE)));
            }));
        }
        start.countDown();
        Set<UUID> ids = new HashSet<>();
        for (Future<HoldDto> f : fs) ids.add(f.get(20, TimeUnit.SECONDS).holdId());
        pool.shutdown();
        assertThat(ids).hasSize(1);
        assertThat(ticketStore).hasSize(1);
    }
}
