package com.waylo.paymentservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes events to the transactional outbox (joins the caller's transaction). OutboxRelay sends them to Kafka. */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private final OutboxWriter outbox;

    public PaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Queueing PaymentCompletedEvent: {}", event.getPaymentId());
        outbox.write("PAYMENT", event.getPaymentId().toString(), KafkaTopics.PAYMENT_EVENTS, event.getPaymentId().toString(), event);
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Queueing PaymentRefundedEvent: {}", event.getPaymentId());
        outbox.write("PAYMENT", event.getPaymentId().toString(), KafkaTopics.PAYMENT_EVENTS, event.getPaymentId().toString(), event);
    }

    public void publishPayoutPaid(PayoutPaidEvent event) {
        log.info("Queueing PayoutPaidEvent: {}", event.getPayoutId());
        outbox.write("PAYOUT", event.getPayoutId().toString(), KafkaTopics.PAYMENT_EVENTS, event.getOrganizationId().toString(), event);
    }

    public void publishPayoutFailed(PayoutFailedEvent event) {
        log.info("Queueing PayoutFailedEvent: {}", event.getPayoutId());
        outbox.write("PAYOUT", event.getPayoutId().toString(), KafkaTopics.PAYMENT_EVENTS, event.getOrganizationId().toString(), event);
    }
}
