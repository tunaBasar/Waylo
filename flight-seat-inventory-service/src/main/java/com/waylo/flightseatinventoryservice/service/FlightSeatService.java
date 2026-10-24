package com.waylo.flightseatinventoryservice.service;

import com.waylo.flightseatinventoryservice.client.FlightClient;
import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.domain.SeatType;
import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.BulkSeatRequest;
import com.waylo.flightseatinventoryservice.dto.BulkSeatResponse;
import com.waylo.flightseatinventoryservice.dto.FlightInfo;
import com.waylo.flightseatinventoryservice.dto.FlightSeatRequest;
import com.waylo.flightseatinventoryservice.dto.FlightSeatResponse;
import com.waylo.flightseatinventoryservice.dto.SeatReservationResponse;
import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.ConflictException;
import com.waylo.flightseatinventoryservice.exception.DuplicateResourceException;
import com.waylo.flightseatinventoryservice.exception.ForbiddenException;
import com.waylo.flightseatinventoryservice.exception.ResourceNotFoundException;
import com.waylo.flightseatinventoryservice.exception.ServiceUnavailableException;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FlightSeatService {

    private static final Logger log = LoggerFactory.getLogger(FlightSeatService.class);
    private static final Pattern SEAT_NUMBER = Pattern.compile("^[0-9]{1,3}[A-K]$");
    static final int MAX_BULK_SEATS = 600;

    private final FlightSeatRepository flightSeatRepository;
    private final FlightClient flightClient;
    private final long holdMinutes;

    public FlightSeatService(FlightSeatRepository flightSeatRepository,
                             FlightClient flightClient,
                             @Value("${waylo.seat-hold.minutes:15}") long holdMinutes) {
        this.flightSeatRepository = flightSeatRepository;
        this.flightClient = flightClient;
        this.holdMinutes = holdMinutes;
    }

    // ---------- helpers ----------

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String validSeatNumber(String raw) {
        String seatNumber = normalize(raw);
        if (!SEAT_NUMBER.matcher(seatNumber).matches()) {
            throw new IllegalArgumentException("Seat number must match ^[0-9]{1,3}[A-K]$");
        }
        return seatNumber;
    }

    private static String validSeatType(String raw) {
        try {
            return SeatType.valueOf(normalize(raw)).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Seat type must be one of ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST");
        }
    }

    /** Held by someone and not yet expired. Legacy held rows without expiry count as active. */
    private static boolean hasActiveHold(FlightSeat seat, Instant now) {
        return !seat.isAvailable()
                && (seat.getHoldExpiresAt() == null || !seat.getHoldExpiresAt().isBefore(now));
    }

    private static boolean isFree(FlightSeat seat, Instant now) {
        return seat.isAvailable()
                || (seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now));
    }

    private FlightSeatResponse toResponse(FlightSeat seat, Instant now) {
        return new FlightSeatResponse(seat.getId(), seat.getFlightNumber(), seat.getSeatNumber(),
                seat.getSeatType(), seat.getPrice(), isFree(seat, now),
                seat.getCreatedAt(), seat.getUpdatedAt());
    }

    private FlightSeat newSeat(String flightNumber, String seatNumber, String seatType,
                               java.math.BigDecimal price) {
        FlightSeat seat = new FlightSeat();
        seat.setFlightNumber(flightNumber);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(seatType);
        seat.setPrice(price);
        seat.setAvailable(true);
        return seat;
    }

    private FlightSeat findSeat(String flightNumber, String seatNumber) {
        return flightSeatRepository.findByFlightNumberAndSeatNumber(flightNumber, seatNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatNumber + " not found for flight " + flightNumber));
    }

    // ---------- admin ----------

    @Transactional
    public FlightSeatResponse createFlightSeat(FlightSeatRequest request) {
        String flightNumber = normalize(request.flightNumber());
        String seatNumber = validSeatNumber(request.seatNumber());
        String seatType = validSeatType(request.seatType());

        if (flightSeatRepository.existsByFlightNumberAndSeatNumber(flightNumber, seatNumber)) {
            throw new DuplicateResourceException("FlightSeat", "flightNumber+seatNumber",
                    flightNumber + "/" + seatNumber);
        }

        FlightSeat saved = flightSeatRepository.save(
                newSeat(flightNumber, seatNumber, seatType, request.price()));
        log.info("Seat created: flight={}, seat={}", flightNumber, seatNumber);
        return toResponse(saved, Instant.now());
    }

    @Transactional
    public BulkSeatResponse createSeatsBulk(BulkSeatRequest request) {
        String flightNumber = normalize(request.flightNumber());
        String seatType = validSeatType(request.seatType());
        int from = request.fromRow();
        int to = request.toRow();
        if (from < 1 || to > 999 || from > to) {
            throw new IllegalArgumentException("Rows must satisfy 1 <= fromRow <= toRow <= 999");
        }

        String letters = normalize(request.letters());
        Set<Character> distinct = new HashSet<>();
        for (char c : letters.toCharArray()) {
            if (c < 'A' || c > 'K' || !distinct.add(c)) {
                throw new IllegalArgumentException("letters must be distinct characters between A and K");
            }
        }

        long total = (long) (to - from + 1) * letters.length();
        if (total > MAX_BULK_SEATS) {
            throw new IllegalArgumentException("Cannot create more than " + MAX_BULK_SEATS + " seats per call");
        }

        Set<String> existing = new HashSet<>();
        flightSeatRepository.findByFlightNumber(flightNumber)
                .forEach(s -> existing.add(s.getSeatNumber()));

        List<FlightSeat> seats = new ArrayList<>();
        for (int row = from; row <= to; row++) {
            for (char letter : letters.toCharArray()) {
                String seatNumber = row + String.valueOf(letter);
                if (existing.contains(seatNumber)) {
                    throw new DuplicateResourceException("FlightSeat", "flightNumber+seatNumber",
                            flightNumber + "/" + seatNumber);
                }
                seats.add(newSeat(flightNumber, seatNumber, seatType, request.price()));
            }
        }

        flightSeatRepository.saveAll(seats);
        log.info("Bulk seats created: flight={}, count={}", flightNumber, seats.size());
        return new BulkSeatResponse(flightNumber, seats.size());
    }

    @Transactional
    public void deleteSeat(String rawFlightNumber, String rawSeatNumber) {
        String flightNumber = normalize(rawFlightNumber);
        String seatNumber = normalize(rawSeatNumber);
        FlightSeat seat = findSeat(flightNumber, seatNumber);
        if (hasActiveHold(seat, Instant.now())) {
            throw new BusinessRuleException("SEAT_HELD", "Seat " + seatNumber + " has an active hold");
        }
        flightSeatRepository.delete(seat);
        log.info("Seat deleted: flight={}, seat={}", flightNumber, seatNumber);
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<FlightSeatResponse> getFlightSeats(String flightNumber) {
        Instant now = Instant.now();
        return flightSeatRepository.findByFlightNumber(normalize(flightNumber)).stream()
                .map(s -> toResponse(s, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FlightSeatResponse> getAvailableSeats(String flightNumber) {
        Instant now = Instant.now();
        return flightSeatRepository.findAvailable(normalize(flightNumber), now).stream()
                .map(s -> toResponse(s, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public FlightSeatResponse getFlightSeat(String flightNumber, String seatNumber) {
        return toResponse(findSeat(normalize(flightNumber), normalize(seatNumber)), Instant.now());
    }

    @Transactional(readOnly = true)
    public long getAvailableSeatCount(String flightNumber) {
        return flightSeatRepository.countAvailable(normalize(flightNumber), Instant.now());
    }

    // ---------- holds ----------

    private void assertFlightBookable(String flightNumber, Instant now) {
        FlightInfo flight;
        try {
            ApiResponse<FlightInfo> response = flightClient.getByFlightNumber(flightNumber);
            flight = response == null ? null : response.data();
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Flight " + flightNumber + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Flight service unavailable", e);
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("Flight service unavailable", e);
        }
        if (flight == null) {
            throw new ServiceUnavailableException("Flight service returned no data", null);
        }
        if (!"SCHEDULED".equals(flight.status())
                || flight.departureTime() == null || !flight.departureTime().isAfter(now)) {
            throw new BusinessRuleException("FLIGHT_NOT_BOOKABLE",
                    "Flight " + flightNumber + " is not open for booking");
        }
    }

    @Transactional
    public SeatReservationResponse reserveSeat(String rawFlightNumber, String rawSeatNumber, UUID userId) {
        String flightNumber = normalize(rawFlightNumber);
        String seatNumber = validSeatNumber(rawSeatNumber);
        Instant now = Instant.now();

        assertFlightBookable(flightNumber, now);

        FlightSeat seat = findSeat(flightNumber, seatNumber);
        if (!isFree(seat, now)) {
            throw new ConflictException("SEAT_ALREADY_RESERVED", "Seat " + seatNumber + " is already reserved");
        }

        String reservationId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(Duration.ofMinutes(holdMinutes));
        seat.setAvailable(false);
        seat.setReservedBy(userId);
        seat.setReservationId(reservationId);
        seat.setHoldExpiresAt(expiresAt);
        flightSeatRepository.saveAndFlush(seat);

        log.info("Seat reserved: flight={}, seat={}, reservationId={}, expiresAt={}",
                flightNumber, seatNumber, reservationId, expiresAt);

        return new SeatReservationResponse(flightNumber, seatNumber, reservationId,
                seat.getPrice(), now, expiresAt);
    }

    @Transactional
    public void cancelReservation(String rawFlightNumber, String rawSeatNumber, UUID userId, boolean admin) {
        String flightNumber = normalize(rawFlightNumber);
        String seatNumber = validSeatNumber(rawSeatNumber);
        FlightSeat seat = findSeat(flightNumber, seatNumber);

        if (!hasActiveHold(seat, Instant.now())) {
            throw new BusinessRuleException("SEAT_NOT_RESERVED", "Seat " + seatNumber + " is not reserved");
        }
        if (!admin && !userId.equals(seat.getReservedBy())) {
            throw new ForbiddenException("Seat " + seatNumber + " is held by another user");
        }

        log.info("Reservation cancelled: flight={}, seat={}, oldReservationId={}",
                flightNumber, seatNumber, seat.getReservationId());

        seat.setAvailable(true);
        seat.setReservedBy(null);
        seat.setReservationId(null);
        seat.setHoldExpiresAt(null);
        flightSeatRepository.saveAndFlush(seat);
    }
}
