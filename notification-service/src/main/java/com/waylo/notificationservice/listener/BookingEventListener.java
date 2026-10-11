package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.event.BookingCancelledEvent;
import com.waylo.notificationservice.event.BookingCreatedEvent;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.event.PaymentCompletedEvent;
import com.waylo.notificationservice.event.PaymentRefundedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;

    public BookingEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "booking-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleBookingEvent(@Payload Object event, Acknowledgment ack) {
        try {
            if (event instanceof BookingCreatedEvent e) {
                handleBookingCreated(e);
            } else if (event instanceof BookingCancelledEvent e) {
                handleBookingCancelled(e);
            } else {
                log.warn("Bilinmeyen booking event tipi: {}", event.getClass().getSimpleName());
            }
        } catch (Exception ex) {
            log.error("Booking event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleBookingCreated(BookingCreatedEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Booking olusturuldu - bookingId={}, customerId={}",
                event.getBookingId(), event.getCustomerId());
        // TODO: Musteri bilgilerini Feign ile cek ve bildirim gonder
        markProcessed(event.getEventId());
    }

    private void handleBookingCancelled(BookingCancelledEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Booking iptal edildi - bookingId={}, customerId={}",
                event.getBookingId(), event.getCustomerId());
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
