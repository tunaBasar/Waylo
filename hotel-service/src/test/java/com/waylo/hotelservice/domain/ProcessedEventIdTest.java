package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedEventIdTest {

    @Test
    void equalsAndHashCode() {
        ProcessedEventId id1 = new ProcessedEventId("evt-1", "group-1");
        ProcessedEventId id2 = new ProcessedEventId("evt-1", "group-1");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void notEqual_whenEventIdDifferent() {
        ProcessedEventId id1 = new ProcessedEventId("evt-1", "group-1");
        ProcessedEventId id2 = new ProcessedEventId("evt-2", "group-1");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void notEqual_whenConsumerGroupDifferent() {
        ProcessedEventId id1 = new ProcessedEventId("evt-1", "group-1");
        ProcessedEventId id2 = new ProcessedEventId("evt-1", "group-2");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void notEqual_whenNull() {
        ProcessedEventId id = new ProcessedEventId("evt-1", "group-1");
        assertThat(id).isNotEqualTo(null);
    }
}
