package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.domain.Flight;
import com.waylo.flightservice.domain.FlightStatus;
import com.waylo.flightservice.dto.FlightMapper;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final FlightMapper flightMapper;

    public FlightServiceImpl(FlightRepository flightRepository,
                             AirportRepository airportRepository,
                             FlightMapper flightMapper) {
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.flightMapper = flightMapper;
    }

    @Override
    public FlightResponse create(FlightRequest request) {
        if (flightRepository.existsByFlightNumber(request.flightNumber())) {
            throw new DuplicateResourceException("Flight", "flightNumber", request.flightNumber());
        }

        Airport departure = findAirportByCode(request.departureAirportCode());
        Airport arrival = findAirportByCode(request.arrivalAirportCode());

        Flight flight = flightMapper.toEntity(request);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);

        if (flight.getStatus() == null) {
            flight.setStatus(FlightStatus.SCHEDULED);
        }

        flight = flightRepository.save(flight);
        return flightMapper.toResponse(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getById(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
        return flightMapper.toResponse(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getByFlightNumber(String flightNumber) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "flightNumber", flightNumber));
        return flightMapper.toResponse(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponse> getAll() {
        return flightRepository.findAll().stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponse> search(String departureCode, String arrivalCode,
                                       Instant from, Instant to) {
        return flightRepository
                .findByDepartureAirport_IataCodeAndArrivalAirport_IataCodeAndDepartureTimeBetween(
                        departureCode, arrivalCode, from, to)
                .stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    @Override
    public FlightResponse update(UUID id, FlightRequest request) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

        Airport departure = findAirportByCode(request.departureAirportCode());
        Airport arrival = findAirportByCode(request.arrivalAirportCode());

        flightMapper.updateEntity(request, flight);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);

        flight = flightRepository.save(flight);
        return flightMapper.toResponse(flight);
    }

    @Override
    public void delete(UUID id) {
        if (!flightRepository.existsById(id)) {
            throw new ResourceNotFoundException("Flight", "id", id);
        }
        flightRepository.deleteById(id);
    }

    @Override
    public FlightResponse cancel(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
        flight.setStatus(FlightStatus.CANCELLED);
        flight = flightRepository.save(flight);
        return flightMapper.toResponse(flight);
    }

    private Airport findAirportByCode(String iataCode) {
        return airportRepository.findByIataCode(iataCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "iataCode", iataCode));
    }
}
