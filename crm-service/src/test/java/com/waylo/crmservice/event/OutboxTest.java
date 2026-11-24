package com.waylo.crmservice.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    void writerStoresSupportEventWithTypeTokenAndRequestId() {
        OutboxWriter writer = new OutboxWriter(jdbc);
        UUID caseId = UUID.randomUUID();
        org.slf4j.MDC.put("requestId", "req-1");
        writer.write("SUPPORT_CASE", caseId.toString(), "support-events", caseId.toString(),
                new SupportCaseUpdatedEvent(caseId, UUID.randomUUID(), UUID.randomUUID(), "Bag question",
                        SupportCaseUpdatedEvent.Kind.ANSWERED, SupportCaseUpdatedEvent.AuthorRole.PARTNER));
        org.slf4j.MDC.clear();

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO outbox_event"), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        List<Object> v = args.getAllValues();
        assertThat(v.get(7)).isEqualTo("req-1");
        assertThat(v.get(1)).isEqualTo("SUPPORT_CASE");
        assertThat(v.get(2)).isEqualTo(caseId.toString());
        assertThat(v.get(3)).isEqualTo("support-events");
        assertThat(v.get(4)).isEqualTo(caseId.toString());
        assertThat(v.get(5)).isEqualTo("supportCaseUpdated");
        assertThat((String) v.get(6)).contains("\"kind\":\"ANSWERED\"").contains("\"authorRole\":\"PARTNER\"").contains(caseId.toString());
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

        ArgumentCaptor<ProducerRecord<String, String>> rec = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(rec.capture());
        assertThat(rec.getValue().topic()).isEqualTo("support-events");
        assertThat(rec.getValue().key()).isEqualTo("k1");
        assertThat(new String(rec.getValue().headers().lastHeader("__TypeId__").value())).isEqualTo("supportCaseUpdated");
        assertThat(new String(rec.getValue().headers().lastHeader("X-Request-Id").value())).isEqualTo("req-1");
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
        assertThat(OutboxRelay.CLAIM_SQL).contains("FOR UPDATE OF o SKIP LOCKED").contains("published_at IS NULL")
                .contains("p.aggregate_id = o.aggregate_id");
    }

    private static Map<String, Object> row(UUID id, int attempts) {
        return Map.of("id", id, "topic", "support-events", "event_key", "k1", "type_id", "supportCaseUpdated",
                "payload", "{}", "attempts", attempts, "request_id", "req-1");
    }
}
