package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.PaymentCompletedEvent;
import com.waylo.notificationservice.event.PaymentRefundedEvent;
import com.waylo.notificationservice.event.PayoutFailedEvent;
import com.waylo.notificationservice.event.PayoutPaidEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;

    public PaymentEventListener(ProcessedEventRepository processedEventRepository, NotificationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        Object event = record.value();
        try {
            if (event instanceof PaymentCompletedEvent e) {
                handlePaymentCompleted(e);
            } else if (event instanceof PaymentRefundedEvent e) {
                handlePaymentRefunded(e);
            } else if (event instanceof PayoutPaidEvent e) {
                handlePayoutPaid(e);
            } else if (event instanceof PayoutFailedEvent e) {
                handlePayoutFailed(e);
            } else {
                log.warn("Bilinmeyen payment event tipi: {}", event == null ? "null" : event.getClass().getSimpleName());
            }
        } catch (RuntimeException ex) {
            // Propagate to the DefaultErrorHandler (retry, then <topic>.DLT). No ack here.
            log.error("Payment event islenirken hata: {}", ex.getMessage(), ex);
            throw ex;
        }
        ack.acknowledge();
    }

    private void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Odeme tamamlandi - paymentId={}, bookingId={}, tutar={} {}",
                event.getPaymentId(), event.getBookingId(), event.getAmount(), event.getTransactionRef());
        markProcessed(event.getEventId());
    }

    private void handlePaymentRefunded(PaymentRefundedEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Odeme iade edildi - paymentId={}, bookingId={}",
                event.getPaymentId(), event.getBookingId());
        markProcessed(event.getEventId());
    }

    /** PAYOUT_PAID: email to the requesting owner (recipientEmail of the event). Only the last 2 IBAN digits are ever shown. */
    private void handlePayoutPaid(PayoutPaidEvent e) {
        if (isDuplicate(e.getEventId())) return;
        String body = "Ödeme talebiniz tamamlandı. Tutar: " + money(e.getAmount(), e.getCurrency())
                + ". Hesap: " + maskedIban(e.getIbanLast4()) + ". Banka referansı: " + e.getReference() + ".";
        sendPayoutEmail(e.getRecipientEmail(), e.getRequestedByUserId(), e.getPayoutId(), "Ödemeniz yapıldı", body, e.getEventId());
    }

    private void handlePayoutFailed(PayoutFailedEvent e) {
        if (isDuplicate(e.getEventId())) return;
        String body = "Ödeme talebiniz işlenemedi. Tutar: " + money(e.getAmount(), e.getCurrency())
                + ". Neden: " + e.getReason() + ". Bakiyeniz iade edildi, yeni talep oluşturabilirsiniz.";
        sendPayoutEmail(e.getRecipientEmail(), e.getRequestedByUserId(), e.getPayoutId(), "Ödemeniz yapılamadı", body, e.getEventId());
    }

    private void sendPayoutEmail(String recipient, java.util.UUID userId, java.util.UUID payoutId, String subject, String body, String eventId) {
        if (recipient == null || recipient.isBlank() || userId == null) {
            log.warn("Payout event {} has no recipient email, skipping notification", eventId);
        } else {
            notificationService.send(new NotificationRequest(userId, payoutId, NotificationType.EMAIL, recipient, subject, body));
        }
        markProcessed(eventId);
    }

    static String maskedIban(String last4) {
        String two = last4 != null && last4.length() >= 2 ? last4.substring(last4.length() - 2) : "**";
        return "TR** **** **** **** **** **" + two;
    }

    static String money(BigDecimal v, String currency) {
        BigDecimal x = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_EVEN);
        return x.toPlainString() + " " + ("TRY".equals(currency) ? "TL" : currency);
    }

    private boolean isDuplicate(String eventId) {
        if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, GROUP)) {
            log.debug("Tekrar eden event atlanıyor: {}", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId) {
        processedEventRepository.save(new ProcessedEvent(eventId, GROUP));
    }
}
