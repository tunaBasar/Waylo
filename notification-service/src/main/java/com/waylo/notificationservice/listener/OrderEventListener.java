package com.waylo.notificationservice.listener;

import com.waylo.notificationservice.client.CrmServiceClient;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.event.OrderEvent;
import com.waylo.notificationservice.repository.ProcessedEventRepository;
import com.waylo.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * order-events (package-booking-service) -> customer e-mails, see NOTIFICATION-CONTRACT.md.
 * ORDER_PAID -> confirmation, ORDER_CANCELLED of a paid order -> refund info. Other types are ignored.
 * Texts never contain voucher codes or QR payloads (bearer credentials).
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private static final String GROUP = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final CrmServiceClient crmServiceClient;
    private final NotificationService notificationService;

    public OrderEventListener(ProcessedEventRepository processedEventRepository,
                              CrmServiceClient crmServiceClient,
                              NotificationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.crmServiceClient = crmServiceClient;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order-events", groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            if (record.value() instanceof OrderEvent e) {
                if ("ORDER_PAID".equals(e.getEventType())) {
                    handle(e, "Siparişiniz onaylandı", confirmationBody(e));
                } else if ("ORDER_CANCELLED".equals(e.getEventType()) && e.isPaid()) {
                    handle(e, "Siparişiniz iptal edildi", refundBody(e));
                }
            }
        } catch (Exception ex) {
            log.error("Order event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handle(OrderEvent e, String subject, String body) {
        if (processedEventRepository.existsByEventIdAndConsumerGroup(e.getEventId(), GROUP)) return;
        String recipient = lookupEmail(e.getCustomerId());
        if (recipient != null) {
            notificationService.send(new NotificationRequest(e.getCustomerId(), e.getOrderId(),
                    NotificationType.EMAIL, recipient, subject, body));
        }
        processedEventRepository.save(new ProcessedEvent(e.getEventId(), GROUP));
    }

    static String money(BigDecimal v, String currency) {
        BigDecimal x = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_EVEN);
        return x.toPlainString() + " " + ("TRY".equals(currency) ? "TL" : currency);
    }

    static String confirmationBody(OrderEvent e) {
        StringBuilder sb = new StringBuilder("Siparişiniz onaylandı. Sipariş no: ").append(e.getOrderId()).append("\n");
        if (e.getItems() != null) {
            for (OrderEvent.Item i : e.getItems()) {
                sb.append("- ").append(i.description());
                if (i.startsAt() != null) {
                    sb.append(" (").append(i.startsAt());
                    if (i.endsAt() != null) sb.append(" - ").append(i.endsAt());
                    sb.append(")");
                }
                sb.append(": ").append(money(i.amount(), e.getCurrency())).append("\n");
            }
        }
        sb.append("Toplam: ").append(money(e.getTotalAmount(), e.getCurrency())).append("\n");
        sb.append("QR kodunuz Siparişlerim sayfasında.");
        return sb.toString();
    }

    static String refundBody(OrderEvent e) {
        boolean refunded = e.getRefundAmount() != null && e.getRefundAmount().signum() > 0;
        return "Siparişiniz iptal edildi. Sipariş no: " + e.getOrderId() + "\n"
                + (refunded ? "İade tutarı: " + money(e.getRefundAmount(), e.getCurrency())
                        + ". İade, ödeme yönteminize bankanıza bağlı olarak birkaç iş günü içinde yansır."
                        : "İptal koşulları gereği iade yapılmayacaktır.");
    }

    private String lookupEmail(UUID customerId) {
        try {
            CrmServiceClient.CustomerLookupResponse r = crmServiceClient.getById(customerId);
            if (r == null || !r.success() || r.data() == null || r.data().email() == null) {
                log.warn("Customer lookup returned no email for customerId={}, skipping notification", customerId);
                return null;
            }
            return r.data().email();
        } catch (Exception ex) {
            log.warn("Customer lookup failed for customerId={}: {}, skipping notification", customerId, ex.getMessage());
            return null;
        }
    }
}
