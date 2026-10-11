package com.waylo.paymentservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Publishing PaymentCompletedEvent: paymentId={}, bookingId={}", event.getPaymentId(), event.getBookingId());
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, event.getPaymentId().toString(), event);
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Publishing PaymentRefundedEvent: paymentId={}", event.getPaymentId());
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, event.getPaymentId().toString(), event);
    }
}
