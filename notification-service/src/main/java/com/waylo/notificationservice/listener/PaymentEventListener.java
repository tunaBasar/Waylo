package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.event.PaymentCompletedEvent;
import com.waylo.notificationservice.event.PaymentRefundedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;

    public PaymentEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "payment-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvent(@Payload Object event, Acknowledgment ack) {
        try {
            if (event instanceof PaymentCompletedEvent e) {
                handlePaymentCompleted(e);
            } else if (event instanceof PaymentRefundedEvent e) {
                handlePaymentRefunded(e);
            } else {
                log.warn("Bilinmeyen payment event tipi: {}", event.getClass().getSimpleName());
            }
        } catch (Exception ex) {
            log.error("Payment event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
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
