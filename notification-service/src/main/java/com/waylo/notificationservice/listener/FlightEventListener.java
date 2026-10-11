package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class FlightEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlightEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;

    public FlightEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "flight-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleFlightEvent(@Payload Object event, Acknowledgment ack) {
        try {
            if (event instanceof FlightCancelledEvent e) {
                handleFlightCancelled(e);
            } else {
                log.debug("Atlanıyor: {}", event.getClass().getSimpleName());
            }
        } catch (Exception ex) {
            log.error("Flight event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleFlightCancelled(FlightCancelledEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Ucus iptal edildi - flightId={}, flightNumber={}",
                event.getFlightId(), event.getFlightNumber());
        // TODO: İlgili booking'lerin musterilerine bildirim gonder
        markProcessed(event.getEventId());
    }

    private boolean isDuplicate(String eventId) {
        if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, GROUP)) {
            log.debug("Tekrar eden event atlanıyor: {}", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId) {
        processedEventRepository.save(new ProcessedEvent(eventId, GROUP));
    }
}
