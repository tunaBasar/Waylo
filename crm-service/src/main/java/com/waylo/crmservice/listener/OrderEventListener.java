package com.waylo.crmservice.listener;

import com.waylo.crmservice.event.OrderEvent;
import com.waylo.crmservice.service.LoyaltyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final LoyaltyService loyaltyService;

    public OrderEventListener(LoyaltyService loyaltyService) { this.loyaltyService = loyaltyService; }

    @KafkaListener(topics = "order-events", groupId = "crm-service", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            if (record.value() instanceof OrderEvent e && "ORDER_ITEM_REDEEMED".equals(e.getEventType())) {
                int pts = loyaltyService.awardForRedeemedItem(e.getOrderItemId(), e.getCustomerId(), e.getCurrency(), e.getAmount());
                log.info("[CRM] Redeemed item {} -> {} loyalty points", e.getOrderItemId(), pts);
            }
        } catch (Exception ex) {
            log.error("[CRM] Order event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }
}
