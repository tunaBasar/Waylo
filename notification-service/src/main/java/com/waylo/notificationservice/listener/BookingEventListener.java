package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.BookingCancelledEvent;
import com.waylo.notificationservice.event.BookingCreatedEvent;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.event.PaymentCompletedEvent;
import com.waylo.notificationservice.event.PaymentRefundedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final CrmServiceClient crmServiceClient;
    private final NotificationService notificationService;

    public BookingEventListener(ProcessedEventRepository processedEventRepository,
                                CrmServiceClient crmServiceClient,
                                NotificationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.crmServiceClient = crmServiceClient;
        this.notificationService = notificationService;
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
                log.warn("Bilinmeyen booking event tipi: {}", event == null ? "null" : event.getClass().getName());
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
        sendCustomerEmail(event.getCustomerId(), event.getBookingId(), "Rezervasyonunuz alindi",
                "Sayin musterimiz, " + event.getBookingId() + " numarali rezervasyonunuz basariyla olusturuldu.");
        markProcessed(event.getEventId());
    }

    private void handleBookingCancelled(BookingCancelledEvent event) {
        if (isDuplicate(event.getEventId())) return;
        log.info("[NOTIFICATION] Booking iptal edildi - bookingId={}, customerId={}",
                event.getBookingId(), event.getCustomerId());
        sendCustomerEmail(event.getCustomerId(), event.getBookingId(), "Rezervasyonunuz iptal edildi",
                "Sayin musterimiz, " + event.getBookingId() + " numarali rezervasyonunuz iptal edilmistir.");
        markProcessed(event.getEventId());
    }

    /**
     * Looks up the customer's email via crm-service and sends a notification.
     * Fails soft: if the customer lookup fails (crm-service unavailable, customer not found),
     * logs a warning and does not send rather than failing the whole event (matches the
     * fail-soft pattern used elsewhere for cross-service lookups, e.g. RoleGrantService).
     */
    private void sendCustomerEmail(java.util.UUID customerId, java.util.UUID bookingId, String subject, String body) {
        String recipient;
        try {
            CrmServiceClient.CustomerLookupResponse response = crmServiceClient.getById(customerId);
            if (response == null || !response.success() || response.data() == null
                    || response.data().email() == null) {
                log.warn("Customer lookup returned no email for customerId={}, skipping notification", customerId);
                return;
            }
            recipient = response.data().email();
        } catch (Exception e) {
            log.warn("Customer lookup failed for customerId={}: {}, skipping notification", customerId, e.getMessage());
            return;
        }

        notificationService.send(new NotificationRequest(
                customerId, bookingId, NotificationType.EMAIL, recipient, subject, body));
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
