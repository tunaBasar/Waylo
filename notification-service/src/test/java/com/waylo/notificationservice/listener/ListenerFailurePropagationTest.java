package com.waylo.notificationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.client.PackageBookingServiceClient;
import com.waylo.notificationservice.event.BookingCreatedEvent;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.event.OrderEvent;
import com.waylo.notificationservice.event.PaymentCompletedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.EmailService;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Outer catch-all no longer swallows: a processing error propagates to the DefaultErrorHandler (retry, then DLT)
 * and the record is NOT acked or marked processed. Duplicates are acked without processing. Best-effort inner
 * catches (customer lookup) still ack.
 */
class ListenerFailurePropagationTest {

    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final CrmServiceClient crm = mock(CrmServiceClient.class);
    private final PackageBookingServiceClient pkg = mock(PackageBookingServiceClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static <T> ConsumerRecord<String, Object> rec(String topic, Object v) {
        return new ConsumerRecord<>(topic, 0, 0L, "k", v);
    }

    private CrmServiceClient.CustomerLookupResponse lookup() {
        return new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(UUID.randomUUID(), "A", "B", "a@b.com", "1"));
    }

    // ---- booking ----
    @Test
    void booking_sendFailure_propagates_notAcked_notMarked() {
        BookingCreatedEvent e = new BookingCreatedEvent();
        e.setCustomerId(UUID.randomUUID());
        when(crm.getById(any())).thenReturn(lookup());
        doThrow(new IllegalStateException("smtp down")).when(notifications).send(any());
        var l = new BookingEventListener(processed, crm, notifications);

        assertThrows(IllegalStateException.class, () -> l.handleBookingEvent(rec("booking-events", e), ack));

        verify(ack, never()).acknowledge();
        verify(processed, never()).save(any());
    }

    @Test
    void booking_duplicate_ackedWithoutProcessing() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        new BookingEventListener(processed, crm, notifications)
                .handleBookingEvent(rec("booking-events", new BookingCreatedEvent()), ack);
        verifyNoInteractions(crm, notifications);
        verify(ack).acknowledge();
    }

    @Test
    void booking_crmLookupFailure_isBestEffort_stillAcks() {
        BookingCreatedEvent e = new BookingCreatedEvent();
        e.setCustomerId(UUID.randomUUID());
        when(crm.getById(any())).thenThrow(new RuntimeException("crm down"));
        new BookingEventListener(processed, crm, notifications).handleBookingEvent(rec("booking-events", e), ack);
        verify(notifications, never()).send(any());
        verify(ack).acknowledge();
    }

    // ---- flight ----
    @Test
    void flight_sendFailure_propagates_notAcked_notMarked() {
        FlightCancelledEvent e = new FlightCancelledEvent();
        e.setFlightNumber("WY1");
        var item = new PackageBookingServiceClient.FlightItemDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(pkg.getFlightItems(any(), any())).thenReturn(
                new PackageBookingServiceClient.FlightItemListResponse(true, java.util.List.of(item)));
        when(crm.getById(any())).thenReturn(lookup());
        doThrow(new IllegalStateException("smtp down")).when(notifications).send(any());
        var l = new FlightEventListener(processed, pkg, crm, notifications);

        assertThrows(IllegalStateException.class, () -> l.handleFlightEvent(rec("flight-events", e), ack));

        verify(ack, never()).acknowledge();
        verify(processed, never()).save(any());
    }

    @Test
    void flight_duplicate_ackedWithoutProcessing() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        new FlightEventListener(processed, pkg, crm, notifications)
                .handleFlightEvent(rec("flight-events", new FlightCancelledEvent()), ack);
        verifyNoInteractions(pkg, crm, notifications);
        verify(ack).acknowledge();
    }

    @Test
    void flight_bookingLookupFailure_isBestEffort_stillAcks() {
        FlightCancelledEvent e = new FlightCancelledEvent();
        when(pkg.getFlightItems(any(), any())).thenThrow(new RuntimeException("down"));
        new FlightEventListener(processed, pkg, crm, notifications).handleFlightEvent(rec("flight-events", e), ack);
        verify(ack).acknowledge();
    }

    // ---- order ----
    private OrderEvent paidOrder() {
        OrderEvent e = new OrderEvent();
        e.setEventType("ORDER_PAID");
        e.setOrderId(UUID.randomUUID());
        e.setCustomerId(UUID.randomUUID());
        e.setCurrency("TRY");
        e.setTotalAmount(BigDecimal.TEN);
        return e;
    }

    @Test
    void order_sendFailure_propagates_notAcked_notMarked() {
        when(crm.getById(any())).thenReturn(lookup());
        doThrow(new IllegalStateException("smtp down")).when(notifications).send(any());
        var l = new OrderEventListener(processed, crm, notifications);

        assertThrows(IllegalStateException.class, () -> l.handleOrderEvent(rec("order-events", paidOrder()), ack));

        verify(ack, never()).acknowledge();
        verify(processed, never()).save(any());
    }

    @Test
    void order_duplicate_ackedWithoutProcessing() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        new OrderEventListener(processed, crm, notifications).handleOrderEvent(rec("order-events", paidOrder()), ack);
        verifyNoInteractions(crm, notifications);
        verify(ack).acknowledge();
    }

    @Test
    void order_crmLookupFailure_isBestEffort_stillSendsPushMarksAndAcks() {
        when(crm.getById(any())).thenThrow(new RuntimeException("crm down"));
        new OrderEventListener(processed, crm, notifications).handleOrderEvent(rec("order-events", paidOrder()), ack);
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    // ---- payment ----
    @Test
    void payment_repositoryFailure_propagates_notAcked() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(processed.save(any())).thenThrow(new IllegalStateException("db down"));
        var l = new PaymentEventListener(processed, mock(NotificationService.class));

        assertThrows(IllegalStateException.class,
                () -> l.handlePaymentEvent(rec("payment-events", new PaymentCompletedEvent()), ack));

        verify(ack, never()).acknowledge();
    }

    @Test
    void payment_duplicate_ackedWithoutProcessing() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        new PaymentEventListener(processed, mock(NotificationService.class)).handlePaymentEvent(rec("payment-events", new PaymentCompletedEvent()), ack);
        verify(processed, never()).save(any());
        verify(ack).acknowledge();
    }

    // ---- auth (String payload, no Acknowledgment: container acks via error handler / MANUAL absence) ----
    @Test
    void auth_emailFailure_propagates() {
        EmailService email = mock(EmailService.class);
        doThrow(new IllegalStateException("smtp down")).when(email).sendWelcomeEmail(any(), any());
        var l = new AuthEventListener(email, new ObjectMapper().findAndRegisterModules(), mock(ProcessedEventRepository.class), java.time.Duration.ofHours(24));
        assertThrows(IllegalStateException.class, () -> l.handleAuthEvent(
                "{\"eventType\":\"WELCOME\",\"userId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"" + java.time.Instant.now() + "\",\"email\":\"a@b.com\",\"name\":\"A\"}"));
    }

    @Test
    void auth_malformedJson_isNonRetryableIllegalArgument() {
        var l = new AuthEventListener(mock(EmailService.class), new ObjectMapper().findAndRegisterModules(), mock(ProcessedEventRepository.class), java.time.Duration.ofHours(24));
        assertThrows(IllegalArgumentException.class, () -> l.handleAuthEvent("{nope"));
    }
}
