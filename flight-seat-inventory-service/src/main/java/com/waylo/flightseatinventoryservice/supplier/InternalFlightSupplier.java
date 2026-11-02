package com.waylo.flightseatinventoryservice.supplier;

import com.waylo.flightseatinventoryservice.client.FlightClient;
import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.domain.HoldStatus;
import com.waylo.flightseatinventoryservice.domain.SeatHold;
import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.FlightSummary;
import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.ConflictException;
import com.waylo.flightseatinventoryservice.exception.ResourceNotFoundException;
import com.waylo.flightseatinventoryservice.exception.ServiceUnavailableException;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.repository.SeatHoldRepository;
import com.waylo.flightseatinventoryservice.supplier.SupplierDtos.*;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Sells admin-created flights from flight-service using seat prices from this service. */
@Service
public class InternalFlightSupplier implements FlightSupplier {

    public static final String CODE = "INTERNAL";
    private static final String CURRENCY = "TRY";
    private static final Pattern SEAT_NUMBER = Pattern.compile("^[0-9]{1,3}[A-K]$");
    private static final String PNR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FlightSeatRepository seats;
    private final SeatHoldRepository holds;
    private final FlightClient flightClient;
    private final OfferCodec offerCodec;
    private final Clock clock;
    private final long offerTtlMinutes;
    private final long holdTtlMinutes;
    private final long salesCutoffMinutes;

    public InternalFlightSupplier(FlightSeatRepository seats, SeatHoldRepository holds, FlightClient flightClient,
                                  OfferCodec offerCodec, Clock clock,
                                  @Value("${waylo.flight.offer-ttl-minutes:10}") long offerTtlMinutes,
                                  @Value("${waylo.flight.hold-ttl-minutes:15}") long holdTtlMinutes,
                                  @Value("${waylo.flight.sales-cutoff-minutes:120}") long salesCutoffMinutes) {
        this.seats = seats;
        this.holds = holds;
        this.flightClient = flightClient;
        this.offerCodec = offerCodec;
        this.clock = clock;
        this.offerTtlMinutes = offerTtlMinutes;
        this.holdTtlMinutes = holdTtlMinutes;
        this.salesCutoffMinutes = salesCutoffMinutes;
    }

    @Override
    public String code() { return CODE; }

    // ---------- offers ----------

    @Override
    @Transactional(readOnly = true)
    public List<FlightOffer> searchOffers(OfferSearch search) {
        if (search.adults() != 1) {
            throw new BusinessRuleException("PASSENGER_COUNT_UNSUPPORTED", "Only one passenger per order is supported");
        }
        Instant now = clock.instant();
        List<FlightSummary> flights;
        try {
            ApiResponse<List<FlightSummary>> r = flightClient.search(search.from(), search.to(), search.date().toString());
            flights = r == null || r.data() == null ? List.of() : r.data();
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Flight service unavailable", e);
        }
        List<FlightOffer> offers = new ArrayList<>();
        for (FlightSummary f : flights) {
            if (!sellable(f, now)) continue;
            Map<String, List<FlightSeat>> byCabin = new TreeMap<>();
            for (FlightSeat s : seats.findAvailable(f.flightNumber(), now)) {
                byCabin.computeIfAbsent(s.getSeatType(), k -> new ArrayList<>()).add(s);
            }
            for (Map.Entry<String, List<FlightSeat>> e : byCabin.entrySet()) {
                BigDecimal min = e.getValue().stream().map(FlightSeat::getPrice).min(Comparator.naturalOrder()).orElseThrow();
                offers.add(toOffer(f, e.getKey(), money(min), e.getValue().size(), now));
            }
        }
        offers.sort(Comparator.comparing(FlightOffer::departureTime).thenComparing(FlightOffer::totalAmount));
        return offers;
    }

    @Override
    @Transactional(readOnly = true)
    public FlightOffer getOffer(String offerId) {
        Instant now = clock.instant();
        OfferCodec.Decoded d = offerCodec.decode(offerId, now);
        FlightSummary f = loadFlight(d.flightNumber());
        assertSellable(f, now);
        List<FlightSeat> free = seats.findAvailable(d.flightNumber(), now).stream()
                .filter(s -> s.getSeatType().equals(d.cabin())).toList();
        if (free.isEmpty()) {
            throw new ConflictException("NO_SEAT_AVAILABLE", "No seat left in this cabin");
        }
        BigDecimal min = money(free.stream().map(FlightSeat::getPrice).min(Comparator.naturalOrder()).orElseThrow());
        if (min.compareTo(d.price()) != 0) {
            throw new ConflictException("FARE_CHANGED", "Fare changed to " + min + " " + CURRENCY);
        }
        return toOffer(f, d.cabin(), min, free.size(), now);
    }

