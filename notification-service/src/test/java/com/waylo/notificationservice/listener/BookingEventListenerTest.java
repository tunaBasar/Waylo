package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.BookingCancelledEvent;
import com.waylo.notificationservice.event.BookingCreatedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private CrmServiceClient crmServiceClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Acknowledgment ack;

    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    private BookingEventListener newListener() {
        return new BookingEventListener(processedEventRepository, crmServiceClient, notificationService);
    }

    private static ConsumerRecord<String, Object> rec(Object value) {
        return new ConsumerRecord<>("booking-events", 0, 0L, "key", value);
    }

    private BookingCreatedEvent createdEvent() {
        BookingCreatedEvent e = new BookingCreatedEvent();
        e.setBookingId(bookingId);
        e.setCustomerId(customerId);
        return e;
    }

    private BookingCancelledEvent cancelledEvent() {
        BookingCancelledEvent e = new BookingCancelledEvent();
        e.setBookingId(bookingId);
        e.setCustomerId(customerId);
        return e;
    }

    private CrmServiceClient.CustomerLookupResponse lookupWithEmail(String email) {
        return new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(customerId, "Ada", "Lovelace", email, "5551234567"));
    }

    // --- BookingCreatedEvent ---

    @Test
    void handleBookingCreated_sendsEmailWithCustomerAddress() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(crmServiceClient.getById(customerId)).thenReturn(lookupWithEmail("ada@waylo.com"));

        newListener().handleBookingEvent(rec(createdEvent()), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        NotificationRequest req = captor.getValue();
        assertThat(req.customerId()).isEqualTo(customerId);
        assertThat(req.bookingId()).isEqualTo(bookingId);
        assertThat(req.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(req.recipient()).isEqualTo("ada@waylo.com");
        assertThat(req.body()).contains(bookingId.toString());
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void handleBookingCreated_duplicateEvent_skipsEverything() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);

        newListener().handleBookingEvent(rec(createdEvent()), ack);

        verifyNoInteractions(crmServiceClient, notificationService);
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void handleBookingCreated_crmLookupThrows_doesNotSendButStillMarksProcessedAndAcks() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(crmServiceClient.getById(customerId)).thenThrow(new RuntimeException("crm-service down"));

        newListener().handleBookingEvent(rec(createdEvent()), ack);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void handleBookingCreated_customerHasNoEmail_doesNotSend() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(crmServiceClient.getById(customerId)).thenReturn(lookupWithEmail(null));

        newListener().handleBookingEvent(rec(createdEvent()), ack);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository).save(any());
    }

    @Test
    void handleBookingCreated_lookupNotSuccessful_doesNotSend() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(crmServiceClient.getById(customerId))
                .thenReturn(new CrmServiceClient.CustomerLookupResponse(false, null));

        newListener().handleBookingEvent(rec(createdEvent()), ack);

        verify(notificationService, never()).send(any());
    }

    // --- BookingCancelledEvent ---

    @Test
    void handleBookingCancelled_sendsEmailWithCancellationSubject() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(crmServiceClient.getById(customerId)).thenReturn(lookupWithEmail("ada@waylo.com"));

        newListener().handleBookingEvent(rec(cancelledEvent()), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Rezervasyonunuz iptal edildi");
        assertThat(captor.getValue().recipient()).isEqualTo("ada@waylo.com");
    }

    @Test
    void handleBookingCancelled_duplicateEvent_skipsEverything() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);

        newListener().handleBookingEvent(rec(cancelledEvent()), ack);

        verifyNoInteractions(crmServiceClient, notificationService);
        verify(ack).acknowledge();
    }

    // --- unknown event type ---

    @Test
    void handleBookingEvent_unknownType_acksWithoutError() {
        newListener().handleBookingEvent(rec(new Object()), ack);

        verifyNoInteractions(crmServiceClient, notificationService, processedEventRepository);
        verify(ack).acknowledge();
    }
}
