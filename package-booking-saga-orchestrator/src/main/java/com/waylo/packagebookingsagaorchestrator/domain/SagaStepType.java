package com.waylo.packagebookingsagaorchestrator.domain;

public enum SagaStepType {
    RESERVE_FLIGHT,
    RESERVE_HOTEL,
    RESERVE_CAR,
    PROCESS_PAYMENT,
    CONFIRM_BOOKING,
    COMPENSATE_FLIGHT,
    COMPENSATE_HOTEL,
    COMPENSATE_CAR,
    COMPENSATE_PAYMENT
}
