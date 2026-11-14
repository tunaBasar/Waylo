package com.waylo.packagebookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes events to the transactional outbox (joins the caller's transaction). OutboxRelay sends them to Kafka. */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final OutboxWriter outbox;

    public OrderEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Queueing OrderCreatedEvent: {}", event.getOrderId());
        outbox.write("ORDER", event.getOrderId().toString(), KafkaTopics.ORDER_EVENTS, event.getOrderId().toString(), event);
    }

    public void publishOrderPaid(OrderPaidEvent event) {
        log.info("Queueing OrderPaidEvent: {}", event.getOrderId());
        outbox.write("ORDER", event.getOrderId().toString(), KafkaTopics.ORDER_EVENTS, event.getOrderId().toString(), event);
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.info("Queueing OrderCancelledEvent: {}", event.getOrderId());
        outbox.write("ORDER", event.getOrderId().toString(), KafkaTopics.ORDER_EVENTS, event.getOrderId().toString(), event);
    }

    public void publishOrderItemRedeemed(OrderItemRedeemedEvent event) {
        log.info("Queueing OrderItemRedeemedEvent: {}", event.getOrderId());
        outbox.write("ORDER", event.getOrderId().toString(), KafkaTopics.ORDER_EVENTS, event.getOrderId().toString(), event);
    }
}
