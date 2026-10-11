package com.waylo.flightservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FlightEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FlightEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FlightEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishFlightCreated(FlightCreatedEvent event) {
        log.info("Publishing FlightCreatedEvent: flightNumber={}", event.getFlightNumber());
        kafkaTemplate.send(KafkaTopics.FLIGHT_EVENTS, event.getFlightId().toString(), event);
    }

    public void publishFlightCancelled(FlightCancelledEvent event) {
        log.info("Publishing FlightCancelledEvent: flightNumber={}", event.getFlightNumber());
        kafkaTemplate.send(KafkaTopics.FLIGHT_EVENTS, event.getFlightId().toString(), event);
    }
}
