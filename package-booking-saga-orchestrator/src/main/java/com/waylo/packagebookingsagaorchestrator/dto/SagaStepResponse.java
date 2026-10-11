package com.waylo.packagebookingsagaorchestrator.dto;

import com.waylo.packagebookingsagaorchestrator.domain.SagaStepStatus;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStepType;
import java.time.Instant;
import java.util.UUID;

public record SagaStepResponse(
        UUID id, SagaStepType stepType, SagaStepStatus status, int stepOrder,
        String errorMessage, Instant startedAt, Instant completedAt
) {}
