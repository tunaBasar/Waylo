package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.dto.AirportMapper;
import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.ZoneId;

@Service
@Transactional
public class AirportServiceImpl implements AirportService {

    private static final Logger log = LoggerFactory.getLogger(AirportServiceImpl.class);

    private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

    private final AirportRepository airportRepository;
    private final AirportMapper airportMapper;

    public AirportServiceImpl(AirportRepository airportRepository, AirportMapper airportMapper) {
        this.airportRepository = airportRepository;
        this.airportMapper = airportMapper;
    }

    @Override
    public AirportResponse create(AirportRequest request) {
        String normalizedIata = normalizeIataCode(request.iataCode());

        if (airportRepository.existsByIataCode(normalizedIata)) {
            throw new DuplicateResourceException("Airport", "iataCode", normalizedIata);
        }

        String trimmedTimezone = trim(request.timezone());
        validateTimezone(trimmedTimezone);

        Airport airport = airportMapper.toEntity(request);
        airport.setIataCode(normalizedIata);
        airport.setName(trim(request.name()));
        airport.setCity(trim(request.city()));
        airport.setCountry(trim(request.country()));
        airport.setTimezone(trimmedTimezone);

        airport = airportRepository.saveAndFlush(airport);
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
        String normalizedIata = normalizeIataCode(iataCode);
        Airport airport = airportRepository.findByIataCode(normalizedIata)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "iataCode", normalizedIata));
        return airportMapper.toResponse(airport);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AirportResponse> getAll() {
        return airportRepository.findAllByOrderByIataCodeAsc().stream()
                .map(airportMapper::toResponse)
                .toList();
    }

    @Override
    public AirportResponse update(UUID id, AirportRequest request) {
        String newNormalizedIata = normalizeIataCode(request.iataCode());
        String trimmedTimezone = trim(request.timezone());
        validateTimezone(trimmedTimezone);

        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));

        // Check if IATA changed and another airport has it
        String oldIata = airport.getIataCode();
        if (!newNormalizedIata.equals(oldIata) && airportRepository.existsByIataCode(newNormalizedIata)) {
            throw new DuplicateResourceException("Airport", "iataCode", newNormalizedIata);
        }

        airportMapper.updateEntity(request, airport);
        airport.setIataCode(newNormalizedIata);
        airport.setName(trim(request.name()));
        airport.setCity(trim(request.city()));
        airport.setCountry(trim(request.country()));
        airport.setTimezone(trimmedTimezone);

        airport = airportRepository.saveAndFlush(airport);
        return airportMapper.toResponse(airport);
    }

    @Override
    public void delete(UUID id) {
        if (!airportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airport", "id", id);
        }

        if (airportRepository.isReferencedByFlights(id)) {
            throw new BusinessRuleException("AIRPORT_IN_USE", "Airport is used by flights");
        }

        airportRepository.deleteById(id);
    }

    // --- Helpers ---

    private static String normalizeIataCode(String iataCode) {
        return iataCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value != null ? value.trim() : value;
    }

    private static void validateTimezone(String timezone) {
        // Only accept region IDs (e.g. "Europe/Istanbul"), reject offset-style zones (e.g. "+03:00", "Z")
        if (!AVAILABLE_ZONE_IDS.contains(timezone)) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }
}
