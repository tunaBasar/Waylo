package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.SupportCaseUpdatedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** RM-36: e-mails the customer when a support case is answered by the partner/admin or closed by the admin. */
@Component
public class SupportEventListener {

    static final String ANSWERED_SUBJECT = "Destek talebiniz yanıtlandı";
    static final String CLOSED_SUBJECT = "Destek talebiniz kapatıldı";
    static final String HINT = "Yanıtı görmek için Waylo hesabınızda Destek sayfasını açın.";

    private static final Logger log = LoggerFactory.getLogger(SupportEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final CrmServiceClient crmServiceClient;
    private final NotificationService notificationService;

    public SupportEventListener(ProcessedEventRepository processedEventRepository,
                                CrmServiceClient crmServiceClient,
                                NotificationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.crmServiceClient = crmServiceClient;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "support-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleSupportEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        Object event = record.value();
        try {
            if (event instanceof SupportCaseUpdatedEvent e) {
                handleUpdated(e);
            } else {
                log.warn("Bilinmeyen support event tipi: {}", event == null ? "null" : event.getClass().getSimpleName());
            }
        } catch (RuntimeException ex) {
            // Propagate to the DefaultErrorHandler (retry, then <topic>.DLT). No ack here.
            log.error("Support event islenirken hata: {}", ex.getMessage(), ex);
            throw ex;
        }
        ack.acknowledge();
    }

    private void handleUpdated(SupportCaseUpdatedEvent e) {
        // CREATED / CUSTOMER_MESSAGE are partner-only (PartnerEventListener); the customer never mails themselves.
        if (!"ANSWERED".equals(e.getKind()) && !"CLOSED".equals(e.getKind())) return;
        if (processedEventRepository.existsByEventIdAndConsumerGroup(e.getEventId(), GROUP)) {
            log.debug("Tekrar eden event atlanıyor: {}", e.getEventId());
            return;
        }
        String subject = "CLOSED".equals(e.getKind()) ? CLOSED_SUBJECT : ANSWERED_SUBJECT;
        String body = (e.getSubject() == null ? "" : e.getSubject() + "\n\n") + HINT;
        sendCustomerEmail(e, subject, body);
        processedEventRepository.save(new ProcessedEvent(e.getEventId(), GROUP));
    }

    /** Fails soft like BookingEventListener: no e-mail found means log and skip, never fail the event. */
    private void sendCustomerEmail(SupportCaseUpdatedEvent e, String subject, String body) {
        String recipient;
        try {
            CrmServiceClient.CustomerLookupResponse response = crmServiceClient.getById(e.getCustomerId());
            if (response == null || !response.success() || response.data() == null || response.data().email() == null) {
                log.warn("Customer lookup returned no email for customerId={}, skipping notification", e.getCustomerId());
                return;
            }
            recipient = response.data().email();
        } catch (Exception ex) {
            log.warn("Customer lookup failed for customerId={}: {}, skipping notification", e.getCustomerId(), ex.getMessage());
            return;
        }
        notificationService.send(new NotificationRequest(
                e.getCustomerId(), null, NotificationType.EMAIL, recipient, subject, body));
    }
}
