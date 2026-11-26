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

    private OrderEventListener listener() { return new OrderEventListener(processed, crm, notifications, "https://waylo.test/"); }

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
        verify(notifications, times(2)).send(c.capture());
        NotificationRequest r = c.getAllValues().get(0);
        NotificationRequest push = c.getAllValues().get(1);
        assertThat(push.type()).isEqualTo(com.waylo.notificationservice.domain.NotificationType.PUSH);
        assertThat(push.customerId()).isEqualTo(customerId);
        assertThat(r.customerId()).isEqualTo(customerId);
        assertThat(r.bookingId()).isEqualTo(orderId);
        assertThat(r.recipient()).isEqualTo("c@example.test");
        assertThat(r.subject()).isEqualTo("Siparişiniz onaylandı");
        assertThat(r.body()).contains("Otel Test", "01.12.2026", "03.12.2026", "1.500,00 TL", "QR kodunuz Siparişlerim sayfasında",
                "Siparişinizi görmek için: https://waylo.test/orders");
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void confirmation_formatsTimesInIstanbul_moneyTurkish_oneItemPerLine() {
        OrderEvent e = event("ORDER_PAID");
        e.setTotalAmount(new BigDecimal("5280"));
        e.setItems(List.of(
                new OrderEvent.Item("FLIGHT", "TK100 IST-ESB <script>alert(1)</script>", "2026-11-27T08:45:00Z", null, new BigDecimal("2640")),
                new OrderEvent.Item("CAR", "Fiat Egea", "2026-11-27T09:00:00", "2026-11-29T09:00:00", new BigDecimal("2640"))));
        String body = OrderEventListener.confirmationBody(e, "https://waylo.test");

        String[] lines = body.split("\n");
        assertThat(lines[1]).startsWith("- TK100 IST-ESB").contains("(27.11.2026 11:45)", "2.640,00 TL");
        assertThat(lines[2]).contains("(27.11.2026 09:00 - 29.11.2026 09:00)");
        assertThat(body).contains("Toplam: 5.280,00 TL").endsWith("Siparişinizi görmek için: https://waylo.test/orders");
        // HTML escaping happens at delivery (NotificationServiceImpl.escape), the text body stays raw
        assertThat(body).contains("<script>");
    }

    @Test
    void when_handlesDateInstantAndGarbage() {
        assertThat(OrderEventListener.when("2026-11-27")).isEqualTo("27.11.2026");
        assertThat(OrderEventListener.when("2026-11-27T21:30:00+00:00")).isEqualTo("28.11.2026 00:30");
        assertThat(OrderEventListener.when("2026-11-27T13:30")).isEqualTo("27.11.2026 13:30");
        assertThat(OrderEventListener.when("later")).isEqualTo("later");
    }

    @Test
    void cancelledPaid_sendsRefundAmount() {
        OrderEvent e = event("ORDER_CANCELLED");
        e.setPaid(true);
        e.setRefundAmount(new BigDecimal("1234.5"));
        emailFound();

        listener().handleOrderEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(2)).send(c.capture());
        assertThat(c.getAllValues().get(0).body()).contains("İade tutarı: 1.234,50 TL");
    }

    @Test
    void cancelledPaidNoRefund_saysNoRefund() {
        OrderEvent e = event("ORDER_CANCELLED");
        e.setPaid(true);
        e.setRefundAmount(BigDecimal.ZERO);
        emailFound();

        listener().handleOrderEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(2)).send(c.capture());
        assertThat(c.getAllValues().get(0).body()).contains("iade yapılmayacaktır");
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
        // only the PUSH row (no CRM needed) is recorded, no EMAIL
        verify(notifications, times(1)).send(any());
        verify(ack).acknowledge();
    }

    @Test
    void nullOrUnknownValue_acked() {
        listener().handleOrderEvent(rec(null), ack);
        verify(ack).acknowledge();
    }
}
