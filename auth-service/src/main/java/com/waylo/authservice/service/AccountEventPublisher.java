package com.waylo.authservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.authservice.event.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records auth-events in the transactional outbox. Must be called inside the business transaction. The value is the
 * JSON text of the payload (a plain string on the wire), unchanged for existing consumers.
 */
@Component
public class AccountEventPublisher {
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public AccountEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public void publish(AccountService.AuthEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", event.eventType());
        payload.put("userId", event.userId().toString());
        payload.put("eventId", UUID.randomUUID().toString());
        if (event.email() != null) payload.put("email", event.email());
        if (event.name() != null) payload.put("name", event.name());
        if (event.token() != null) payload.put("token", event.token());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize auth event", e);
        }
        outboxWriter.write("USER", event.userId().toString(), "auth-events", event.userId().toString(), json);
    }
}
