package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.AuthOrganizationClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.OrderEvent;
import com.waylo.notificationservice.event.SupportCaseUpdatedEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partner e-mails (new reservation, cancelled reservation, new support case / customer message) to the
 * ACTIVE owners of the supplier organization, see NOTIFICATION-CONTRACT.md. Own Kafka group and own
 * processed_events group, so the customer mail and the partner mail of one event both happen exactly once.
 * Never contains the customer's e-mail or phone, nor other organizations' items. The order event carries no
 * customer name, so none is shown.
 */
@Component
public class PartnerEventListener {

    static final String GROUP = "notification-service-partner";
    static final String NEW_ORDER_SUBJECT = "Yeni rezervasyon";
    static final String CANCELLED_ORDER_SUBJECT = "Rezervasyon iptal edildi";
    static final String NEW_CASE_SUBJECT = "Yeni müşteri destek talebi";
    static final String NEW_MESSAGE_SUBJECT = "Destek talebinde yeni müşteri mesajı";

    private static final Logger log = LoggerFactory.getLogger(PartnerEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final AuthOrganizationClient authOrganizationClient;
    private final NotificationService notificationService;
    private final String appBaseUrl;
    private final Duration maxAge;

    public PartnerEventListener(ProcessedEventRepository processedEventRepository,
                                AuthOrganizationClient authOrganizationClient,
                                NotificationService notificationService,
                                @Value("${waylo.mail.app-base-url:http://localhost:3000}") String appBaseUrl,
                                @Value("${waylo.mail.partner-event-max-age:PT24H}") Duration maxAge) {
        this.maxAge = maxAge;
        this.processedEventRepository = processedEventRepository;
        this.authOrganizationClient = authOrganizationClient;
        this.notificationService = notificationService;
        this.appBaseUrl = appBaseUrl;
    }

    @KafkaListener(topics = "order-events", groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            if (record.value() instanceof OrderEvent e && e.getItems() != null
                    && ("ORDER_PAID".equals(e.getEventType()) || ("ORDER_CANCELLED".equals(e.getEventType()) && e.isPaid()))
                    && !processedEventRepository.existsByEventIdAndConsumerGroup(e.getEventId(), GROUP)) {
                if (tooOld(record)) {
                    skipOld(e.getEventId(), record);
                    ack.acknowledge();
                    return;
                }
                boolean paid = "ORDER_PAID".equals(e.getEventType());
                Map<UUID, List<OrderEvent.Item>> byOrg = new LinkedHashMap<>();
                for (OrderEvent.Item i : e.getItems()) {
                    if (i.supplierOrganizationId() != null) {
                        byOrg.computeIfAbsent(i.supplierOrganizationId(), k -> new java.util.ArrayList<>()).add(i);
                    }
                }
                byOrg.forEach((org, items) -> send(org, e.getOrderId(),
                        paid ? NEW_ORDER_SUBJECT : CANCELLED_ORDER_SUBJECT,
                        orderBody(e, items, paid, appBaseUrl)));
                processedEventRepository.save(new ProcessedEvent(e.getEventId(), GROUP));
            }
        } catch (RuntimeException ex) {
            log.error("Partner order event islenirken hata: {}", ex.getMessage(), ex);
            throw ex;
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "support-events", groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void handleSupportEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            if (record.value() instanceof SupportCaseUpdatedEvent e && e.getSupplierOrganizationId() != null
                    && ("CREATED".equals(e.getKind()) || "CUSTOMER_MESSAGE".equals(e.getKind()))
                    && !processedEventRepository.existsByEventIdAndConsumerGroup(e.getEventId(), GROUP)) {
                if (tooOld(record)) {
                    skipOld(e.getEventId(), record);
                    ack.acknowledge();
                    return;
                }
                boolean created = "CREATED".equals(e.getKind());
                send(e.getSupplierOrganizationId(), e.getOrderId(),
                        created ? NEW_CASE_SUBJECT : NEW_MESSAGE_SUBJECT, supportBody(e, created, appBaseUrl));
                processedEventRepository.save(new ProcessedEvent(e.getEventId(), GROUP));
            }
        } catch (RuntimeException ex) {
            log.error("Partner support event islenirken hata: {}", ex.getMessage(), ex);
            throw ex;
        }
        ack.acknowledge();
    }

