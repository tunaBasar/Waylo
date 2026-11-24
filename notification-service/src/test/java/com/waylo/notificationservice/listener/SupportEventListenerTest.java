package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.config.KafkaConsumerConfig;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.SupportCaseUpdatedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupportEventListenerTest {

    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final CrmServiceClient crm = mock(CrmServiceClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final SupportEventListener listener = new SupportEventListener(processed, crm, notifications);
    private final UUID customerId = UUID.randomUUID();

    private SupportCaseUpdatedEvent event(String kind) {
        SupportCaseUpdatedEvent e = new SupportCaseUpdatedEvent();
        e.setCaseId(UUID.randomUUID());
        e.setCustomerId(customerId);
        e.setOrderId(UUID.randomUUID());
        e.setSubject("Bag question");
        e.setKind(kind);
        e.setAuthorRole("PARTNER");
        return e;
    }

    private void handle(SupportCaseUpdatedEvent e) {
        listener.handleSupportEvent(new ConsumerRecord<>("support-events", 0, 0L, "k", e), ack);
    }

    @Test
    void answeredSendsEmailToCrmAddressWithoutReplyText() {
        when(crm.getById(customerId)).thenReturn(new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(customerId, "Ada", "L", "ada@waylo.com", null)));
        handle(event("ANSWERED"));

        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        NotificationRequest r = c.getValue();
        assertThat(r.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(r.recipient()).isEqualTo("ada@waylo.com");
        assertThat(r.customerId()).isEqualTo(customerId);
        assertThat(r.subject()).isEqualTo("Destek talebiniz yanıtlandı");
        assertThat(r.body()).contains("Bag question").contains("Yanıtı görmek için Waylo hesabınızda Destek sayfasını açın.");
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void closedUsesClosedSubject() {
        when(crm.getById(customerId)).thenReturn(new CrmServiceClient.CustomerLookupResponse(true,
                new CrmServiceClient.CustomerDto(customerId, "Ada", "L", "ada@waylo.com", null)));
        handle(event("CLOSED"));
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        assertThat(c.getValue().subject()).isEqualTo("Destek talebiniz kapatıldı");
    }

    @Test
    void duplicateEventIdIsIgnored() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        handle(event("ANSWERED"));
        verifyNoInteractions(crm, notifications);
        verify(processed, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void unknownCustomerFailsSoft() {
        when(crm.getById(customerId)).thenThrow(new RuntimeException("404 not found"));
        handle(event("ANSWERED"));
        verifyNoInteractions(notifications);
        verify(ack).acknowledge();
    }

    @Test
    void typeMappingTokenExists() throws Exception {
        Field f = KafkaConsumerConfig.class.getDeclaredField("TYPE_MAPPINGS");
        f.setAccessible(true);
        assertThat((String) f.get(null)).contains(
                "supportCaseUpdated:com.waylo.notificationservice.event.SupportCaseUpdatedEvent");
    }
}
