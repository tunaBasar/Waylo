package com.waylo.paymentservice.event;

import com.waylo.observability.RequestId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Relays outbox_event rows to Kafka (at-least-once). Rows are claimed with FOR UPDATE SKIP LOCKED in a read-write
 * transaction so several instances never send the same row concurrently. A row is only claimed when no older unpublished
 * row of the same aggregate exists, which keeps per-order ordering. A crash after the broker ack but before the commit
 * re-sends the row, so consumers must be idempotent (they are: eventId guards).
 */
@Component
public class OutboxRelay {

    static final String CLAIM_SQL =
            "SELECT o.id, o.topic, o.event_key, o.type_id, o.payload, o.attempts, o.request_id FROM outbox_event o "
          + "WHERE o.published_at IS NULL AND o.next_attempt_at <= now() "
          + "AND NOT EXISTS (SELECT 1 FROM outbox_event p WHERE p.aggregate_id = o.aggregate_id "
          + "AND p.published_at IS NULL AND p.created_at < o.created_at) "
          + "ORDER BY o.created_at LIMIT ? FOR UPDATE OF o SKIP LOCKED";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(JdbcTemplate jdbc,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${waylo.outbox.batch-size:50}") int batchSize,
                       @Value("${waylo.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${waylo.outbox.poll-ms:1000}")
    @Transactional
    public void relay() {
        List<Map<String, Object>> rows = jdbc.queryForList(CLAIM_SQL, batchSize);
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        (String) row.get("topic"), (String) row.get("event_key"), (String) row.get("payload"));
                String typeId = (String) row.get("type_id");
                if (typeId != null && !typeId.isEmpty()) {
                    record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
                }
                String requestId = (String) row.get("request_id");
                if (requestId != null) {
                    record.headers().add(RequestId.HEADER, requestId.getBytes(StandardCharsets.UTF_8));
                }
                kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                jdbc.update("UPDATE outbox_event SET published_at = now(), last_error = NULL WHERE id = ?", id);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                int attempts = ((Number) row.get("attempts")).intValue() + 1;
                long backoffSeconds = Math.min(60L, 1L << Math.min(attempts, 6));
                String err = String.valueOf(e.getMessage());
                jdbc.update("UPDATE outbox_event SET attempts = ?, last_error = ?, "
                                + "next_attempt_at = now() + (? * interval '1 second') WHERE id = ?",
                        attempts, err.length() > 500 ? err.substring(0, 500) : err, backoffSeconds, id);
                log.warn("Outbox publish failed (id={}, attempt={}): {}", id, attempts, err);
                break; // broker is likely down: do not burn a timeout per remaining row
            }
        }
    }
}
