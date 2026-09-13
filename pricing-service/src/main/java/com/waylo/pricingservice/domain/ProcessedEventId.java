package com.waylo.pricingservice.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * ProcessedEvent entity'sinin composite primary key sınıfı.
 */
public class ProcessedEventId implements Serializable {

    private String eventId;
    private String consumerGroup;

    public ProcessedEventId() {}

    public ProcessedEventId(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEventId that = (ProcessedEventId) o;
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(consumerGroup, that.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerGroup);
    }
}
