package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedEventTest {

    @Test
    void constructor_setsFields() {
        ProcessedEvent event = new ProcessedEvent("evt-123", "hotel-consumer");

        assertThat(event.getEventId()).isEqualTo("evt-123");
        assertThat(event.getConsumerGroup()).isEqualTo("hotel-consumer");
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    void processedAt_isCurrentTime() {
        Instant before = Instant.now();
        ProcessedEvent event = new ProcessedEvent("evt-1", "group-1");
        Instant after = Instant.now();

        assertThat(event.getProcessedAt()).isAfterOrEqualTo(before);
        assertThat(event.getProcessedAt()).isBeforeOrEqualTo(after);
    }
}
