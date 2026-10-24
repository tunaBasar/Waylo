package com.waylo.packagebookingsagaorchestrator.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightSeatInventoryClientTest {
    @Test
    void reserveAndReleaseCarryUserIdentityHeader() throws Exception {
        Method reserve = FlightSeatInventoryClient.class.getMethod("reserveSeat", String.class, UUID.class,
                FlightSeatInventoryClient.SeatReservationRequest.class);
        Method release = FlightSeatInventoryClient.class.getMethod("releaseSeat", String.class, UUID.class,
                FlightSeatInventoryClient.SeatReservationRequest.class);

        assertEquals("X-User-Id", reserve.getParameters()[1].getAnnotation(RequestHeader.class).value());
        assertEquals("X-User-Id", release.getParameters()[1].getAnnotation(RequestHeader.class).value());
    }
}
