package com.waylo.flightseatinventoryservice.supplier;

import com.waylo.flightseatinventoryservice.client.FlightClient;
import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.domain.HoldStatus;
import com.waylo.flightseatinventoryservice.domain.SeatHold;
import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.FlightSummary;
import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.ConflictException;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.repository.SeatHoldRepository;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InternalFlightSupplierTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final UUID USER = UUID.randomUUID();

    FlightSeatRepository seats = mock(FlightSeatRepository.class);
    SeatHoldRepository holds = mock(SeatHoldRepository.class);
    FlightClient client = mock(FlightClient.class);
    OfferCodec codec = new OfferCodec("test-secret");
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    InternalFlightSupplier supplier;
    FlightSeat seat;

    @BeforeEach
    void setUp() {
        supplier = new InternalFlightSupplier(seats, holds, client, codec, clock, 10, 15, 120);
        seat = seat("12A", "150.00");
        when(client.getSummary("TK100")).thenReturn(ApiResponse.of(flight(NOW.plus(Duration.ofDays(3)), "SCHEDULED")));
        when(holds.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(holds.saveAndFlush(any(SeatHold.class))).thenAnswer(i -> i.getArgument(0));
        when(seats.saveAndFlush(any(FlightSeat.class))).thenAnswer(i -> i.getArgument(0));
        when(seats.findForUpdate("TK100", "12A")).thenReturn(Optional.of(seat));
    }

    static FlightSummary flight(Instant dep, String status) {
        return new FlightSummary("TK100", "IST", "ESB", dep, dep.plus(Duration.ofHours(1)), "THY", status);
    }

    static FlightSeat seat(String number, String price) {
        FlightSeat s = new FlightSeat();
        s.setId(UUID.randomUUID());
        s.setFlightNumber("TK100");
        s.setSeatNumber(number);
        s.setSeatType("ECONOMY");
        s.setPrice(new BigDecimal(price));
        s.setAvailable(true);
        return s;
    }

    String offer(String price) {
        return codec.encode("TK100", "ECONOMY", new BigDecimal(price), "TRY", NOW.plus(Duration.ofMinutes(5)));
    }

    HoldCommand cmd(String offerId, String seatNo, String key) {
        return new HoldCommand(offerId, seatNo, "Ali", "Yilmaz", USER, key, "order-1");
    }

    @Test
    void searchGroupsByCabinWithMinPrice() {
        when(client.search("IST", "ESB", "2026-11-04")).thenReturn(ApiResponse.of(List.of(flight(NOW.plus(Duration.ofDays(3)), "SCHEDULED"))));
        when(seats.findAvailable("TK100", NOW)).thenReturn(List.of(seat("1A", "200.00"), seat("1B", "150.00")));
        List<FlightOffer> offers = supplier.searchOffers(new OfferSearch("IST", "ESB", LocalDate.parse("2026-11-04"), 1));
        assertEquals(1, offers.size());
        assertEquals(new BigDecimal("150.00"), offers.get(0).totalAmount());
        assertEquals(2, offers.get(0).availableSeats());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), offers.get(0).expiresAt());
    }

    @Test
    void searchHidesFlightsInsideSalesCutoffAndCancelled() {
        when(client.search(anyString(), anyString(), anyString())).thenReturn(ApiResponse.of(List.of(
                flight(NOW.plus(Duration.ofMinutes(90)), "SCHEDULED"), flight(NOW.plus(Duration.ofDays(2)), "CANCELLED"))));
        assertTrue(supplier.searchOffers(new OfferSearch("IST", "ESB", LocalDate.parse("2026-11-04"), 1)).isEmpty());
    }

    @Test
    void tamperedOrExpiredOfferRejected() {
        String id = offer("150.00");
        BusinessRuleException bad = assertThrows(BusinessRuleException.class,
                () -> supplier.hold(cmd(id.substring(0, id.length() - 2) + "xx", "12A", "k1")));
        assertEquals("INVALID_OFFER", bad.getRuleCode());
        String old = codec.encode("TK100", "ECONOMY", new BigDecimal("150.00"), "TRY", NOW.minusSeconds(1));
        ConflictException ex = assertThrows(ConflictException.class, () -> supplier.hold(cmd(old, "12A", "k2")));
        assertEquals("OFFER_EXPIRED", ex.getCode());
    }

    @Test
    void holdMarksSeatAndStoresHold() {
        FlightHold h = supplier.hold(cmd(offer("150.00"), "12A", "k1"));
        assertEquals("HELD", h.status());
        assertEquals(new BigDecimal("150.00"), h.totalAmount());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), h.expiresAt());
        assertFalse(seat.isAvailable());
        assertEquals(USER, seat.getReservedBy());
        assertEquals("INTERNAL", h.supplier());
        assertNull(h.pnr());
    }

    @Test
    void sameIdempotencyKeyReturnsSameHold() {
        SeatHold existing = new SeatHold();
        existing.setId(UUID.randomUUID());
        existing.setFlightNumber("TK100");
        existing.setTotalAmount(new BigDecimal("150.00"));
        existing.setCurrency("TRY");
        existing.setHoldExpiresAt(NOW.plusSeconds(60));
        when(holds.findByIdempotencyKey("k1")).thenReturn(Optional.of(existing));
        assertEquals(existing.getId(), supplier.hold(cmd(offer("150.00"), "12A", "k1")).holdId());
        verify(seats, never()).findForUpdate(anyString(), anyString());
    }

    @Test
    void priceChangedSinceOfferIsFareChanged() {
        seat.setPrice(new BigDecimal("175.00"));
        ConflictException ex = assertThrows(ConflictException.class,
                () -> supplier.hold(cmd(offer("150.00"), "12A", "k1")));
        assertEquals("FARE_CHANGED", ex.getCode());
        assertTrue(seat.isAvailable());
    }

    @Test
    void heldSeatCannotBeHeldAgainButExpiredHoldCan() {
        seat.setAvailable(false);
        seat.setHoldExpiresAt(NOW.plusSeconds(30));
        ConflictException ex = assertThrows(ConflictException.class, () -> supplier.hold(cmd(offer("150.00"), "12A", "k1")));
        assertEquals("SEAT_ALREADY_RESERVED", ex.getCode());
        seat.setHoldExpiresAt(NOW.minusSeconds(30));
        assertEquals("HELD", supplier.hold(cmd(offer("150.00"), "12A", "k2")).status());
    }

    @Test
    void closedFlightNotBookable() {
        when(client.getSummary("TK100")).thenReturn(ApiResponse.of(flight(NOW.plus(Duration.ofDays(3)), "CANCELLED")));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> supplier.hold(cmd(offer("150.00"), "12A", "k1")));
        assertEquals("FLIGHT_NOT_BOOKABLE", ex.getRuleCode());
    }

    @Test
    void autoPicksCheapestSeatWhenNoSeatNumber() {
        when(seats.lockCheapestFree("TK100", "ECONOMY", NOW)).thenReturn(Optional.of(seat));
        assertEquals("12A", supplier.hold(cmd(offer("150.00"), null, "k1")).seatNumber());
    }

    @Test
    void issueTicketConfirmsAndIsIdempotent() {
        SeatHold h = heldHold();
        seat.setAvailable(false);
        seat.setHoldExpiresAt(NOW.plusSeconds(600));
        when(holds.findByIdForUpdate(h.getId())).thenReturn(Optional.of(h));
        when(seats.findByIdForUpdate(seat.getId())).thenReturn(Optional.of(seat));
        FlightHold first = supplier.issueTicket(h.getId());
        assertEquals("CONFIRMED", first.status());
        assertEquals(6, first.pnr().length());
        assertTrue(first.ticketNumber().startsWith("WL"));
        assertNull(seat.getHoldExpiresAt());
        assertFalse(seat.isAvailable());
        assertEquals(first.pnr(), supplier.issueTicket(h.getId()).pnr());
    }

    @Test
    void issueTicketOnExpiredHoldFails() {
        SeatHold h = heldHold();
        h.setHoldExpiresAt(NOW.minusSeconds(1));
        when(holds.findByIdForUpdate(h.getId())).thenReturn(Optional.of(h));
        assertEquals("HOLD_EXPIRED", assertThrows(ConflictException.class, () -> supplier.issueTicket(h.getId())).getCode());
    }

    @Test
    void cancelFreesSeatAndIsIdempotent() {
        SeatHold h = heldHold();
        seat.setAvailable(false);
        seat.setReservationId(h.getId().toString());
        when(holds.findByIdForUpdate(h.getId())).thenReturn(Optional.of(h));
        when(seats.findByIdForUpdate(seat.getId())).thenReturn(Optional.of(seat));
        assertEquals("CANCELLED", supplier.cancel(h.getId()).status());
        assertTrue(seat.isAvailable());
        assertEquals("CANCELLED", supplier.cancel(h.getId()).status());
        verify(seats, times(1)).saveAndFlush(seat);
    }

    @Test
    void refundQuoteFullForConfirmed() {
        SeatHold h = heldHold();
        h.setStatus(HoldStatus.CONFIRMED);
        when(holds.findById(h.getId())).thenReturn(Optional.of(h));
        RefundQuote q = supplier.refundQuote(h.getId());
        assertEquals(new BigDecimal("150.00"), q.customerRefundAmount());
        assertEquals(new BigDecimal("0.00"), q.supplierRecoverableAmount());
    }

    @Test
    void expireHoldsFreesSeats() {
        SeatHold h = heldHold();
        h.setHoldExpiresAt(NOW.minusSeconds(5));
        seat.setAvailable(false);
        seat.setReservationId(h.getId().toString());
        when(holds.findByStatusAndHoldExpiresAtBefore(HoldStatus.HELD, NOW)).thenReturn(List.of(h));
        when(seats.findByIdForUpdate(seat.getId())).thenReturn(Optional.of(seat));
        assertEquals(1, supplier.expireHolds());
        assertEquals(HoldStatus.EXPIRED, h.getStatus());
        assertTrue(seat.isAvailable());
    }

    /**
     * Simulates the row lock of SELECT ... FOR UPDATE: the lock taken in findForUpdate is held until the
     * hold call (the "transaction") returns, so a second caller sees the first one's committed state.
     */
    @Test
    void concurrentDoubleHoldOfOneSeatAllowsExactlyOne() throws Exception {
        ReentrantLock rowLock = new ReentrantLock();
        when(seats.findForUpdate("TK100", "12A")).thenAnswer(i -> {
            rowLock.lock();
            return Optional.of(seat);
        });
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        String offerId = offer("150.00");
        for (int t = 0; t < threads; t++) {
            String key = "key-" + t;
            fs.add(pool.submit(() -> {
                start.await();
                try {
                    supplier.hold(cmd(offerId, "12A", key));
                    ok.incrementAndGet();
                } catch (ConflictException e) {
                    if ("SEAT_ALREADY_RESERVED".equals(e.getCode())) conflict.incrementAndGet();
                } finally {
                    while (rowLock.isHeldByCurrentThread()) rowLock.unlock();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(1, ok.get());
        assertEquals(threads - 1, conflict.get());
    }

    private SeatHold heldHold() {
        SeatHold h = new SeatHold();
        h.setId(UUID.randomUUID());
        h.setSeatId(seat.getId());
        h.setFlightNumber("TK100");
        h.setSeatNumber("12A");
        h.setCabin("ECONOMY");
        h.setCurrency("TRY");
        h.setTotalAmount(new BigDecimal("150.00"));
        h.setStatus(HoldStatus.HELD);
        h.setHoldExpiresAt(NOW.plusSeconds(600));
        return h;
    }
}
