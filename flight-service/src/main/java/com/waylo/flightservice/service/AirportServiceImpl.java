package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.dto.AirportMapper;
import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;
    private final AirportMapper airportMapper;

    public AirportServiceImpl(AirportRepository airportRepository, AirportMapper airportMapper) {
        this.airportRepository = airportRepository;
        this.airportMapper = airportMapper;
    }

    @Override
    public AirportResponse create(AirportRequest request) {
        if (airportRepository.existsByIataCode(request.iataCode())) {
            throw new DuplicateResourceException("Airport", "iataCode", request.iataCode());
        }
        Airport airport = airportMapper.toEntity(request);
        airport = airportRepository.save(airport);
        return airportMapper.toResponse(airport);
    }

    @Override
    @Transactional(readOnly = true)
    public AirportResponse getById(UUID id) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));
        return airportMapper.toResponse(airport);
    }

    @Override
    @Transactional(readOnly = true)
    public AirportResponse getByIataCode(String iataCode) {
        Airport airport = airportRepository.findByIataCode(iataCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "iataCode", iataCode));
        return airportMapper.toResponse(airport);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AirportResponse> getAll() {
        return airportRepository.findAll().stream()
                .map(airportMapper::toResponse)
                .toList();
    }

    @Override
    public AirportResponse update(UUID id, AirportRequest request) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));
        airportMapper.updateEntity(request, airport);
        airport = airportRepository.save(airport);
        return airportMapper.toResponse(airport);
    }

    @Override
    public void delete(UUID id) {
        if (!airportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airport", "id", id);
        }
        airportRepository.deleteById(id);
    }
}
