package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.client.PackageBookingServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.FlightCancelledEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FlightEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlightEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final PackageBookingServiceClient packageBookingServiceClient;
    private final CrmServiceClient crmServiceClient;
    private final NotificationService notificationService;

    public FlightEventListener(ProcessedEventRepository processedEventRepository,
                               PackageBookingServiceClient packageBookingServiceClient,
                               CrmServiceClient crmServiceClient,
                               NotificationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.packageBookingServiceClient = packageBookingServiceClient;
        this.crmServiceClient = crmServiceClient;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "flight-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleFlightEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        Object event = record.value();
        try {
            if (event instanceof FlightCancelledEvent e) {
                handleFlightCancelled(e);
            } else {
                log.debug("Atlanıyor: {}", event == null ? "null" : event.getClass().getSimpleName());
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
        notifyAffectedCustomers(event.getFlightId(), event.getFlightNumber());
        markProcessed(event.getEventId());
    }

    /**
     * Looks up every booking for the cancelled flight via package-booking-service, then
     * notifies each affected customer via crm-service email lookup. Fails soft per customer:
     * one missing/unreachable lookup does not block notifying the other affected customers,
     * and a failure of the booking lookup itself just logs and skips (matches the fail-soft
     * pattern used elsewhere for cross-service lookups, e.g. RoleGrantService).
     */
    private void notifyAffectedCustomers(UUID flightId, String flightNumber) {
        List<PackageBookingServiceClient.BookingDto> bookings;
        try {
            PackageBookingServiceClient.BookingListResponse response = packageBookingServiceClient.getByFlightId(flightId);
            if (response == null || !response.success() || response.data() == null) {
                log.warn("Booking lookup returned no data for flightId={}, skipping notifications", flightId);
                return;
            }
            bookings = response.data();
        } catch (Exception e) {
            log.warn("Booking lookup failed for flightId={}: {}, skipping notifications", flightId, e.getMessage());
            return;
        }

        for (PackageBookingServiceClient.BookingDto booking : bookings) {
            sendCancellationEmail(booking.customerId(), booking.id(), flightNumber);
        }
    }

    private void sendCancellationEmail(UUID customerId, UUID bookingId, String flightNumber) {
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
                customerId, bookingId, NotificationType.EMAIL, recipient,
                "Ucusunuz iptal edildi",
                "Sayin musterimiz, " + flightNumber + " numarali ucusunuz iptal edilmistir. "
                        + bookingId + " numarali rezervasyonunuz etkilenmistir."));
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
