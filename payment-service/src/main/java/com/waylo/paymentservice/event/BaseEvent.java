package com.waylo.paymentservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Tüm Kafka event'lerinin temel alanlarını taşıyan abstract sınıf.
 * Her event benzersiz bir eventId ile oluşturulur (idempotency kontrolü için kullanılır).
 */
public abstract class BaseEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String source;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
    }

    protected BaseEvent(String eventType, String source) {
        this();
        this.eventType = eventType;
        this.source = source;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
