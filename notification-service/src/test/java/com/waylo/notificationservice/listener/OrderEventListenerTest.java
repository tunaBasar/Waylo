package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.OrderEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock ProcessedEventRepository processed;
    @Mock CrmServiceClient crm;
    @Mock NotificationService notifications;
    @Mock Acknowledgment ack;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private OrderEventListener listener() { return new OrderEventListener(processed, crm, notifications); }

    private static ConsumerRecord<String, Object> rec(Object v) { return new ConsumerRecord<>("order-events", 0, 0L, "k", v); }

    private OrderEvent event(String type) {
        OrderEvent e = new OrderEvent();
        e.setEventType(type);
        e.setOrderId(orderId);
        e.setCustomerId(customerId);
        e.setCurrency("TRY");
        return e;
    }

    private void emailFound() {
        when(crm.getById(customerId)).thenReturn(new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(customerId, "A", "B", "c@example.test", null)));
    }

    @Test
    void paid_sendsConfirmationWithItemsAndTotal_noQr() {
        OrderEvent e = event("ORDER_PAID");
        e.setTotalAmount(new BigDecimal("1500"));
        e.setItems(List.of(new OrderEvent.Item("HOTEL", "Otel Test", "2026-12-01", "2026-12-03", new BigDecimal("1500"))));
        emailFound();

        listener().handleOrderEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        NotificationRequest r = c.getValue();
        assertThat(r.customerId()).isEqualTo(customerId);
        assertThat(r.bookingId()).isEqualTo(orderId);
        assertThat(r.recipient()).isEqualTo("c@example.test");
        assertThat(r.subject()).isEqualTo("Siparişiniz onaylandı");
        assertThat(r.body()).contains("Otel Test", "2026-12-01", "2026-12-03", "1500.00 TL", "QR kodunuz Siparişlerim sayfasında");
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void cancelledPaid_sendsRefundAmount() {
        OrderEvent e = event("ORDER_CANCELLED");
        e.setPaid(true);
        e.setRefundAmount(new BigDecimal("1234.5"));
        emailFound();

        listener().handleOrderEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        assertThat(c.getValue().body()).contains("İade tutarı: 1234.50 TL");
    }

    @Test
    void cancelledPaidNoRefund_saysNoRefund() {
        OrderEvent e = event("ORDER_CANCELLED");
        e.setPaid(true);
        e.setRefundAmount(BigDecimal.ZERO);
        emailFound();

        listener().handleOrderEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        assertThat(c.getValue().body()).contains("iade yapılmayacaktır");
    }

    @Test
    void cancelledUnpaid_sendsNothing() {
        OrderEvent e = event("ORDER_CANCELLED");
        listener().handleOrderEvent(rec(e), ack);
        verifyNoInteractions(notifications, crm);
        verify(ack).acknowledge();
    }

    @Test
    void otherTypes_ignored() {
        listener().handleOrderEvent(rec(event("ORDER_CREATED")), ack);
        listener().handleOrderEvent(rec(event("ORDER_ITEM_REDEEMED")), ack);
        verifyNoInteractions(notifications, crm);
    }

    @Test
    void duplicateEvent_skipped() {
        OrderEvent e = event("ORDER_PAID");
        when(processed.existsByEventIdAndConsumerGroup(e.getEventId(), "notification-service")).thenReturn(true);
        listener().handleOrderEvent(rec(e), ack);
        verifyNoInteractions(notifications, crm);
        verify(processed, never()).save(any());
    }

    @Test
    void crmDown_failSoft_stillAcks() {
        when(crm.getById(customerId)).thenThrow(new RuntimeException("down"));
        listener().handleOrderEvent(rec(event("ORDER_PAID")), ack);
        verify(notifications, never()).send(any());
        verify(ack).acknowledge();
    }

    @Test
    void nullOrUnknownValue_acked() {
        listener().handleOrderEvent(rec(null), ack);
        verify(ack).acknowledge();
    }
}
