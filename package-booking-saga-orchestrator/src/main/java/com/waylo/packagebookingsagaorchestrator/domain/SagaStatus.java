package com.waylo.packagebookingsagaorchestrator.domain;

public enum SagaStatus {
    STARTED,
    FLIGHT_RESERVED,
    HOTEL_RESERVED,
    CAR_RESERVED,
    PAYMENT_COMPLETED,
    CONFIRMED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
