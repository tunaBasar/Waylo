package com.waylo.crmservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.crmservice.repository.CustomerRepository;
import com.waylo.crmservice.service.CustomerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps a CRM customer for every auth user, with Customer.id == auth userId. Bookings and
 * orders carry the auth userId as customerId, so without this link notification-service and
 * crm loyalty processing cannot resolve the customer.
 *
 * auth-service publishes auth-events as a JSON string (KafkaTemplate&lt;String, String&gt;), so the
 * record value arrives as a String and is parsed here. Every auth event type (VERIFICATION,
 * WELCOME, PASSWORD_RESET) triggers the idempotent upsert, which also backfills users that
 * registered before this listener existed.
 */
@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);
    private static final String GROUP = "crm-service";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public AuthEventListener(CustomerService customerService, CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    @KafkaListener(topics = "auth-events", groupId = GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleAuthEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            Object value = record.value();
            if (!(value instanceof String raw)) {
                log.warn("[CRM] Beklenmeyen auth event payload tipi: {}",
                        value == null ? "null" : value.getClass().getName());
                return;
            }
            JsonNode node = JSON.readTree(raw);
            String userIdText = node.path("userId").asText(null);
            String email = node.path("email").asText(null);
            if (userIdText == null || email == null || email.isBlank()) {
                log.warn("[CRM] auth event userId/email eksik, atlaniyor (offset={})", record.offset());
                return;
            }
            UUID userId = UUID.fromString(userIdText);
            boolean created = customerService.ensureCustomerForUser(userId, node.path("name").asText(""), email);
            if (created) {
                log.info("[CRM] Auth kullanicisi icin musteri olusturuldu - customerId={}", userId);
            } else if (!customerRepository.existsById(userId)) {
                log.warn("[CRM] Musteri olusturulamadi, e-posta baska bir musteride kayitli - userId={}", userId);
            }
        } catch (Exception ex) {
            log.error("[CRM] Auth event islenirken hata: {}", ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }
}
