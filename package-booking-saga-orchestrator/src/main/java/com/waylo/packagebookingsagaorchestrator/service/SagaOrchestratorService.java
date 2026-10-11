package com.waylo.packagebookingsagaorchestrator.service;

import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import com.waylo.packagebookingsagaorchestrator.dto.SagaInstanceResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaStartRequest;

import java.util.List;
import java.util.UUID;

public interface SagaOrchestratorService {
    SagaInstanceResponse startSaga(SagaStartRequest request);
    SagaInstanceResponse getById(UUID id);
    SagaInstanceResponse getByBookingId(UUID bookingId);
    List<SagaInstanceResponse> getAll();
    List<SagaInstanceResponse> getByStatus(SagaStatus status);
    SagaInstanceResponse compensate(UUID id);
}
