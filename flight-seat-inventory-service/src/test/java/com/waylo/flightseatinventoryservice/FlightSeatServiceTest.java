package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.dto.FlightSeatMapper;
import com.waylo.flightseatinventoryservice.dto.FlightSeatRequest;
import com.waylo.flightseatinventoryservice.dto.FlightSeatResponse;
import com.waylo.flightseatinventoryservice.dto.SeatReservationResponse;
import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.DuplicateResourceException;
import com.waylo.flightseatinventoryservice.exception.ResourceNotFoundException;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlightSeatService.
 */
@ExtendWith(MockitoExtension.class)
class FlightSeatServiceTest {

    @Mock
    private FlightSeatRepository flightSeatRepository;

    @Mock
    private FlightSeatMapper flightSeatMapper;

    @InjectMocks
    private FlightSeatService flightSeatService;

    private static final UUID TEST_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.now();

    // ========== HELPERS ==========

    private FlightSeat buildSeat(String flightNumber, String seatNumber, boolean available) {
        FlightSeat seat = new FlightSeat();
        seat.setId(TEST_UUID);
        seat.setFlightNumber(flightNumber);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType("Economy");
        seat.setPrice(new BigDecimal("150.00"));
        seat.setAvailable(available);
        seat.setReservationId(available ? null : "res-001");
        // createdAt/updatedAt are set by Hibernate @CreationTimestamp / @UpdateTimestamp
        // only getters exist, no setters on the entity.
        return seat;
    }

    private FlightSeatResponse buildResponse(String flightNumber, String seatNumber, boolean available,
                                              String reservationId) {
        return new FlightSeatResponse(TEST_UUID, flightNumber, seatNumber, "Economy",
                new BigDecimal("150.00"), available, reservationId, NOW, NOW);
    }

    // ========== CREATE FLIGHT SEAT ==========

    @Test
    void createFlightSeat_success() {
        // Arrange
        FlightSeatRequest request = new FlightSeatRequest("TK100", "A1", "Economy", new BigDecimal("150.00"));
        FlightSeat entity = buildSeat("TK100", "A1", true);
        FlightSeatResponse response = buildResponse("TK100", "A1", true, null);

        when(flightSeatRepository.existsByFlightNumberAndSeatNumber("TK100", "A1")).thenReturn(false);
        when(flightSeatMapper.toEntity(request)).thenReturn(entity);
        when(flightSeatRepository.save(entity)).thenReturn(entity);
        when(flightSeatMapper.toResponse(entity)).thenReturn(response);

        // Act
        FlightSeatResponse result = flightSeatService.createFlightSeat(request);

        // Assert
        assertNotNull(result);
        assertEquals("TK100", result.flightNumber());
        assertEquals("A1", result.seatNumber());
        assertTrue(result.available());
        verify(flightSeatRepository).save(entity);
    }

