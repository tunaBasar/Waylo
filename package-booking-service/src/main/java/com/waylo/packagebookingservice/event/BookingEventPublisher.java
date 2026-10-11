package com.waylo.packagebookingservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishBookingCreated(BookingCreatedEvent event) {
        log.info("Publishing BookingCreatedEvent: bookingId={}", event.getBookingId());
        kafkaTemplate.send(KafkaTopics.BOOKING_EVENTS, event.getBookingId().toString(), event);
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        log.info("Publishing BookingCancelledEvent: bookingId={}", event.getBookingId());
        kafkaTemplate.send(KafkaTopics.BOOKING_EVENTS, event.getBookingId().toString(), event);
    }
}
