package com.waylo.packagebookingsagaorchestrator.dto;

import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SagaInstanceResponse(
        UUID id, UUID bookingId, UUID customerId, SagaStatus status,
        String currentStep, String failureReason,
        List<SagaStepResponse> steps,
        Instant createdAt, Instant updatedAt
) {}
