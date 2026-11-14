package com.waylo.packagebookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes events to the transactional outbox (joins the caller's transaction). OutboxRelay sends them to Kafka. */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);
    private final OutboxWriter outbox;

    public BookingEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void publishBookingCreated(BookingCreatedEvent event) {
        log.info("Queueing BookingCreatedEvent: {}", event.getBookingId());
        outbox.write("BOOKING", event.getBookingId().toString(), KafkaTopics.BOOKING_EVENTS, event.getBookingId().toString(), event);
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        log.info("Queueing BookingCancelledEvent: {}", event.getBookingId());
        outbox.write("BOOKING", event.getBookingId().toString(), KafkaTopics.BOOKING_EVENTS, event.getBookingId().toString(), event);
    }
}
