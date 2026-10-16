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
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private AirportRepository airportRepository;
    @Mock
    private FlightMapper flightMapper;
    @Mock
    private FlightEventPublisher eventPublisher;

    private FlightServiceImpl newService() {
        return new FlightServiceImpl(flightRepository, airportRepository, flightMapper, eventPublisher);
    }

    private Airport buildAirport(String iataCode) {
        Airport a = new Airport();
        a.setId(UUID.randomUUID());
        a.setIataCode(iataCode);
        a.setName(iataCode + " Airport");
        return a;
    }

    private FlightRequest buildRequest(String flightNumber, String from, String to) {
        return new FlightRequest(flightNumber, from, to,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"), "Waylo Air", null);
    }

    // --- create ---

    @Test
    void create_duplicateFlightNumber_throwsDuplicateResourceException() {
        FlightServiceImpl service = newService();
        FlightRequest request = buildRequest("WA100", "IST", "JFK");
        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(airportRepository, never()).findByIataCode(any());
    }

    @Test
    void create_unknownDepartureAirport_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        FlightRequest request = buildRequest("WA100", "XXX", "JFK");
        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(false);
        when(airportRepository.findByIataCode("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_valid_setsAirportsDefaultsStatusAndPublishesEvent() {
        FlightServiceImpl service = newService();
        FlightRequest request = buildRequest("WA100", "IST", "JFK");
        Airport departure = buildAirport("IST");
        Airport arrival = buildAirport("JFK");
        Flight mapped = new Flight();
        mapped.setFlightNumber("WA100");

        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(false);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(departure));
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.of(arrival));
        when(flightMapper.toEntity(request)).thenReturn(mapped);
        when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        service.create(request);

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).save(captor.capture());
        Flight saved = captor.getValue();
        assertThat(saved.getDepartureAirport()).isEqualTo(departure);
        assertThat(saved.getArrivalAirport()).isEqualTo(arrival);
        assertThat(saved.getStatus()).isEqualTo(FlightStatus.SCHEDULED);

        verify(eventPublisher).publishFlightCreated(any(FlightCreatedEvent.class));
    }

    @Test
    void create_statusAlreadySetByMapper_notOverwritten() {
        FlightServiceImpl service = newService();
        FlightRequest request = buildRequest("WA100", "IST", "JFK");
        Flight mapped = new Flight();
        mapped.setStatus(FlightStatus.CANCELLED);

        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(false);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(buildAirport("IST")));
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.of(buildAirport("JFK")));
        when(flightMapper.toEntity(request)).thenReturn(mapped);
        when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        service.create(request);

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FlightStatus.CANCELLED);
    }

    // --- getById / getByFlightNumber ---

    @Test
    void getById_notFound_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByFlightNumber_notFound_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        when(flightRepository.findByFlightNumber("ZZ1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByFlightNumber("ZZ1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByFlightNumber_found_returnsMapped() {
        FlightServiceImpl service = newService();
        Flight flight = new Flight();
        FlightResponse response = mock(FlightResponse.class);
        when(flightRepository.findByFlightNumber("WA100")).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(response);

        assertThat(service.getByFlightNumber("WA100")).isEqualTo(response);
    }

    // --- getAll / search ---

    @Test
    void getAll_mapsAllFlights() {
        FlightServiceImpl service = newService();
        when(flightRepository.findAll()).thenReturn(List.of(new Flight(), new Flight()));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        assertThat(service.getAll()).hasSize(2);
    }

    @Test
    void search_delegatesToRepositoryWithParams() {
        FlightServiceImpl service = newService();
        Instant from = Instant.parse("2026-12-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-02T00:00:00Z");
        when(flightRepository.findByDepartureAirport_IataCodeAndArrivalAirport_IataCodeAndDepartureTimeBetween(
                "IST", "JFK", from, to)).thenReturn(List.of(new Flight()));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        List<FlightResponse> result = service.search("IST", "JFK", from, to);

        assertThat(result).hasSize(1);
        verify(flightRepository).findByDepartureAirport_IataCodeAndArrivalAirport_IataCodeAndDepartureTimeBetween(
                "IST", "JFK", from, to);
    }

    // --- update ---

    @Test
    void update_notFound_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, buildRequest("WA1", "IST", "JFK")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_valid_reassignsAirportsAndSaves() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Flight existing = new Flight();
        Airport newDeparture = buildAirport("LHR");
        Airport newArrival = buildAirport("CDG");
        FlightRequest request = buildRequest("WA200", "LHR", "CDG");

        when(flightRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.findByIataCode("LHR")).thenReturn(Optional.of(newDeparture));
        when(airportRepository.findByIataCode("CDG")).thenReturn(Optional.of(newArrival));
        when(flightRepository.save(existing)).thenReturn(existing);
        when(flightMapper.toResponse(existing)).thenReturn(mock(FlightResponse.class));

        service.update(id, request);

        verify(flightMapper).updateEntity(request, existing);
        assertThat(existing.getDepartureAirport()).isEqualTo(newDeparture);
        assertThat(existing.getArrivalAirport()).isEqualTo(newArrival);
    }

    // --- delete ---

    @Test
    void delete_notFound_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(flightRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(flightRepository, never()).deleteById(any());
    }

    @Test
    void delete_found_deletes() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(flightRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(flightRepository).deleteById(id);
    }

    // --- cancel ---

    @Test
    void cancel_notFound_throwsResourceNotFound() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancel_valid_setsStatusCancelledAndPublishesEvent() {
        FlightServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Flight flight = new Flight();
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setFlightNumber("WA300");

        when(flightRepository.findById(id)).thenReturn(Optional.of(flight));
        when(flightRepository.save(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(mock(FlightResponse.class));

        service.cancel(id);

        assertThat(flight.getStatus()).isEqualTo(FlightStatus.CANCELLED);
        verify(eventPublisher).publishFlightCancelled(any(FlightCancelledEvent.class));
    }
}
