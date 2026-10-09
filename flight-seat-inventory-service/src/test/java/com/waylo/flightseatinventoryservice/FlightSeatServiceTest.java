//qwen
package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.dto.FlightSeatDto;
import com.waylo.flightseatinventoryservice.dto.SeatReservationDto;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FlightSeatService için unit test.
 */
class FlightSeatServiceTest {

    @Mock
    private FlightSeatRepository flightSeatRepository;

    @InjectMocks
    private FlightSeatService flightSeatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateFlightSeat_Success() {
        // Arrange
        String flightNumber = "TK123";
        String seatNumber = "A1";
        String seatType = "Economy";
        BigDecimal price = new BigDecimal("150.00");

        FlightSeat mockSeat = new FlightSeat(flightNumber, seatNumber, seatType, price);
        mockSeat.setId(1L);
        mockSeat.setCreatedAt(LocalDateTime.now());
        mockSeat.setUpdatedAt(LocalDateTime.now());

        when(flightSeatRepository.findByFlightNumberAndSeatNumber(flightNumber, seatNumber))
                .thenReturn(Optional.empty());
        when(flightSeatRepository.save(any(FlightSeat.class))).thenReturn(mockSeat);

        // Act
        FlightSeatDto result = flightSeatService.createFlightSeat(flightNumber, seatNumber, seatType, price);

        // Assert
        assertNotNull(result);
        assertEquals(flightNumber, result.getFlightNumber());
        assertEquals(seatNumber, result.getSeatNumber());
        assertEquals(seatType, result.getSeatType());
        assertEquals(price, result.getPrice());
        assertTrue(result.getAvailable());
    }

    @Test
    void testGetAvailableSeats_Success() {
        // Arrange
        String flightNumber = "TK123";
        FlightSeat seat1 = new FlightSeat(flightNumber, "A1", "Economy", new BigDecimal("150.00"));
        seat1.setId(1L);
        seat1.setCreatedAt(LocalDateTime.now());
        seat1.setUpdatedAt(LocalDateTime.now());
        seat1.setAvailable(true);

        FlightSeat seat2 = new FlightSeat(flightNumber, "A2", "Economy", new BigDecimal("150.00"));
        seat2.setId(2L);
        seat2.setCreatedAt(LocalDateTime.now());
        seat2.setUpdatedAt(LocalDateTime.now());
        seat2.setAvailable(true);

        List<FlightSeat> seats = new ArrayList<>();
        seats.add(seat1);
        seats.add(seat2);

        when(flightSeatRepository.findByFlightNumberAndIsAvailableTrue(flightNumber)).thenReturn(seats);

        // Act
        List<FlightSeatDto> result = flightSeatService.getAvailableSeats(flightNumber);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }
}