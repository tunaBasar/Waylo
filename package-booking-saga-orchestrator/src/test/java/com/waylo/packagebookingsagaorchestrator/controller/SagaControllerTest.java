package com.waylo.packagebookingsagaorchestrator.controller;

import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import com.waylo.packagebookingsagaorchestrator.dto.ApiResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaInstanceResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaStartRequest;
import com.waylo.packagebookingsagaorchestrator.service.SagaOrchestratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaControllerTest {

    @Mock
    private SagaOrchestratorService sagaService;
    @InjectMocks
    private SagaController controller;

    private final UUID sagaId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private SagaInstanceResponse buildResponse() {
        return new SagaInstanceResponse(sagaId, bookingId, customerId, SagaStatus.STARTED,
                "RESERVE_FLIGHT", null, List.of(), Instant.now(), Instant.now());
    }

    @Test
    void start_returns201() {
        SagaStartRequest request = new SagaStartRequest(bookingId, customerId, UUID.randomUUID(), null, null);
        SagaInstanceResponse response = buildResponse();
        when(sagaService.startSaga(any(SagaStartRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<SagaInstanceResponse>> result = controller.start(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        SagaInstanceResponse response = buildResponse();
        when(sagaService.getById(sagaId)).thenReturn(response);

        ResponseEntity<ApiResponse<SagaInstanceResponse>> result = controller.getById(sagaId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(sagaService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<SagaInstanceResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByBookingId_returns200() {
        SagaInstanceResponse response = buildResponse();
        when(sagaService.getByBookingId(bookingId)).thenReturn(response);

        ResponseEntity<ApiResponse<SagaInstanceResponse>> result = controller.getByBookingId(bookingId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getByStatus_returns200() {
        when(sagaService.getByStatus(SagaStatus.STARTED)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<SagaInstanceResponse>>> result = controller.getByStatus(SagaStatus.STARTED);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(sagaService).getByStatus(SagaStatus.STARTED);
    }

    @Test
    void compensate_returns200() {
        SagaInstanceResponse response = new SagaInstanceResponse(sagaId, bookingId, customerId, SagaStatus.COMPENSATED,
                null, null, List.of(), Instant.now(), Instant.now());
        when(sagaService.compensate(sagaId)).thenReturn(response);

        ResponseEntity<ApiResponse<SagaInstanceResponse>> result = controller.compensate(sagaId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().status()).isEqualTo(SagaStatus.COMPENSATED);
    }
}
