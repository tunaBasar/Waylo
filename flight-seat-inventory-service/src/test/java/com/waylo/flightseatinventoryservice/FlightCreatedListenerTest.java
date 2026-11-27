package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.client.FlightClient;
import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.event.FlightCreatedEvent;
import com.waylo.flightseatinventoryservice.listener.FlightEventListener;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import com.waylo.flightseatinventoryservice.service.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlightCreatedListenerTest {

    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final FlightSeatService seatService = mock(FlightSeatService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final FlightEventListener listener = new FlightEventListener(idempotency, seatService);

    private FlightCreatedEvent event(List<FlightCreatedEvent.Cabin> cabins) {
        FlightCreatedEvent e = new FlightCreatedEvent();
        e.setFlightNumber("WD126331");
        e.setCabins(cabins);
        return e;
    }

    private void deliver(FlightCreatedEvent e) {
        listener.handleFlightEvent(new ConsumerRecord<>("flight-events", 0, 0L, "k", e), ack);
    }

    private static final List<FlightCreatedEvent.Cabin> CABINS = List.of(
            new FlightCreatedEvent.Cabin("BUSINESS", new BigDecimal("5000.00"), 1, 2, "AC"));

    @Test
    void flightCreatedWithCabinsCreatesSeatsAndMarksProcessedAfterSuccess() {
        FlightCreatedEvent e = event(CABINS);
        when(idempotency.isProcessed(e.getEventId())).thenReturn(false);

        deliver(e);

        verify(seatService).createMissingSeats("WD126331", CABINS);
        verify(idempotency).markProcessed(e.getEventId());
        verify(ack).acknowledge();
    }

    @Test
    void secondDeliveryOfSameEventDoesNothing() {
        FlightCreatedEvent e = event(CABINS);
        when(idempotency.isProcessed(e.getEventId())).thenReturn(false, true);

        deliver(e);
        deliver(e);

        verify(seatService).createMissingSeats(any(), anyList());
        verify(idempotency).markProcessed(e.getEventId());
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void nullOrEmptyCabinsDoNothing() {
        deliver(event(null));
        deliver(event(List.of()));

        verifyNoInteractions(seatService);
        verify(idempotency, never()).markProcessed(any());
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void failureIsNotMarkedProcessedAndNotAcked() {
        FlightCreatedEvent e = event(CABINS);
        when(idempotency.isProcessed(e.getEventId())).thenReturn(false);
        when(seatService.createMissingSeats(any(), anyList())).thenThrow(new IllegalStateException("db down"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> deliver(e));

        verify(idempotency, never()).markProcessed(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void createMissingSeatsSkipsExistingSeatNumbers() {
        FlightSeatRepository repo = mock(FlightSeatRepository.class);
        FlightSeatService service = new FlightSeatService(repo, mock(FlightClient.class), 10);
        FlightSeat existing = new FlightSeat();
        existing.setSeatNumber("1A");
        when(repo.findByFlightNumber("WD126331")).thenReturn(List.of(existing));

        int created = service.createMissingSeats("wd126331", CABINS);

        assertEquals(3, created);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FlightSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        List<String> numbers = captor.getValue().stream().map(FlightSeat::getSeatNumber).toList();
        assertEquals(List.of("1C", "2A", "2C"), numbers);
        assertTrue(captor.getValue().stream().allMatch(s -> "BUSINESS".equals(s.getSeatType())));
    }
}
