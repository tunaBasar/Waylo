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
        when(idempotency.isProcessed(event.getEventId())).thenReturn(false);

        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", event), ack);

        verify(service).closeFlightSeats(" tk100 ");
        verify(idempotency).markProcessed(event.getEventId());
        verify(ack).acknowledge();
    }

    @Test
    void duplicateCancellationDoesNotCloseSeats() {
        FlightCancelledEvent event = new FlightCancelledEvent();
        when(idempotency.isProcessed(event.getEventId())).thenReturn(true);

        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", event), ack);

        verifyNoInteractions(service);
        verify(idempotency, never()).markProcessed(any());
        verify(ack).acknowledge();
    }

    @Test
    void processingFailurePropagates_notAcked_notMarkedProcessed() {
        FlightCancelledEvent event = new FlightCancelledEvent();
        when(idempotency.isProcessed(event.getEventId())).thenReturn(false);
        when(service.closeFlightSeats(any())).thenThrow(new IllegalStateException("db down"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", event), ack));

        verify(ack, never()).acknowledge();
        verify(idempotency, never()).markProcessed(any());
    }

    @Test
    void unknownEventIsIgnored() {
        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "TK100", new Object()), ack);

        verifyNoInteractions(idempotency, service);
        verify(ack).acknowledge();
    }
}
