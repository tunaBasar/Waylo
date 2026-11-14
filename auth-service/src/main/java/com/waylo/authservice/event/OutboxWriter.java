package com.waylo.authservice.event;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Writes an event row into outbox_event. MANDATORY: it must join the caller's business transaction, so the row commits
 * or rolls back together with the domain change. The payload goes through the same JsonSerializer the service used for
 * direct sends (auth-events values are JSON-encoded strings with a java.lang.String type header), so consumers see
 * byte-identical records.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final JsonSerializer<Object> serializer = new JsonSerializer<>();

    public OutboxWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, String aggregateId, String topic, String key, Object event) {
        RecordHeaders headers = new RecordHeaders();
        byte[] json = serializer.serialize(topic, headers, event);
        Header typeId = headers.lastHeader("__TypeId__");
        String typeToken = typeId == null ? "" : new String(typeId.value(), StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO outbox_event (id, aggregate_type, aggregate_id, topic, event_key, type_id, payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), aggregateType, aggregateId, topic, key, typeToken,
                new String(json, StandardCharsets.UTF_8));
    }
}
