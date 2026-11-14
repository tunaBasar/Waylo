package com.waylo.authservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.authservice.service.AccountEventPublisher;
import com.waylo.authservice.service.AccountService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxTest {

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @Test
    void publisherWritesOutboxRowByteCompatibleWithOldDirectSend() throws Exception {
        AccountEventPublisher publisher = new AccountEventPublisher(new OutboxWriter(jdbc), new ObjectMapper());
        UUID userId = UUID.randomUUID();
        publisher.publish(new AccountService.AuthEvent("VERIFICATION", userId, "a@b.test", "Ada L", "tok"));

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO outbox_event"), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture());
        List<Object> v = args.getAllValues();
        assertThat(v.get(1)).isEqualTo("USER");
        assertThat(v.get(2)).isEqualTo(userId.toString());
        assertThat(v.get(3)).isEqualTo("auth-events");
        assertThat(v.get(4)).isEqualTo(userId.toString());
        // Old path: KafkaTemplate<String,String> with spring.kafka JsonSerializer -> JSON-encoded string + String type id.
        assertThat(v.get(5)).isEqualTo("java.lang.String");
        String payload = (String) v.get(6);
        assertThat(payload).startsWith("\"{").endsWith("}\"");
        String inner = new ObjectMapper().readValue(payload, String.class);
        Map<?, ?> m = new ObjectMapper().readValue(inner, Map.class);
        assertThat(m.get("eventType")).isEqualTo("VERIFICATION");
        assertThat(m.get("userId")).isEqualTo(userId.toString());
        assertThat(m.get("token")).isEqualTo("tok");
        assertThat(m.get("eventId")).isNotNull();
        // Exactly the bytes the direct JsonSerializer send produced before.
        byte[] direct = new JsonSerializer<Object>().serialize("auth-events", new RecordHeaders(), inner);
        assertThat(payload).isEqualTo(new String(direct, StandardCharsets.UTF_8));
        verifyNoInteractions(kafka);
    }

    @Test
    void writerRequiresExistingTransaction() throws Exception {
        var m = OutboxWriter.class.getMethod("write", String.class, String.class, String.class, String.class, Object.class);
        assertThat(m.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void relaySuccessMarksPublished() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(List.of(row(id, 0)));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        new OutboxRelay(jdbc, kafka, 50, 1000).relay();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> rec = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(rec.capture());
        assertThat(rec.getValue().topic()).isEqualTo("auth-events");
        assertThat(rec.getValue().key()).isEqualTo("k1");
        assertThat(new String(rec.getValue().headers().lastHeader("__TypeId__").value())).isEqualTo("java.lang.String");
        verify(jdbc).update(contains("published_at = now()"), eq(id));
    }

    @Test
    void relayFailureIncrementsAttemptsAndSchedulesRetry() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(List.of(row(id, 2)));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        new OutboxRelay(jdbc, kafka, 50, 1000).relay();

        verify(jdbc).update(contains("attempts = ?"), eq(3), contains("broker down"), eq(8L), eq(id));
        verify(jdbc, never()).update(contains("published_at = now()"), any(Object.class));
    }

    @Test
    void claimQueryUsesSkipLockedAndOrdersPerAggregate() {
        assertThat(OutboxRelay.CLAIM_SQL).contains("FOR UPDATE OF o SKIP LOCKED").contains("p.aggregate_id = o.aggregate_id");
    }

    private static Map<String, Object> row(UUID id, int attempts) {
        return Map.of("id", id, "topic", "auth-events", "event_key", "k1", "type_id", "java.lang.String",
                "payload", "\"{}\"", "attempts", attempts);
    }
}
