package com.waylo.packagebookingsagaorchestrator.service;

import com.waylo.packagebookingsagaorchestrator.domain.SagaInstance;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStep;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStepStatus;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStepType;
import com.waylo.packagebookingsagaorchestrator.dto.SagaInstanceResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaMapper;
import com.waylo.packagebookingsagaorchestrator.dto.SagaStartRequest;
import com.waylo.packagebookingsagaorchestrator.exception.DuplicateResourceException;
import com.waylo.packagebookingsagaorchestrator.exception.ResourceNotFoundException;
import com.waylo.packagebookingsagaorchestrator.repository.SagaInstanceRepository;
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
class SagaOrchestratorServiceImplTest {

    @Mock
    private SagaInstanceRepository repository;
    @Mock
    private SagaMapper mapper;

    private SagaOrchestratorServiceImpl service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private SagaOrchestratorServiceImpl newService() {
        return new SagaOrchestratorServiceImpl(repository, mapper);
    }

    private SagaStep buildStep(SagaInstance saga, SagaStepType type, SagaStepStatus status, int order) {
        SagaStep step = new SagaStep();
        step.setSagaInstance(saga);
        step.setStepType(type);
        step.setStatus(status);
        step.setStepOrder(order);
        return step;
    }

    // --- startSaga ---

    @Test
    void startSaga_duplicateBookingId_throwsAndNeverSaves() {
        service = newService();
        SagaStartRequest request = new SagaStartRequest(bookingId, customerId, UUID.randomUUID(), null, null);
        when(repository.existsByBookingId(bookingId)).thenReturn(true);

        assertThatThrownBy(() -> service.startSaga(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any(SagaInstance.class));
    }

    @Test
    void startSaga_onlyFlightId_producesThreeStepsInOrder() {
        service = newService();
        UUID flightId = UUID.randomUUID();
        SagaStartRequest request = new SagaStartRequest(bookingId, customerId, flightId, null, null);
        when(repository.existsByBookingId(bookingId)).thenReturn(false);
        when(repository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SagaInstance.class))).thenReturn(mock(SagaInstanceResponse.class));

        service.startSaga(request);

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(repository).save(captor.capture());
        SagaInstance saved = captor.getValue();

        assertThat(saved.getSteps()).hasSize(3);
        assertThat(saved.getSteps().stream().map(SagaStep::getStepType).toList())
                .containsExactly(SagaStepType.RESERVE_FLIGHT, SagaStepType.PROCESS_PAYMENT, SagaStepType.CONFIRM_BOOKING);
        assertThat(saved.getSteps()).allMatch(s -> s.getStatus() == SagaStepStatus.PENDING);
        assertThat(saved.getCurrentStep()).isEqualTo(SagaStepType.RESERVE_FLIGHT.name());
    }

    @Test
    void startSaga_allReservationsSet_producesFiveStepsInOrder() {
        service = newService();
        UUID flightId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        SagaStartRequest request = new SagaStartRequest(bookingId, customerId, flightId, hotelId, vehicleId);
        when(repository.existsByBookingId(bookingId)).thenReturn(false);
        when(repository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SagaInstance.class))).thenReturn(mock(SagaInstanceResponse.class));

        service.startSaga(request);

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(repository).save(captor.capture());
        SagaInstance saved = captor.getValue();

        assertThat(saved.getSteps()).hasSize(5);
        assertThat(saved.getSteps().stream().map(SagaStep::getStepType).toList())
                .containsExactly(
                        SagaStepType.RESERVE_FLIGHT,
                        SagaStepType.RESERVE_HOTEL,
                        SagaStepType.RESERVE_CAR,
                        SagaStepType.PROCESS_PAYMENT,
                        SagaStepType.CONFIRM_BOOKING);
        for (int i = 0; i < saved.getSteps().size(); i++) {
            assertThat(saved.getSteps().get(i).getStepOrder()).isEqualTo(i);
        }
        assertThat(saved.getCurrentStep()).isEqualTo(SagaStepType.RESERVE_FLIGHT.name());
    }

    // --- getById ---

    @Test
    void getById_found_returnsMappedResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        SagaInstance saga = new SagaInstance();
        SagaInstanceResponse response = mock(SagaInstanceResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(saga));
        when(mapper.toResponse(saga)).thenReturn(response);

        SagaInstanceResponse result = service.getById(id);

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

    // --- getByBookingId ---

    @Test
    void getByBookingId_found_returnsMappedResponse() {
        service = newService();
        SagaInstance saga = new SagaInstance();
        SagaInstanceResponse response = mock(SagaInstanceResponse.class);
        when(repository.findByBookingId(bookingId)).thenReturn(Optional.of(saga));
        when(mapper.toResponse(saga)).thenReturn(response);

        SagaInstanceResponse result = service.getByBookingId(bookingId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getByBookingId_notFound_throwsResourceNotFound() {
        service = newService();
        when(repository.findByBookingId(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByBookingId(bookingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_mapsAllSagas() {
        service = newService();
        SagaInstance s1 = new SagaInstance();
        SagaInstance s2 = new SagaInstance();
        SagaInstanceResponse r1 = mock(SagaInstanceResponse.class);
        SagaInstanceResponse r2 = mock(SagaInstanceResponse.class);
        when(repository.findAll()).thenReturn(List.of(s1, s2));
        when(mapper.toResponse(s1)).thenReturn(r1);
        when(mapper.toResponse(s2)).thenReturn(r2);

        List<SagaInstanceResponse> result = service.getAll();

        assertThat(result).containsExactly(r1, r2);
    }

    // --- getByStatus ---

    @Test
    void getByStatus_mapsFilteredSagas() {
        service = newService();
        SagaInstance s1 = new SagaInstance();
        SagaInstanceResponse r1 = mock(SagaInstanceResponse.class);
        when(repository.findByStatus(SagaStatus.STARTED)).thenReturn(List.of(s1));
        when(mapper.toResponse(s1)).thenReturn(r1);

        List<SagaInstanceResponse> result = service.getByStatus(SagaStatus.STARTED);

        assertThat(result).containsExactly(r1);
    }

    // --- compensate ---

    @Test
    void compensate_notFound_throws() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compensate(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void compensate_onlyCompletedStepsBecomeCompensated() {
        service = newService();
        UUID id = UUID.randomUUID();
        SagaInstance saga = new SagaInstance();
        saga.setId(id);
        saga.setStatus(SagaStatus.STARTED);

        SagaStep completedStep = buildStep(saga, SagaStepType.RESERVE_FLIGHT, SagaStepStatus.COMPLETED, 0);
        SagaStep pendingStep = buildStep(saga, SagaStepType.PROCESS_PAYMENT, SagaStepStatus.PENDING, 1);
        SagaStep failedStep = buildStep(saga, SagaStepType.CONFIRM_BOOKING, SagaStepStatus.FAILED, 2);
        saga.addStep(completedStep);
        saga.addStep(pendingStep);
        saga.addStep(failedStep);

        when(repository.findById(id)).thenReturn(Optional.of(saga));
        when(repository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SagaInstance.class))).thenReturn(mock(SagaInstanceResponse.class));

        service.compensate(id);

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(repository).save(captor.capture());
        SagaInstance saved = captor.getValue();

        assertThat(completedStep.getStatus()).isEqualTo(SagaStepStatus.COMPENSATED);
        assertThat(pendingStep.getStatus()).isEqualTo(SagaStepStatus.PENDING);
        assertThat(failedStep.getStatus()).isEqualTo(SagaStepStatus.FAILED);
        assertThat(saved.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
    }
}
