package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer idempotency kontrolü.
 * consumer_group olarak spring.kafka.consumer.group-id kullanılır.
 * Böylece aynı event_id farklı servislerde bağımsız olarak işlenebilir.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final String consumerGroup;

    public IdempotencyService(ProcessedEventRepository processedEventRepository,
                              @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
        this.processedEventRepository = processedEventRepository;
        this.consumerGroup = consumerGroup;
    }

    /**
     * Event daha önce bu consumer group tarafından işlenmiş mi?
     */
    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    /**
     * Event'i işlenmiş olarak kaydet.
     * Transactional: iş mantığı ile aynı transaction içinde çağrılmalı.
     */
    @Transactional
    public void markProcessed(String eventId) {
        if (!isProcessed(eventId)) {
            processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup));
            log.debug("Event marked as processed: eventId={}, consumerGroup={}", eventId, consumerGroup);
        } else {
            log.debug("Event already processed, skipping: eventId={}, consumerGroup={}", eventId, consumerGroup);
        }
    }
}
