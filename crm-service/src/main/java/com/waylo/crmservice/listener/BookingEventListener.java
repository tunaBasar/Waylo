package com.waylo.crmservice.listener;

import com.waylo.crmservice.domain.ProcessedEvent;
import com.waylo.crmservice.event.BookingCancelledEvent;
import com.waylo.crmservice.event.BookingCreatedEvent;
import com.waylo.crmservice.repository.ProcessedEventRepository;
import com.waylo.crmservice.service.CustomerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);
    private static final String GROUP = "crm-service";
    private static final int BOOKING_LOYALTY_POINTS = 100;

    private final ProcessedEventRepository processedEventRepository;
    private final CustomerService customerService;

    public BookingEventListener(ProcessedEventRepository processedEventRepository,
                                CustomerService customerService) {
        this.processedEventRepository = processedEventRepository;
        this.customerService = customerService;
    }

    @KafkaListener(topics = "booking-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleBookingEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        Object event = record.value();
        try {
            if (event instanceof BookingCreatedEvent e) {
                handleBookingCreated(e);
            } else if (event instanceof BookingCancelledEvent e) {
                handleBookingCancelled(e);
            } else {
                log.warn("Bilinmeyen booking event tipi: {}", event == null ? "null" : event.getClass().getSimpleName());
            }
        } catch (Exception ex) {
            log.error("Booking event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleBookingCreated(BookingCreatedEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[CRM] Booking olusturuldu - bookingId={}, customerId={}",
                event.getBookingId(), event.getCustomerId());
        try {
            customerService.addLoyaltyPoints(event.getCustomerId(), BOOKING_LOYALTY_POINTS);
            log.info("[CRM] {} loyalty puan eklendi - customerId={}",
                    BOOKING_LOYALTY_POINTS, event.getCustomerId());
        } catch (Exception ex) {
            log.warn("[CRM] Loyalty puan eklenemedi - customerId={}: {}",
                    event.getCustomerId(), ex.getMessage());
        }
        markProcessed(event.getEventId());
    }

    private void handleBookingCancelled(BookingCancelledEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[CRM] Booking iptal edildi - bookingId={}, customerId={}",
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
