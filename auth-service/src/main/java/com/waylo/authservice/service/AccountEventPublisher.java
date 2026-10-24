package com.waylo.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.HashMap;
import java.util.Map;

@Component
public class AccountEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    public AccountEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) { this.kafkaTemplate = kafkaTemplate; this.objectMapper = objectMapper; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AccountService.AuthEvent event) throws Exception {
        Map<String, Object> payload = new HashMap<>(); payload.put("eventType", event.eventType()); payload.put("userId", event.userId().toString());
        if (event.email() != null) payload.put("email", event.email()); if (event.name() != null) payload.put("name", event.name()); if (event.token() != null) payload.put("token", event.token());
        kafkaTemplate.send("auth-events", event.userId().toString(), objectMapper.writeValueAsString(payload));
    }
}
