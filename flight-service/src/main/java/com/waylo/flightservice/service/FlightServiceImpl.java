package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.domain.Flight;
import com.waylo.flightservice.domain.FlightStatus;
import com.waylo.flightservice.dto.FlightMapper;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.event.FlightCancelledEvent;
import com.waylo.flightservice.event.FlightCreatedEvent;
import com.waylo.flightservice.event.FlightEventPublisher;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class FlightServiceImpl implements FlightService {

    private static final Pattern FLIGHT_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{2}[0-9]{1,4}[A-Z]?$");
    private static final Duration MAX_DURATION = Duration.ofHours(20);

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final FlightMapper flightMapper;
    private final FlightEventPublisher eventPublisher;
    private final Clock clock;

    public FlightServiceImpl(FlightRepository flightRepository,
                             AirportRepository airportRepository,
                             FlightMapper flightMapper,
                             FlightEventPublisher eventPublisher,
                             Clock clock) {
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.flightMapper = flightMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // --- create ---

    @Override
    public FlightResponse create(FlightRequest request) {
        String normalizedFlightNumber = normalize(request.flightNumber());
        String normalizedFrom = normalize(request.departureAirportCode());
        String normalizedTo = normalize(request.arrivalAirportCode());

        validateFlightNumber(normalizedFlightNumber);
        validateSchedule(request.departureTime(), request.arrivalTime(), true);

        if (flightRepository.existsByFlightNumber(normalizedFlightNumber)) {
            throw new DuplicateResourceException("Flight", "flightNumber", normalizedFlightNumber);
        }

        Airport departure = findAirportByCode(normalizedFrom);
        Airport arrival = findAirportByCode(normalizedTo);

        Flight flight = flightMapper.toEntity(request);
        flight.setFlightNumber(normalizedFlightNumber);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);
        flight.setStatus(FlightStatus.SCHEDULED);

        flight = flightRepository.saveAndFlush(flight);
        eventPublisher.publishFlightCreated(new FlightCreatedEvent(
                flight.getId(), flight.getFlightNumber(),
                flight.getDepartureAirport().getIataCode(), flight.getArrivalAirport().getIataCode(),
                flight.getDepartureTime(), flight.getArrivalTime(), flight.getAirline()));
        return flightMapper.toResponse(flight);
    }

    // --- getById ---

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getById(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
        return flightMapper.toResponse(flight);
    }

    // --- getByFlightNumber ---

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getByFlightNumber(String flightNumber) {
        String normalized = normalize(flightNumber);
        Flight flight = flightRepository.findByFlightNumber(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "flightNumber", normalized));
        return flightMapper.toResponse(flight);
    }

    // --- getAll ---

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponse> getAll() {
        return flightRepository.findAllByOrderByDepartureTimeAsc().stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    // --- search ---

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponse> search(String from, String to,
                                       LocalDate date,
                                       Instant departureFrom,
                                       Instant departureTo,
                                       boolean includeCancelled) {
        // Validate from/to are not blank
        if (from != null && from.trim().isEmpty()) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to != null && to.trim().isEmpty()) {
            throw new IllegalArgumentException("from and to are required");
        }

        String normalizedFrom = normalize(from);
        String normalizedTo = normalize(to);

        // from == to after normalization is invalid
        if (normalizedFrom.equals(normalizedTo)) {
            throw new IllegalArgumentException("Departure and arrival airports must differ");
        }

        // Must use exactly one mode: date XOR (departureFrom AND departureTo)
        boolean usingDate = date != null;
        boolean usingRangeFrom = departureFrom != null;
        boolean usingRangeTo = departureTo != null;

        // Reject partial range: exactly one of departureFrom/departureTo given
        if (usingRangeFrom != usingRangeTo) {
            throw new IllegalArgumentException("Both departureFrom and departureTo are required, or neither");
        }

        // Reject date combined with any range param
        if (usingDate && (usingRangeFrom || usingRangeTo)) {
            throw new IllegalArgumentException("Use either date or departureFrom/departureTo, not both");
        }

        if (!usingDate && !usingRangeFrom && !usingRangeTo) {
            throw new IllegalArgumentException("Either date or departureFrom/departureTo is required");
        }

        Instant start;
        Instant end;

        if (usingDate) {
            // Look up departure airport to get timezone
            Airport departureAirport = findAirportByCode(normalizedFrom);
            ZoneId zone = ZoneId.of(departureAirport.getTimezone());
            start = date.atStartOfDay(zone).toInstant();
            end = date.plusDays(1).atStartOfDay(zone).toInstant();
        } else {
            start = departureFrom;
            end = departureTo;
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("departureFrom must be before departureTo");
            }
            // 31-day limit: use Duration.compareTo to avoid toDays() truncation
            if (Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
                throw new IllegalArgumentException("Search range must be at most 31 days");
            }
        }

        return flightRepository.searchSchedule(normalizedFrom, normalizedTo, start, end, includeCancelled, FlightStatus.CANCELLED)
                .stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    // --- update ---

    @Override
    public FlightResponse update(UUID id, FlightRequest request) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

        // Check if flight is cancelled -> reject
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            throw new BusinessRuleException("FLIGHT_CANCELLED", "Cannot update a cancelled flight");
        }

        // If existing departureTime is before now -> reject
        if (flight.getDepartureTime().isBefore(clock.instant())) {
            throw new BusinessRuleException("FLIGHT_DEPARTED", "Cannot update a flight that has already departed");
        }

        // Compute whether departureTime is changing BEFORE the mapper mutates the entity
        boolean departureTimeChanged = !request.departureTime().equals(flight.getDepartureTime());

        String normalizedFlightNumber = normalize(request.flightNumber());
        String normalizedFrom = normalize(request.departureAirportCode());
        String normalizedTo = normalize(request.arrivalAirportCode());

        validateFlightNumber(normalizedFlightNumber);
        validateSchedule(request.departureTime(), request.arrivalTime(), departureTimeChanged);

        // If flight number changed and another flight has it -> duplicate
        if (!flight.getFlightNumber().equals(normalizedFlightNumber)) {
            if (flightRepository.existsByFlightNumber(normalizedFlightNumber)) {
                throw new DuplicateResourceException("Flight", "flightNumber", normalizedFlightNumber);
            }
        }

        Airport departure = findAirportByCode(normalizedFrom);
        Airport arrival = findAirportByCode(normalizedTo);

        // Call updateEntity FIRST, then override flightNumber and airports with normalized values
        // so the mapper does not overwrite them with raw request data
        flightMapper.updateEntity(request, flight);
        flight.setFlightNumber(normalizedFlightNumber);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);

        flight = flightRepository.saveAndFlush(flight);
        return flightMapper.toResponse(flight);
    }

    // --- delete ---

    @Override
    public void delete(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

        if (flight.getStatus() != FlightStatus.CANCELLED) {
            throw new BusinessRuleException("FLIGHT_NOT_CANCELLED", "Cancel the flight before deleting it");
        }

        flightRepository.delete(flight);
    }

    // --- cancel ---

    @Override
    public FlightResponse cancel(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

        // Already cancelled -> reject, no event published
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            throw new BusinessRuleException("FLIGHT_ALREADY_CANCELLED", "Flight is already cancelled");
        }

        // Departed -> reject
        if (flight.getDepartureTime().isBefore(clock.instant())) {
            throw new BusinessRuleException("FLIGHT_DEPARTED", "Cannot cancel a flight that has already departed");
        }

        flight.setStatus(FlightStatus.CANCELLED);
        flight = flightRepository.saveAndFlush(flight);
        eventPublisher.publishFlightCancelled(new FlightCancelledEvent(
                flight.getId(), flight.getFlightNumber()));
        return flightMapper.toResponse(flight);
    }

    // --- helpers ---

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateFlightNumber(String flightNumber) {
        if (!FLIGHT_NUMBER_PATTERN.matcher(flightNumber).matches()) {
            throw new IllegalArgumentException("Invalid flight number format");
        }
    }

    /**
     * Validate schedule constraints.
     * @param departureTime the departure time
     * @param arrivalTime the arrival time
     * @param mustBeInFuture true if departureTime must be after now (create), false for update
     */
    private void validateSchedule(Instant departureTime, Instant arrivalTime, boolean mustBeInFuture) {
        if (departureTime.equals(arrivalTime) || departureTime.isAfter(arrivalTime)) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }

        Duration duration = Duration.between(departureTime, arrivalTime);
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Flight duration cannot exceed 20 hours");
        }

        if (mustBeInFuture && departureTime.isBefore(clock.instant())) {
            throw new IllegalArgumentException("Departure time must be in the future");
        }
    }

    private Airport findAirportByCode(String iataCode) {
        return airportRepository.findByIataCode(iataCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "iataCode", iataCode));
    }
}
