package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.client.PackageBookingServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightEventListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private PackageBookingServiceClient packageBookingServiceClient;
    @Mock
    private CrmServiceClient crmServiceClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Acknowledgment ack;

    private final UUID flightId = UUID.randomUUID();
    private final UUID customerId1 = UUID.randomUUID();
    private final UUID customerId2 = UUID.randomUUID();
    private final UUID bookingId1 = UUID.randomUUID();
    private final UUID bookingId2 = UUID.randomUUID();

    private FlightEventListener newListener() {
        return new FlightEventListener(processedEventRepository, packageBookingServiceClient,
                crmServiceClient, notificationService);
    }

    private static ConsumerRecord<String, Object> rec(Object value) {
        return new ConsumerRecord<>("flight-events", 0, 0L, "key", value);
    }

    private FlightCancelledEvent cancelledEvent() {
        FlightCancelledEvent e = new FlightCancelledEvent();
        e.setFlightId(flightId);
        e.setFlightNumber("WY123");
        return e;
    }

    private CrmServiceClient.CustomerLookupResponse lookupWithEmail(UUID id, String email) {
        return new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(id, "Ada", "Lovelace", email, "5551234567"));
    }

    @Test
    void handleFlightCancelled_notifiesEveryAffectedCustomer() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(packageBookingServiceClient.getByFlightId(flightId)).thenReturn(
                new PackageBookingServiceClient.BookingListResponse(true, List.of(
                        new PackageBookingServiceClient.BookingDto(bookingId1, customerId1, flightId),
                        new PackageBookingServiceClient.BookingDto(bookingId2, customerId2, flightId))));
        when(crmServiceClient.getById(customerId1)).thenReturn(lookupWithEmail(customerId1, "a@waylo.com"));
        when(crmServiceClient.getById(customerId2)).thenReturn(lookupWithEmail(customerId2, "b@waylo.com"));

        newListener().handleFlightEvent(rec(cancelledEvent()), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(2)).send(captor.capture());
        List<NotificationRequest> sent = captor.getAllValues();
        assertThat(sent).extracting(NotificationRequest::recipient)
                .containsExactlyInAnyOrder("a@waylo.com", "b@waylo.com");
        assertThat(sent).allMatch(r -> r.type() == NotificationType.EMAIL);
        assertThat(sent).allMatch(r -> r.body().contains("WY123"));
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void handleFlightCancelled_oneCustomerLookupFails_stillNotifiesTheOther() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(packageBookingServiceClient.getByFlightId(flightId)).thenReturn(
                new PackageBookingServiceClient.BookingListResponse(true, List.of(
                        new PackageBookingServiceClient.BookingDto(bookingId1, customerId1, flightId),
                        new PackageBookingServiceClient.BookingDto(bookingId2, customerId2, flightId))));
        when(crmServiceClient.getById(customerId1)).thenThrow(new RuntimeException("crm down"));
        when(crmServiceClient.getById(customerId2)).thenReturn(lookupWithEmail(customerId2, "b@waylo.com"));

        newListener().handleFlightEvent(rec(cancelledEvent()), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(1)).send(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("b@waylo.com");
        verify(processedEventRepository).save(any());
    }

    @Test
    void handleFlightCancelled_bookingLookupFails_sendsNothingButStillMarksProcessed() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(packageBookingServiceClient.getByFlightId(flightId)).thenThrow(new RuntimeException("package-booking-service down"));

        newListener().handleFlightEvent(rec(cancelledEvent()), ack);

        verify(notificationService, never()).send(any());
        verifyNoInteractions(crmServiceClient);
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void handleFlightCancelled_noAffectedBookings_sendsNothing() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(packageBookingServiceClient.getByFlightId(flightId)).thenReturn(
                new PackageBookingServiceClient.BookingListResponse(true, List.of()));

        newListener().handleFlightEvent(rec(cancelledEvent()), ack);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository).save(any());
    }

    @Test
    void handleFlightCancelled_duplicateEvent_skipsEverything() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);

        newListener().handleFlightEvent(rec(cancelledEvent()), ack);

        verifyNoInteractions(packageBookingServiceClient, crmServiceClient, notificationService);
        verify(ack).acknowledge();
    }

    @Test
    void handleFlightEvent_unknownType_acksWithoutError() {
        newListener().handleFlightEvent(rec(new Object()), ack);

        verifyNoInteractions(packageBookingServiceClient, crmServiceClient, notificationService, processedEventRepository);
        verify(ack).acknowledge();
    }
}
