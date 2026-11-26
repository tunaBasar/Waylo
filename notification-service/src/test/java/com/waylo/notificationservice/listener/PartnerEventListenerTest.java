package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.AuthOrganizationClient;
import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.OrderEvent;
import com.waylo.notificationservice.event.SupportCaseUpdatedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartnerEventListenerTest {

    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final AuthOrganizationClient auth = mock(AuthOrganizationClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final PartnerEventListener listener = new PartnerEventListener(processed, auth, notifications, "https://waylo.test/", java.time.Duration.ofHours(24));
    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private OrderEvent order(String type, boolean paid) {
        OrderEvent e = new OrderEvent();
        e.setEventType(type);
        e.setOrderId(orderId);
        e.setCustomerId(UUID.randomUUID());
        e.setCurrency("TRY");
        e.setPaid(paid);
        e.setRefundAmount(new BigDecimal("900"));
        e.setItems(List.of(
                new OrderEvent.Item("HOTEL", "Otel A", "2026-12-01", "2026-12-03", new BigDecimal("1500"), orgA, new BigDecimal("900")),
                new OrderEvent.Item("CAR", "Araba B", "2026-12-01T09:00:00", null, new BigDecimal("2640"), orgB, null)));
        return e;
    }

    private SupportCaseUpdatedEvent support(String kind, UUID org) {
        SupportCaseUpdatedEvent e = new SupportCaseUpdatedEvent();
        e.setCaseId(UUID.randomUUID());
        e.setCustomerId(UUID.randomUUID());
        e.setOrderId(orderId);
        e.setSubject("Geç kaldı");
        e.setKind(kind);
        e.setAuthorRole("CUSTOMER");
        e.setSupplierOrganizationId(org);
        return e;
    }

    private void handleOrder(OrderEvent e) { listener.handleOrderEvent(rec("order-events", e, System.currentTimeMillis()), ack); }
    private void handleSupport(SupportCaseUpdatedEvent e) { listener.handleSupportEvent(rec("support-events", e, System.currentTimeMillis()), ack); }

    private static ConsumerRecord<String, Object> rec(String topic, Object v, long ts) {
        return new ConsumerRecord<>(topic, 0, 0L, ts, org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0, 0, "k", v, new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    @Test
    void eventOlderThan24hByRecordTimestampIsSkippedButMarkedProcessed() {
        long old = System.currentTimeMillis() - 48L * 3600_000;
        listener.handleOrderEvent(rec("order-events", order("ORDER_PAID", false), old), ack);
        listener.handleSupportEvent(rec("support-events", support("CREATED", orgA), old), ack);
        verifyNoInteractions(notifications, auth);
        verify(processed, times(2)).save(any());
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void eventWithoutRecordTimestampCountsAsTooOld() {
        listener.handleSupportEvent(rec("support-events", support("CREATED", orgA), -1L), ack);
        verifyNoInteractions(notifications, auth);
        verify(processed).save(any());
    }

    private List<NotificationRequest> sent(int n) {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(n)).send(c.capture());
        return c.getAllValues();
    }

    @Test
    void paidOrderSendsOneMailPerOrgWithOnlyThatOrgsItems() {
        when(auth.contacts(orgA)).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("a@partner.test")));
        when(auth.contacts(orgB)).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("b@partner.test")));
        handleOrder(order("ORDER_PAID", false));

        List<NotificationRequest> r = sent(2);
        NotificationRequest a = r.stream().filter(x -> x.recipient().equals("a@partner.test")).findFirst().orElseThrow();
        NotificationRequest b = r.stream().filter(x -> x.recipient().equals("b@partner.test")).findFirst().orElseThrow();
        assertThat(a.subject()).isEqualTo("Yeni rezervasyon");
        assertThat(a.body()).contains("Otel A").contains("1.500,00 TL").contains("https://waylo.test/partner/orders")
                .doesNotContain("Araba B").doesNotContain("2.640,00");
        assertThat(b.body()).contains("Araba B").doesNotContain("Otel A");
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void cancelledPaidOrderMentionsRefundOutcome() {
        when(auth.contacts(orgA)).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("a@partner.test")));
        when(auth.contacts(orgB)).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("b@partner.test")));
        handleOrder(order("ORDER_CANCELLED", true));

        List<NotificationRequest> r = sent(2);
        NotificationRequest a = r.stream().filter(x -> x.recipient().equals("a@partner.test")).findFirst().orElseThrow();
        NotificationRequest b = r.stream().filter(x -> x.recipient().equals("b@partner.test")).findFirst().orElseThrow();
        assertThat(a.subject()).isEqualTo("Rezervasyon iptal edildi");
        assertThat(a.body()).contains("900,00 TL");
        assertThat(b.body()).contains("iade yapılmadı");
    }

    @Test
    void unpaidCancelAndOtherTypesSendNothingButAck() {
        handleOrder(order("ORDER_CANCELLED", false));
        handleOrder(order("ORDER_CREATED", false));
        verifyNoInteractions(notifications, auth);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void contactsLookupFailureSkipsMailAndStillAcks() {
        when(auth.contacts(any())).thenThrow(new RuntimeException("503"));
        handleOrder(order("ORDER_PAID", false));
        verifyNoInteractions(notifications);
        verify(ack).acknowledge();
    }

    @Test
    void duplicateEventIsNotMailedAgain_andUsesOwnConsumerGroup() {
        when(processed.existsByEventIdAndConsumerGroup(any(), eq("notification-service-partner"))).thenReturn(true);
        handleOrder(order("ORDER_PAID", false));
        handleSupport(support("CREATED", orgA));
        verifyNoInteractions(notifications, auth);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void processedRowIsSavedUnderPartnerGroup() {
        when(auth.contacts(any())).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("a@partner.test")));
        handleSupport(support("CREATED", orgA));
        ArgumentCaptor<com.waylo.notificationservice.domain.ProcessedEvent> c = ArgumentCaptor.forClass(com.waylo.notificationservice.domain.ProcessedEvent.class);
        verify(processed).save(c.capture());
        assertThat(c.getValue().getConsumerGroup()).isEqualTo("notification-service-partner");
    }

    @Test
    void createdAndCustomerMessageMailPartnerWithSupportLink() {
        when(auth.contacts(orgA)).thenReturn(new AuthOrganizationClient.OrganizationContacts(List.of("a@partner.test", "o@partner.test")));
        handleSupport(support("CREATED", orgA));
        handleSupport(support("CUSTOMER_MESSAGE", orgA));
        List<NotificationRequest> r = sent(4);
        assertThat(r).allSatisfy(x -> assertThat(x.body()).contains("https://waylo.test/partner/support").contains("Geç kaldı"));
        assertThat(r.get(0).subject()).isEqualTo("Yeni müşteri destek talebi");
        assertThat(r.get(2).subject()).isEqualTo("Destek talebinde yeni müşteri mesajı");
    }

    @Test
    void supportEventWithoutOrganizationOrAnsweredKindSendsNothing() {
        handleSupport(support("CREATED", null));
        handleSupport(support("ANSWERED", orgA));
        verifyNoInteractions(notifications, auth);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void customerListenerIgnoresCreatedAndCustomerMessage() {
        CrmServiceClient crm = mock(CrmServiceClient.class);
        SupportEventListener customerListener = new SupportEventListener(processed, crm, notifications);
        customerListener.handleSupportEvent(new ConsumerRecord<>("support-events", 0, 0L, "k", support("CREATED", orgA)), ack);
        customerListener.handleSupportEvent(new ConsumerRecord<>("support-events", 0, 0L, "k", support("CUSTOMER_MESSAGE", orgA)), ack);
        verifyNoInteractions(notifications, crm);
        verify(processed, never()).save(any());
        verify(ack, times(2)).acknowledge();
    }
}
