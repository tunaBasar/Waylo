package com.waylo.checkinservice.service;

import com.waylo.checkinservice.domain.Checkin;
import com.waylo.checkinservice.domain.CheckinStatus;
import com.waylo.checkinservice.dto.CheckinMapper;
import com.waylo.checkinservice.dto.CheckinRequest;
import com.waylo.checkinservice.dto.CheckinResponse;
import com.waylo.checkinservice.exception.ResourceNotFoundException;
import com.waylo.checkinservice.repository.CheckinRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    @Mock
    private CheckinRepository repository;
    @Mock
    private CheckinMapper mapper;

    private CheckinServiceImpl service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID flightId = UUID.randomUUID();

    private CheckinServiceImpl newService() {
        return new CheckinServiceImpl(repository, mapper);
    }

    private Checkin buildCheckin(UUID id, CheckinStatus status) {
        Checkin c = new Checkin();
        c.setId(id);
        c.setBookingId(bookingId);
        c.setCustomerId(customerId);
        c.setFlightId(flightId);
        c.setSeatNumber("12A");
        c.setStatus(status);
        return c;
    }

    // --- checkIn ---

    @Test
    void checkIn_setsStatusCheckedInAndTimestamp() {
        service = newService();
        CheckinRequest request = new CheckinRequest(bookingId, customerId, flightId, "12A");
        Checkin mapped = new Checkin();
        mapped.setBookingId(bookingId);
        mapped.setCustomerId(customerId);
        mapped.setFlightId(flightId);
        mapped.setSeatNumber("12A");

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(any(Checkin.class))).thenAnswer(inv -> inv.getArgument(0));
        CheckinResponse response = mock(CheckinResponse.class);
        when(mapper.toResponse(any(Checkin.class))).thenReturn(response);

        CheckinResponse result = service.checkIn(request);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Checkin> captor = ArgumentCaptor.forClass(Checkin.class);
        verify(repository).save(captor.capture());
        Checkin saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(saved.getCheckedInAt()).isNotNull();
    }

    // --- getById ---

    @Test
    void getById_found_returnsMappedResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        Checkin checkin = buildCheckin(id, CheckinStatus.NOT_CHECKED_IN);
        CheckinResponse response = mock(CheckinResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(checkin));
        when(mapper.toResponse(checkin)).thenReturn(response);

        CheckinResponse result = service.getById(id);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_mapsAllCheckins() {
        service = newService();
        Checkin c1 = buildCheckin(UUID.randomUUID(), CheckinStatus.CHECKED_IN);
        Checkin c2 = buildCheckin(UUID.randomUUID(), CheckinStatus.NOT_CHECKED_IN);
        when(repository.findAll()).thenReturn(List.of(c1, c2));
        when(mapper.toResponse(any(Checkin.class))).thenReturn(mock(CheckinResponse.class));

        List<CheckinResponse> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    // --- getByBookingId ---

    @Test
    void getByBookingId_filtersCorrectly() {
        service = newService();
        Checkin c = buildCheckin(UUID.randomUUID(), CheckinStatus.CHECKED_IN);
        when(repository.findByBookingId(bookingId)).thenReturn(List.of(c));
        when(mapper.toResponse(c)).thenReturn(mock(CheckinResponse.class));

        List<CheckinResponse> result = service.getByBookingId(bookingId);

        assertThat(result).hasSize(1);
        verify(repository).findByBookingId(bookingId);
    }

    // --- getByFlightId ---

    @Test
    void getByFlightId_filtersCorrectly() {
        service = newService();
        Checkin c = buildCheckin(UUID.randomUUID(), CheckinStatus.CHECKED_IN);
        when(repository.findByFlightId(flightId)).thenReturn(List.of(c));
        when(mapper.toResponse(c)).thenReturn(mock(CheckinResponse.class));

        List<CheckinResponse> result = service.getByFlightId(flightId);

        assertThat(result).hasSize(1);
        verify(repository).findByFlightId(flightId);
    }

    // --- issueBoardingPass ---

    @Test
    void issueBoardingPass_setsStatusAndUrl() {
        service = newService();
        UUID id = UUID.randomUUID();
        Checkin checkin = buildCheckin(id, CheckinStatus.CHECKED_IN);
        when(repository.findById(id)).thenReturn(Optional.of(checkin));
        when(repository.save(any(Checkin.class))).thenAnswer(inv -> inv.getArgument(0));
        CheckinResponse response = mock(CheckinResponse.class);
        when(mapper.toResponse(any(Checkin.class))).thenReturn(response);

        CheckinResponse result = service.issueBoardingPass(id);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Checkin> captor = ArgumentCaptor.forClass(Checkin.class);
        verify(repository).save(captor.capture());
        Checkin saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CheckinStatus.BOARDING_PASS_ISSUED);
        assertThat(saved.getBoardingPassUrl()).contains(id.toString());
    }

    @Test
    void issueBoardingPass_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueBoardingPass(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- cancel ---

    @Test
    void cancel_setsStatusCancelled() {
        service = newService();
        UUID id = UUID.randomUUID();
        Checkin checkin = buildCheckin(id, CheckinStatus.CHECKED_IN);
        when(repository.findById(id)).thenReturn(Optional.of(checkin));
        when(repository.save(any(Checkin.class))).thenAnswer(inv -> inv.getArgument(0));
        CheckinResponse response = mock(CheckinResponse.class);
        when(mapper.toResponse(any(Checkin.class))).thenReturn(response);

        CheckinResponse result = service.cancel(id);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Checkin> captor = ArgumentCaptor.forClass(Checkin.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CheckinStatus.CANCELLED);
    }

    @Test
    void cancel_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