    @Test
    void createFlightSeat_duplicate_throws() {
        // Arrange
        FlightSeatRequest request = new FlightSeatRequest("TK100", "A1", "Economy", new BigDecimal("150.00"));

        when(flightSeatRepository.existsByFlightNumberAndSeatNumber("TK100", "A1")).thenReturn(true);

        // Act & Assert
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> flightSeatService.createFlightSeat(request));
        assertTrue(ex.getMessage().contains("TK100/A1"));
        verify(flightSeatRepository, never()).save(any());
        verify(flightSeatMapper, never()).toEntity(any());
    }

    // ========== GET FLIGHT SEATS ==========

    @Test
    void getFlightSeats_found() {
        // Arrange
        when(flightSeatRepository.findByFlightNumber("TK100"))
                .thenReturn(List.of(buildSeat("TK100", "A1", true), buildSeat("TK100", "A2", false)));
        when(flightSeatMapper.toResponse(any(FlightSeat.class)))
                .thenAnswer(invocation -> {
                    FlightSeat s = invocation.getArgument(0);
                    return new FlightSeatResponse(s.getId(), s.getFlightNumber(), s.getSeatNumber(),
                            s.getSeatType(), s.getPrice(), s.isAvailable(), s.getReservationId(),
                            NOW, NOW);
                });

        // Act
        List<FlightSeatResponse> results = flightSeatService.getFlightSeats("TK100");

        // Assert
        assertEquals(2, results.size());
    }

    @Test
    void getFlightSeats_empty() {
        when(flightSeatRepository.findByFlightNumber("NONEXIST")).thenReturn(List.of());

        List<FlightSeatResponse> results = flightSeatService.getFlightSeats("NONEXIST");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ========== GET AVAILABLE SEATS ==========

    @Test
    void getAvailableSeats_found() {
        when(flightSeatRepository.findByFlightNumberAndAvailableTrue("TK100"))
                .thenReturn(List.of(buildSeat("TK100", "A1", true)));
        when(flightSeatMapper.toResponse(any(FlightSeat.class)))
                .thenAnswer(invocation -> {
                    FlightSeat s = invocation.getArgument(0);
                    return new FlightSeatResponse(s.getId(), s.getFlightNumber(), s.getSeatNumber(),
                            s.getSeatType(), s.getPrice(), s.isAvailable(), s.getReservationId(),
                            NOW, NOW);
                });

        List<FlightSeatResponse> results = flightSeatService.getAvailableSeats("TK100");

        assertEquals(1, results.size());
        assertTrue(results.get(0).available());
    }

    @Test
    void getAvailableSeats_empty() {
        when(flightSeatRepository.findByFlightNumberAndAvailableTrue("TK100")).thenReturn(List.of());

        List<FlightSeatResponse> results = flightSeatService.getAvailableSeats("TK100");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ========== RESERVE SEAT ==========

    @Test
    void reserveSeat_success() {
        // Arrange
        FlightSeat seat = buildSeat("TK100", "A1", true);

        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "A1"))
                .thenReturn(Optional.of(seat));
        when(flightSeatRepository.save(seat)).thenReturn(seat);

        // Act
        SeatReservationResponse result = flightSeatService.reserveSeat("TK100", "A1");

        // Assert
        assertNotNull(result);
        assertEquals("TK100", result.flightNumber());
        assertEquals("A1", result.seatNumber());
        assertNotNull(result.reservationId());
        assertEquals(new BigDecimal("150.00"), result.price());
        // reservedAt is generated at call time, check it's close to now
        assertEquals(NOW.getEpochSecond(), result.reservedAt().getEpochSecond(), 60);

        // Verify side effects on entity
        assertFalse(seat.isAvailable());
        assertNotNull(seat.getReservationId());
        verify(flightSeatRepository).save(seat);
    }

    @Test
    void reserveSeat_seatNotFound_throws() {
        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "Z9"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> flightSeatService.reserveSeat("TK100", "Z9"));
    }

    @Test
    void reserveSeat_alreadyReserved_throws() {
        // Arrange: seat is already reserved (not available)
        FlightSeat seat = buildSeat("TK100", "A1", false);
        seat.setReservationId("existing-res");

        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "A1"))
                .thenReturn(Optional.of(seat));

        assertThrows(BusinessRuleException.class,
                () -> flightSeatService.reserveSeat("TK100", "A1"));
        verify(flightSeatRepository, never()).save(any());
    }

    // ========== CANCEL RESERVATION ==========

    @Test
    void cancelReservation_success() {
        // Arrange: seat is reserved
        FlightSeat seat = buildSeat("TK100", "A1", false);
        seat.setReservationId("res-001");

        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "A1"))
                .thenReturn(Optional.of(seat));
        when(flightSeatRepository.save(seat)).thenReturn(seat);

        // Act
        flightSeatService.cancelReservation("TK100", "A1");

        // Assert
        assertTrue(seat.isAvailable());
        assertNull(seat.getReservationId());
        verify(flightSeatRepository).save(seat);
    }

    @Test
    void cancelReservation_seatNotFound_throws() {
        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "Z9"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> flightSeatService.cancelReservation("TK100", "Z9"));
    }

    @Test
    void cancelReservation_notReserved_throws() {
        // Arrange: seat is already available (never reserved)
        FlightSeat seat = buildSeat("TK100", "A1", true);

        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "A1"))
                .thenReturn(Optional.of(seat));

        assertThrows(BusinessRuleException.class,
                () -> flightSeatService.cancelReservation("TK100", "A1"));
        verify(flightSeatRepository, never()).save(any());
    }

    // ========== GET FLIGHT SEAT (by flight + seat) ==========

    @Test
    void getFlightSeat_found() {
        FlightSeat seat = buildSeat("TK100", "A1", true);
        FlightSeatResponse expected = buildResponse("TK100", "A1", true, null);

        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "A1"))
                .thenReturn(Optional.of(seat));
        when(flightSeatMapper.toResponse(seat)).thenReturn(expected);

        FlightSeatResponse result = flightSeatService.getFlightSeat("TK100", "A1");

        assertNotNull(result);
        assertEquals("TK100", result.flightNumber());
        assertEquals("A1", result.seatNumber());
    }

    @Test
    void getFlightSeat_notFound_throws() {
        when(flightSeatRepository.findByFlightNumberAndSeatNumber("TK100", "Z9"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> flightSeatService.getFlightSeat("TK100", "Z9"));
    }

    // ========== GET AVAILABLE SEAT COUNT ==========

    @Test
    void getAvailableSeatCount_success() {
        when(flightSeatRepository.countByFlightNumberAndAvailableTrue("TK100")).thenReturn(5L);

        long count = flightSeatService.getAvailableSeatCount("TK100");

        assertEquals(5, count);
    }

    @Test
    void getAvailableSeatCount_zero() {
        when(flightSeatRepository.countByFlightNumberAndAvailableTrue("TK100")).thenReturn(0L);

        long count = flightSeatService.getAvailableSeatCount("TK100");

        assertEquals(0, count);
    }
}
