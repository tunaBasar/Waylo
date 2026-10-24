package com.waylo.flightseatinventoryservice.listener;

import com.waylo.flightseatinventoryservice.event.FlightCancelledEvent;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import com.waylo.flightseatinventoryservice.service.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

class FlightEventListenerTest {
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final FlightSeatService service = mock(FlightSeatService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final FlightEventListener listener = new FlightEventListener(idempotency, service);

    @Test
    void cancellationClosesSeatsOnceAndAcknowledges() {
        FlightCancelledEvent event = new FlightCancelledEvent();
        event.setFlightNumber(" tk100 ");
        when(idempotency.tryAcquire(event.getEventId())).thenReturn(true);

        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", event), ack);

        verify(service).closeFlightSeats(" tk100 ");
        verify(ack).acknowledge();
    }

    @Test
    void duplicateCancellationDoesNotCloseSeats() {
        FlightCancelledEvent event = new FlightCancelledEvent();
        when(idempotency.tryAcquire(event.getEventId())).thenReturn(false);

        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", event), ack);

        verifyNoInteractions(service);
        verify(ack).acknowledge();
    }

    @Test
    void unknownEventIsIgnored() {
        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", new Object()), ack);

        verifyNoInteractions(idempotency, service);
        verify(ack).acknowledge();
    }
}
