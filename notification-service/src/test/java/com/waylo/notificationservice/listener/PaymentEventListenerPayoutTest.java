package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.PayoutFailedEvent;
import com.waylo.notificationservice.event.PayoutPaidEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventListenerPayoutTest {

    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationService notifications = mock(NotificationService.class);
    Acknowledgment ack = mock(Acknowledgment.class);
    PaymentEventListener listener = new PaymentEventListener(processed, notifications);

    UUID payoutId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    private static ConsumerRecord<String, Object> rec(Object v) { return new ConsumerRecord<>("payment-events", 0, 0L, "k", v); }

    private PayoutPaidEvent paid(String email) {
        PayoutPaidEvent e = new PayoutPaidEvent();
        e.setPayoutId(payoutId);
        e.setOrganizationId(UUID.randomUUID());
        e.setRequestedByUserId(userId);
        e.setRecipientEmail(email);
        e.setAmount(new BigDecimal("900"));
        e.setCurrency("TRY");
        e.setIbanLast4("1326");
        e.setReference("TRF-2026-11-24-001");
        return e;
    }

    @Test
    void paid_sendsOneEmail_withMaskedIbanAndReference() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        listener.handlePaymentEvent(rec(paid("owner@example.test")), ack);

        ArgumentCaptor<NotificationRequest> req = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(req.capture());
        NotificationRequest r = req.getValue();
        assertThat(r.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(r.recipient()).isEqualTo("owner@example.test");
        assertThat(r.customerId()).isEqualTo(userId);
        assertThat(r.bookingId()).isEqualTo(payoutId);
        assertThat(r.body()).contains("900.00 TL").contains("TRF-2026-11-24-001").contains("TR** **** **** **** **** **26");
        // never more than the last 2 digits of the IBAN
        assertThat(r.body()).doesNotContain("1326").doesNotContain("0006");
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void duplicateEventId_sendsNothing_butAcks() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(true);
        listener.handlePaymentEvent(rec(paid("owner@example.test")), ack);

        verify(notifications, never()).send(any());
        verify(processed, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void nullEmail_skipsSending_acksAndRecordsProcessed() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        listener.handlePaymentEvent(rec(paid(null)), ack);

        verify(notifications, never()).send(any());
        verify(processed).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void failed_bodyContainsReasonAndRefundedBalanceNote() {
        when(processed.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        PayoutFailedEvent e = new PayoutFailedEvent();
        e.setPayoutId(payoutId);
        e.setRequestedByUserId(userId);
        e.setRecipientEmail("owner@example.test");
        e.setAmount(new BigDecimal("250.5"));
        e.setCurrency("TRY");
        e.setIbanLast4("1326");
        e.setReason("Hesap sahibi adi uyusmuyor");

        listener.handlePaymentEvent(rec(e), ack);

        ArgumentCaptor<NotificationRequest> req = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(req.capture());
        assertThat(req.getValue().body()).contains("Hesap sahibi adi uyusmuyor").contains("250.50 TL").contains("Bakiyeniz iade edildi");
        assertThat(req.getValue().subject()).isEqualTo("Ödemeniz yapılamadı");
        verify(ack).acknowledge();
    }
}