    private FlightOffer toOffer(FlightSummary f, String cabin, BigDecimal price, int available, Instant now) {
        Instant expires = now.plus(Duration.ofMinutes(offerTtlMinutes));
        String id = offerCodec.encode(f.flightNumber(), cabin, price, CURRENCY, expires);
        return new FlightOffer(id, CODE, f.flightNumber(), f.airline(), f.departureAirportCode(),
                f.arrivalAirportCode(), f.departureTime(), f.arrivalTime(), cabin, CURRENCY, price, available, expires);
    }

    // ---------- hold ----------

    @Override
    @Transactional
    public FlightHold hold(HoldCommand c) {
        if (c.idempotencyKey() == null || c.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        var existing = holds.findByIdempotencyKey(c.idempotencyKey());
        if (existing.isPresent()) {
            return toHold(existing.get());
        }
        Instant now = clock.instant();
        OfferCodec.Decoded offer = offerCodec.decode(c.offerId(), now);
        FlightSummary f = loadFlight(offer.flightNumber());
        assertSellable(f, now);

        FlightSeat seat = lockSeat(offer, c.seatNumber(), now);
        // an unswept expired hold on this seat must not block the unique live-seat index
        for (SeatHold old : holds.findBySeatIdAndStatus(seat.getId(), HoldStatus.HELD)) {
            if (old.getHoldExpiresAt().isBefore(now)) {
                old.setStatus(HoldStatus.EXPIRED);
            }
        }
        holds.flush();

        BigDecimal price = money(seat.getPrice());
        if (price.compareTo(offer.price()) != 0) {
            throw new ConflictException("FARE_CHANGED", "Fare changed to " + price + " " + CURRENCY);
        }
        String reservationId = UUID.randomUUID().toString();
        Instant expires = now.plus(Duration.ofMinutes(holdTtlMinutes));
        seat.setAvailable(false);
        seat.setReservedBy(c.principalUserId());
        seat.setReservationId(reservationId);
        seat.setHoldExpiresAt(expires);
        seats.saveAndFlush(seat);

        SeatHold h = new SeatHold();
        h.setId(UUID.fromString(reservationId));
        h.setIdempotencyKey(c.idempotencyKey());
        h.setOrderCorrelationId(c.orderCorrelationId());
        h.setPrincipalUserId(c.principalUserId());
        h.setSeatId(seat.getId());
        h.setFlightNumber(f.flightNumber());
        h.setSeatNumber(seat.getSeatNumber());
        h.setCabin(seat.getSeatType());
        h.setPassengerFirstName(c.passengerFirstName().trim());
        h.setPassengerLastName(c.passengerLastName().trim());
        h.setCurrency(CURRENCY);
        h.setTotalAmount(price);
        h.setDepartureTime(f.departureTime());
        h.setArrivalTime(f.arrivalTime());
        h.setDepartureAirport(f.departureAirportCode());
        h.setArrivalAirport(f.arrivalAirportCode());
        h.setAirline(f.airline());
        h.setStatus(HoldStatus.HELD);
        h.setHoldExpiresAt(expires);
        try {
            return toHold(holds.saveAndFlush(h));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("SEAT_ALREADY_RESERVED", "Seat was taken by another customer");
        }
    }

    private FlightSeat lockSeat(OfferCodec.Decoded offer, String seatNumber, Instant now) {
        if (seatNumber == null || seatNumber.isBlank()) {
            return seats.lockCheapestFree(offer.flightNumber(), offer.cabin(), now)
                    .orElseThrow(() -> new ConflictException("NO_SEAT_AVAILABLE", "No seat left in this cabin"));
        }
        String number = seatNumber.trim().toUpperCase(Locale.ROOT);
        if (!SEAT_NUMBER.matcher(number).matches()) {
            throw new IllegalArgumentException("Seat number must match ^[0-9]{1,3}[A-K]$");
        }
        FlightSeat seat = seats.findForUpdate(offer.flightNumber(), number)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + number + " not found for flight " + offer.flightNumber()));
        if (!seat.getSeatType().equals(offer.cabin())) {
            throw new BusinessRuleException("SEAT_CABIN_MISMATCH", "Seat " + number + " is not in the offered cabin");
        }
        boolean free = !seat.isClosed() && (seat.isAvailable()
                || (seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now)));
        if (!free) {
            throw new ConflictException("SEAT_ALREADY_RESERVED", "Seat " + number + " is already reserved");
        }
        return seat;
    }

    // ---------- lifecycle ----------

    @Override
    @Transactional(readOnly = true)
    public FlightHold getHold(UUID holdId) {
        return toHold(holds.findById(holdId).orElseThrow(() -> notFound(holdId)));
    }

    /** For INTERNAL the ticket (PNR, e-ticket number) is issued at confirm, i.e. when the order is paid. */
    @Override
    @Transactional
    public FlightHold issueTicket(UUID holdId) {
        SeatHold h = holds.findByIdForUpdate(holdId).orElseThrow(() -> notFound(holdId));
        if (h.getStatus() == HoldStatus.CONFIRMED) {
            return toHold(h);
        }
        Instant now = clock.instant();
        if (h.getStatus() != HoldStatus.HELD || h.getHoldExpiresAt().isBefore(now)) {
            throw new ConflictException("HOLD_EXPIRED", "Hold expired or cancelled");
        }
        FlightSeat seat = seats.findByIdForUpdate(h.getSeatId()).orElseThrow(() -> notFound(holdId));
        seat.setHoldExpiresAt(null); // permanent: not free, no expiry
        seats.saveAndFlush(seat);
        h.setStatus(HoldStatus.CONFIRMED);
        h.setPnr(randomCode(6));
        h.setTicketNumber("WL" + randomDigits(11));
        return toHold(holds.saveAndFlush(h));
    }

    @Override
    @Transactional
    public FlightHold cancel(UUID holdId) {
        SeatHold h = holds.findByIdForUpdate(holdId).orElseThrow(() -> notFound(holdId));
        if (h.getStatus() == HoldStatus.CANCELLED || h.getStatus() == HoldStatus.EXPIRED) {
            return toHold(h);
        }
        seats.findByIdForUpdate(h.getSeatId()).ifPresent(seat -> {
            if (h.getId().toString().equals(seat.getReservationId())) {
                seat.setAvailable(true);
                seat.setReservedBy(null);
                seat.setReservationId(null);
                seat.setHoldExpiresAt(null);
                seats.saveAndFlush(seat);
            }
        });
        h.setStatus(HoldStatus.CANCELLED);
        return toHold(holds.saveAndFlush(h));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundQuote refundQuote(UUID holdId) {
        SeatHold h = holds.findById(holdId).orElseThrow(() -> notFound(holdId));
        BigDecimal customer = h.getStatus() == HoldStatus.CONFIRMED || h.getStatus() == HoldStatus.HELD
                ? h.getTotalAmount() : BigDecimal.ZERO.setScale(2);
        return new RefundQuote(h.getId(), h.getCurrency(), customer, BigDecimal.ZERO.setScale(2),
                "INTERNAL_FULL_REFUND_BEFORE_SCAN");
    }

    /** Marks expired HELD holds EXPIRED and frees their seats. Returns how many were expired. */
    @Transactional
    public int expireHolds() {
        Instant now = clock.instant();
        int n = 0;
        for (SeatHold h : holds.findByStatusAndHoldExpiresAtBefore(HoldStatus.HELD, now)) {
            seats.findByIdForUpdate(h.getSeatId()).ifPresent(seat -> {
                if (h.getId().toString().equals(seat.getReservationId())) {
                    seat.setAvailable(true);
                    seat.setReservedBy(null);
                    seat.setReservationId(null);
                    seat.setHoldExpiresAt(null);
                    seats.save(seat);
                }
            });
            h.setStatus(HoldStatus.EXPIRED);
            n++;
        }
        return n;
    }

    // ---------- helpers ----------

    private FlightSummary loadFlight(String flightNumber) {
        try {
            ApiResponse<FlightSummary> r = flightClient.getSummary(flightNumber);
            if (r == null || r.data() == null) throw new ServiceUnavailableException("Flight service returned no data", null);
            return r.data();
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Flight " + flightNumber + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Flight service unavailable", e);
        }
    }

    private boolean sellable(FlightSummary f, Instant now) {
        return "SCHEDULED".equals(f.status()) && f.departureTime() != null
                && f.departureTime().isAfter(now.plus(Duration.ofMinutes(salesCutoffMinutes)));
    }

    private void assertSellable(FlightSummary f, Instant now) {
        if (!sellable(f, now)) {
            throw new BusinessRuleException("FLIGHT_NOT_BOOKABLE", "Flight " + f.flightNumber() + " is not open for booking");
        }
    }

    private FlightHold toHold(SeatHold h) {
        return new FlightHold(h.getId(), CODE, h.getFlightNumber(), h.getAirline(), h.getDepartureAirport(),
                h.getArrivalAirport(), h.getDepartureTime(), h.getArrivalTime(), h.getCabin(), h.getSeatNumber(),
                h.getPassengerFirstName(), h.getPassengerLastName(), h.getCurrency(), h.getTotalAmount(),
                BigDecimal.ZERO.setScale(2), h.getStatus().name(), h.getHoldExpiresAt(), h.getPnr(), h.getTicketNumber());
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Flight hold " + id + " not found");
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN); }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(PNR_ALPHABET.charAt(RANDOM.nextInt(PNR_ALPHABET.length())));
        return sb.toString();
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
