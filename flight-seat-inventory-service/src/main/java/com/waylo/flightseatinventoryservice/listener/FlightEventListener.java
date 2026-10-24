package com.waylo.flightseatinventoryservice.listener;

import com.waylo.flightseatinventoryservice.event.FlightCancelledEvent;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import com.waylo.flightseatinventoryservice.service.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class FlightEventListener {
    private static final Logger log = LoggerFactory.getLogger(FlightEventListener.class);
    private static final String GROUP = "flight-seat-inventory-service";

    private final IdempotencyService idempotencyService;
    private final FlightSeatService flightSeatService;

    public FlightEventListener(IdempotencyService idempotencyService, FlightSeatService flightSeatService) {
        this.idempotencyService = idempotencyService;
        this.flightSeatService = flightSeatService;
    }

    @KafkaListener(topics = "flight-events", groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void handleFlightEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            if (record.value() instanceof FlightCancelledEvent event) {
                if (idempotencyService.tryAcquire(event.getEventId())) {
                    flightSeatService.closeFlightSeats(event.getFlightNumber());
                }
            } else {
                log.debug("Skipping unknown flight event type: {}", record.value());
            }
        } catch (Exception ex) {
            log.error("Flight event processing failed: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }
}
