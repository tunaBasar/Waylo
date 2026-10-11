package com.waylo.packagebookingsagaorchestrator.domain;

public enum SagaStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    COMPENSATED,
    SKIPPED
}
