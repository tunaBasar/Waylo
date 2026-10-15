package com.waylo.hotelservice.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEventTest {

    private final TestEvent event = new TestEvent();
    private final TestEvent eventWithParams = new TestEvent("CREATE_HOTEL", "hotel-service");

    @Test
    void defaultConstructor_generatesEventIdAndTimestamp() {
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void constructorWithParams_setsFields() {
        assertThat(eventWithParams.getEventType()).isEqualTo("CREATE_HOTEL");
        assertThat(eventWithParams.getSource()).isEqualTo("hotel-service");
    }

    @Test
    void gettersAndSetters() {
        event.setEventType("UPDATE_ROOM");
        assertThat(event.getEventType()).isEqualTo("UPDATE_ROOM");

        event.setSource("admin-service");
        assertThat(event.getSource()).isEqualTo("admin-service");

        Instant now = Instant.now();
        event.setOccurredAt(now);
        assertThat(event.getOccurredAt()).isEqualTo(now);

        event.setEventId("custom-id");
        assertThat(event.getEventId()).isEqualTo("custom-id");
    }

    private static class TestEvent extends BaseEvent {
        TestEvent() {
            super();
        }

        TestEvent(String eventType, String source) {
            super(eventType, source);
        }
    }
}
