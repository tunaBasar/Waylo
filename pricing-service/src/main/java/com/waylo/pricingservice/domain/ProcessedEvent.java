package com.waylo.pricingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Kafka consumer idempotency kontrolü için işlenmiş event kaydı.
 * Composite PK: (eventId, consumerGroup) - aynı event farklı
 * consumer group'larda bağımsız olarak işlenebilir.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, length = 128)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public String getConsumerGroup() { return consumerGroup; }
    public Instant getProcessedAt() { return processedAt; }
}
