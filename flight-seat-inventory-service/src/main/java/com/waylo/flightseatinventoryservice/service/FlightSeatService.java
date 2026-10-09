package com.waylo.flightseatinventoryservice.service;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.dto.FlightSeatMapper;
import com.waylo.flightseatinventoryservice.dto.FlightSeatRequest;
import com.waylo.flightseatinventoryservice.dto.FlightSeatResponse;
import com.waylo.flightseatinventoryservice.dto.SeatReservationResponse;
import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.DuplicateResourceException;
import com.waylo.flightseatinventoryservice.exception.ResourceNotFoundException;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FlightSeatService {

    private static final Logger log = LoggerFactory.getLogger(FlightSeatService.class);

    private final FlightSeatRepository flightSeatRepository;
    private final FlightSeatMapper flightSeatMapper;

    public FlightSeatService(FlightSeatRepository flightSeatRepository,
                             FlightSeatMapper flightSeatMapper) {
        this.flightSeatRepository = flightSeatRepository;
        this.flightSeatMapper = flightSeatMapper;
    }

    @Transactional
    public FlightSeatResponse createFlightSeat(FlightSeatRequest request) {
        if (flightSeatRepository.existsByFlightNumberAndSeatNumber(
                request.flightNumber(), request.seatNumber())) {
            throw new DuplicateResourceException(
                    "FlightSeat", "flightNumber+seatNumber",
                    request.flightNumber() + "/" + request.seatNumber());
        }

        FlightSeat seat = flightSeatMapper.toEntity(request);
        FlightSeat saved = flightSeatRepository.save(seat);
        log.info("Seat created: flight={}, seat={}", saved.getFlightNumber(), saved.getSeatNumber());
        return flightSeatMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FlightSeatResponse> getFlightSeats(String flightNumber) {
        return flightSeatRepository.findByFlightNumber(flightNumber)
                .stream()
                .map(flightSeatMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FlightSeatResponse> getAvailableSeats(String flightNumber) {
        return flightSeatRepository.findByFlightNumberAndAvailableTrue(flightNumber)
                .stream()
                .map(flightSeatMapper::toResponse)
                .toList();
    }

    @Transactional
    public SeatReservationResponse reserveSeat(String flightNumber, String seatNumber) {
        FlightSeat seat = flightSeatRepository.findByFlightNumberAndSeatNumber(flightNumber, seatNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatNumber + " not found for flight " + flightNumber));

        if (!seat.isAvailable()) {
            throw new BusinessRuleException("SEAT_ALREADY_RESERVED",
                    "Seat " + seatNumber + " is already reserved");
        }

        String reservationId = UUID.randomUUID().toString();
        seat.setAvailable(false);
        seat.setReservationId(reservationId);
        flightSeatRepository.save(seat);

        log.info("Seat reserved: flight={}, seat={}, reservationId={}",
                flightNumber, seatNumber, reservationId);

        return new SeatReservationResponse(
                flightNumber, seatNumber, reservationId, seat.getPrice(), Instant.now());
    }

    @Transactional
    public void cancelReservation(String flightNumber, String seatNumber) {
        FlightSeat seat = flightSeatRepository.findByFlightNumberAndSeatNumber(flightNumber, seatNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatNumber + " not found for flight " + flightNumber));

        if (seat.isAvailable()) {
            throw new BusinessRuleException("SEAT_NOT_RESERVED",
                    "Seat " + seatNumber + " is not reserved");
        }

        log.info("Reservation cancelled: flight={}, seat={}, oldReservationId={}",
                flightNumber, seatNumber, seat.getReservationId());

        seat.setAvailable(true);
        seat.setReservationId(null);
        flightSeatRepository.save(seat);
    }

    @Transactional(readOnly = true)
    public FlightSeatResponse getFlightSeat(String flightNumber, String seatNumber) {
        FlightSeat seat = flightSeatRepository.findByFlightNumberAndSeatNumber(flightNumber, seatNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatNumber + " not found for flight " + flightNumber));
        return flightSeatMapper.toResponse(seat);
    }

    @Transactional(readOnly = true)
    public long getAvailableSeatCount(String flightNumber) {
        return flightSeatRepository.countByFlightNumberAndAvailableTrue(flightNumber);
    }
}