    /**
     * Replay safety (new consumer group starts at earliest). The typed event's occurredAt is not trustworthy
     * (BaseEvent's constructor sets "now"), so the producer record timestamp is the age source. A missing one counts as too old.
     */
    private boolean tooOld(ConsumerRecord<String, Object> record) {
        long ts = record.timestamp();
        return ts <= 0 || Instant.ofEpochMilli(ts).isBefore(Instant.now().minus(maxAge));
    }

    private void skipOld(String eventId, ConsumerRecord<String, Object> record) {
        log.info("Skipping old partner event (replay safety): eventId={}, recordTimestamp={}", eventId, record.timestamp());
        processedEventRepository.save(new ProcessedEvent(eventId, GROUP));
    }

    /** Fails soft like the customer mail: lookup failure or no owners means log and skip, never fail the event. */
    private void send(UUID organizationId, UUID orderId, String subject, String body) {
        List<String> emails;
        try {
            AuthOrganizationClient.OrganizationContacts c = authOrganizationClient.contacts(organizationId);
            emails = c == null || c.emails() == null ? List.of() : c.emails();
        } catch (Exception ex) {
            log.warn("Partner contacts lookup failed for organizationId={}: {}, skipping notification", organizationId, ex.getMessage());
            return;
        }
        if (emails.isEmpty()) {
            log.warn("No active owner e-mail for organizationId={}, skipping notification", organizationId);
            return;
        }
        for (String to : emails) {
            if (to == null || to.isBlank()) continue;
            // notifications.customer_id is NOT NULL: the organization id stands in for the recipient owner
            notificationService.send(new NotificationRequest(organizationId, orderId, NotificationType.EMAIL, to, subject, body));
        }
    }

    private static String base(String appBaseUrl) {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    static String orderBody(OrderEvent e, List<OrderEvent.Item> items, boolean paid, String appBaseUrl) {
        StringBuilder sb = new StringBuilder(paid ? "Yeni bir rezervasyonunuz var." : "Bir rezervasyon iptal edildi.")
                .append(" Sipariş no: ").append(e.getOrderId()).append("\n");
        BigDecimal refund = BigDecimal.ZERO;
        for (OrderEvent.Item i : items) {
            sb.append("- ").append(i.description());
            if (i.startsAt() != null) {
                sb.append(" (").append(OrderEventListener.when(i.startsAt()));
                if (i.endsAt() != null) sb.append(" - ").append(OrderEventListener.when(i.endsAt()));
                sb.append(")");
            }
            sb.append(": ").append(OrderEventListener.money(i.amount(), e.getCurrency())).append("\n");
            if (i.refundAmount() != null) refund = refund.add(i.refundAmount());
        }
        if (!paid) {
            sb.append(refund.signum() > 0
                    ? "Müşteriye iade edilen tutar: " + OrderEventListener.money(refund, e.getCurrency())
                    : "İptal koşulları gereği iade yapılmadı.").append("\n");
        }
        return sb.append("Detaylar için: ").append(base(appBaseUrl)).append("/partner/orders").toString();
    }

    static String supportBody(SupportCaseUpdatedEvent e, boolean created, String appBaseUrl) {
        return (created ? "Siparişinizle ilgili yeni bir destek talebi açıldı." : "Destek talebine müşteri yeni bir mesaj ekledi.")
                + "\n" + (e.getSubject() == null ? "" : "Konu: " + e.getSubject() + "\n")
                + (e.getOrderId() == null ? "" : "Sipariş no: " + e.getOrderId() + "\n")
                + "Yanıtlamak için: " + base(appBaseUrl) + "/partner/support";
    }
}
