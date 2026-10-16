package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.dto.AirportMapper;
import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AirportServiceImplTest {

    @Mock
    private AirportRepository airportRepository;
    @Mock
    private AirportMapper airportMapper;

    private AirportServiceImpl newService() {
        return new AirportServiceImpl(airportRepository, airportMapper);
    }

    private AirportRequest buildRequest(String iataCode) {
        return new AirportRequest(iataCode, iataCode + " Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));
    }

    @Test
    void create_duplicateIataCode_throwsDuplicateResourceException() {
        AirportServiceImpl service = newService();
        when(airportRepository.existsByIataCode("IST")).thenReturn(true);

        assertThatThrownBy(() -> service.create(buildRequest("IST")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(airportRepository, never()).save(any());
    }

    @Test
    void create_valid_savesAndReturnsMapped() {
        AirportServiceImpl service = newService();
        AirportRequest request = buildRequest("IST");
        Airport mapped = new Airport();
        AirportResponse response = mock(AirportResponse.class);

        when(airportRepository.existsByIataCode("IST")).thenReturn(false);
        when(airportMapper.toEntity(request)).thenReturn(mapped);
        when(airportRepository.save(mapped)).thenReturn(mapped);
        when(airportMapper.toResponse(mapped)).thenReturn(response);

        assertThat(service.create(request)).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIataCode_notFound_throwsResourceNotFound() {
        AirportServiceImpl service = newService();
        when(airportRepository.findByIataCode("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIataCode("ZZZ")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIataCode_found_returnsMapped() {
        AirportServiceImpl service = newService();
        Airport airport = new Airport();
        AirportResponse response = mock(AirportResponse.class);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(airport));
        when(airportMapper.toResponse(airport)).thenReturn(response);

        assertThat(service.getByIataCode("IST")).isEqualTo(response);
    }

    @Test
    void getAll_mapsAllAirports() {
        AirportServiceImpl service = newService();
        when(airportRepository.findAll()).thenReturn(List.of(new Airport(), new Airport()));
        when(airportMapper.toResponse(any(Airport.class))).thenReturn(mock(AirportResponse.class));

        assertThat(service.getAll()).hasSize(2);
    }

    @Test
    void update_notFound_throwsResourceNotFound() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, buildRequest("IST")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_valid_updatesAndSaves() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Airport existing = new Airport();
        AirportRequest request = buildRequest("LHR");

        when(airportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.save(existing)).thenReturn(existing);
        when(airportMapper.toResponse(existing)).thenReturn(mock(AirportResponse.class));

        service.update(id, request);

        verify(airportMapper).updateEntity(request, existing);
        verify(airportRepository).save(existing);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(airportRepository, never()).deleteById(any());
    }

    @Test
    void delete_found_deletes() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(airportRepository).deleteById(id);
    }
}
