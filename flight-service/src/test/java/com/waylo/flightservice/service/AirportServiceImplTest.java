package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.dto.AirportMapper;
import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    // --- IATA normalization tests ---

    @Test
    void create_normalizesIataCode_lowercaseAndTrimmed() {
        AirportServiceImpl service = newService();
        AirportRequest request = buildRequest(" ist ");
        Airport mapped = new Airport();
        AirportResponse response = mock(AirportResponse.class);

        when(airportRepository.existsByIataCode("IST")).thenReturn(false);
        when(airportMapper.toEntity(request)).thenReturn(mapped);
        when(airportRepository.saveAndFlush(mapped)).thenReturn(mapped);
        when(airportMapper.toResponse(mapped)).thenReturn(response);

        assertThat(service.create(request)).isEqualTo(response);

        // Verify that save was called with IST
        ArgumentCaptor<Airport> captor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository).saveAndFlush(captor.capture());
        Airport saved = captor.getValue();
        assertThat(saved.getIataCode()).isEqualTo("IST");
        // Verify trim on name
        assertThat(saved.getName()).isEqualTo("ist  Airport");
    }

    @Test
    void create_duplicateAfterNormalization_throws409() {
        AirportServiceImpl service = newService();
        AirportRequest request = buildRequest(" ist ");

        when(airportRepository.existsByIataCode("IST")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("IST");
        verify(airportRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_invalidTimezone_throwsIllegalArgumentException() {
        AirportServiceImpl service = newService();
        AirportRequest request = new AirportRequest("IST", "Istanbul Airport", "Istanbul", "Turkey",
                "Mars/Base", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
        verify(airportRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_offsetStyleTimezone_throwsIllegalArgumentException() {
        AirportServiceImpl service = newService();
        AirportRequest request = new AirportRequest("IST", "Istanbul Airport", "Istanbul", "Turkey",
                "+03:00", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
        verify(airportRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_paddedTimezone_acceptedAndSavedTrimmed() {
        AirportServiceImpl service = newService();
        AirportRequest request = new AirportRequest("IST", "Istanbul Airport", "Istanbul", "Turkey",
                "  Europe/Istanbul  ", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));
        Airport mapped = new Airport();
        AirportResponse response = mock(AirportResponse.class);

        when(airportRepository.existsByIataCode("IST")).thenReturn(false);
        when(airportMapper.toEntity(request)).thenReturn(mapped);
        when(airportRepository.saveAndFlush(mapped)).thenReturn(mapped);
        when(airportMapper.toResponse(mapped)).thenReturn(response);

        assertThat(service.create(request)).isEqualTo(response);

        ArgumentCaptor<Airport> captor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository).saveAndFlush(captor.capture());
        Airport saved = captor.getValue();
        assertThat(saved.getTimezone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void create_duplicateReportsNormalizedIata() {
        AirportServiceImpl service = newService();
        AirportRequest request = new AirportRequest(" ist ", "Airport", "City", "Country",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        when(airportRepository.existsByIataCode("IST")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("IST");
    }

    @Test
    void create_updateReportNormalizedIataInDuplicate() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Airport existing = new Airport();
        existing.setIataCode("IST");

        AirportRequest request = new AirportRequest("LHR", "LHR Airport", "London", "UK",
                "Europe/London", BigDecimal.valueOf(51.5), BigDecimal.valueOf(-0.1));

        when(airportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.existsByIataCode("LHR")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("LHR");
        verify(airportRepository, never()).saveAndFlush(any());
    }

    // --- getByIataCode tests ---

    @Test
    void getByIataCode_normalizesAndLooksUpWithNormalized() {
        AirportServiceImpl service = newService();
        Airport airport = new Airport();
        AirportResponse response = mock(AirportResponse.class);

        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(airport));
        when(airportMapper.toResponse(airport)).thenReturn(response);

        assertThat(service.getByIataCode(" ist ")).isEqualTo(response);
        // Verify no any() - must call with exact normalized value
        verify(airportRepository).findByIataCode("IST");
        verifyNoMoreInteractions(airportRepository);
    }

    @Test
    void getByIataCode_notFound_throwsResourceNotFound() {
        AirportServiceImpl service = newService();
        when(airportRepository.findByIataCode("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIataCode("ZZZ")).isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll tests ---

    @Test
    void getAll_sortedByIataCodeAsc() {
        AirportServiceImpl service = newService();
        Airport ist = new Airport();
        ist.setIataCode("IST");
        Airport lhr = new Airport();
        lhr.setIataCode("LHR");
        Airport jfk = new Airport();
        jfk.setIataCode("JFK");

        when(airportRepository.findAllByOrderByIataCodeAsc()).thenReturn(List.of(ist, jfk, lhr));
        AirportResponse istResp = mock(AirportResponse.class);
        when(istResp.iataCode()).thenReturn("IST");
        AirportResponse jfkResp = mock(AirportResponse.class);
        when(jfkResp.iataCode()).thenReturn("JFK");
        AirportResponse lhrResp = mock(AirportResponse.class);
        when(lhrResp.iataCode()).thenReturn("LHR");

        when(airportMapper.toResponse(ist)).thenReturn(istResp);
        when(airportMapper.toResponse(jfk)).thenReturn(jfkResp);
        when(airportMapper.toResponse(lhr)).thenReturn(lhrResp);

        List<AirportResponse> result = service.getAll();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).iataCode()).isEqualTo("IST");
        assertThat(result.get(1).iataCode()).isEqualTo("JFK");
        assertThat(result.get(2).iataCode()).isEqualTo("LHR");

        // Verify that findAll was NOT called (only findAllByOrderByIataCodeAsc)
        verify(airportRepository, never()).findAll();
    }

    // --- update tests ---

    @Test
    void update_changeIataToDuplicate_throws409() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Airport existing = new Airport();
        existing.setIataCode("IST");

        AirportRequest request = new AirportRequest("LHR", "LHR Airport", "London", "UK",
                "Europe/London", BigDecimal.valueOf(51.5), BigDecimal.valueOf(-0.1));

        when(airportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.existsByIataCode("LHR")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("LHR");
        verify(airportRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_sameIataChangesFields() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        Airport existing = new Airport();
        existing.setIataCode("IST");
        AirportRequest request = new AirportRequest("ist", "Heathrow Update", "London", "UK",
                "Europe/London", BigDecimal.valueOf(51.5), BigDecimal.valueOf(-0.1));

        when(airportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.saveAndFlush(existing)).thenReturn(existing);
        when(airportMapper.toResponse(existing)).thenReturn(mock(AirportResponse.class));

        service.update(id, request);

        // Verify updateEntity was called
        verify(airportMapper).updateEntity(request, existing);
        // Verify iataCode was normalized
        assertThat(existing.getIataCode()).isEqualTo("IST");
        // Verify trim on other fields
        assertThat(existing.getName()).isEqualTo("Heathrow Update");
        assertThat(existing.getTimezone()).isEqualTo("Europe/London");
        verify(airportRepository).saveAndFlush(existing);
    }

    @Test
    void update_invalidTimezone_throwsIllegalArgumentException() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();

        AirportRequest request = new AirportRequest("IST", "Istanbul Airport", "Istanbul", "Turkey",
                "Invalid/TZ", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
        verify(airportRepository, never()).findById(any());
        verify(airportRepository, never()).saveAndFlush(any());
    }

    // --- delete tests ---

    @Test
    void update_offsetStyleTimezone_throwsIllegalArgumentException() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();

        AirportRequest request = new AirportRequest("IST", "Istanbul Airport", "Istanbul", "Turkey",
                "+03:00", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
        verify(airportRepository, never()).findById(any());
        verify(airportRepository, never()).saveAndFlush(any());
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
    void delete_inUse_throwsBusinessRuleException() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.existsById(id)).thenReturn(true);
        when(airportRepository.isReferencedByFlights(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Airport is used by flights")
                .hasFieldOrPropertyWithValue("ruleCode", "AIRPORT_IN_USE");

        // Verify deleteById was NOT called
        verify(airportRepository, never()).deleteById(any());
    }

    @Test
    void delete_free_deletes() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.existsById(id)).thenReturn(true);
        when(airportRepository.isReferencedByFlights(id)).thenReturn(false);

        service.delete(id);

        verify(airportRepository).deleteById(id);
    }

    @Test
    void delete_inUse_doesNotCallDeleteById() {
        AirportServiceImpl service = newService();
        UUID id = UUID.randomUUID();
        when(airportRepository.existsById(id)).thenReturn(true);
        when(airportRepository.isReferencedByFlights(id)).thenReturn(true);

        try {
            service.delete(id);
        } catch (BusinessRuleException ignored) {
        }

        // Verify deleteById was NOT called - use any() for the UUID arg
        verify(airportRepository, never()).deleteById(any(UUID.class));
    }
}
