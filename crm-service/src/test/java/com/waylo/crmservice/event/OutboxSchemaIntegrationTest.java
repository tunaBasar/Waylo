package com.waylo.crmservice.event;

import com.waylo.observability.RequestId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the real OutboxWriter and OutboxRelay against the real Flyway schema (V1..latest), so a column used by the
 * code but missing from a migration (the V3 request_id bug) fails here instead of at runtime. No Spring context:
 * the @Transactional annotations are inert, so the methods run in autocommit.
 */
@Testcontainers
class OutboxSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM outbox_event");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private UUID writeEvent(String requestId) {
        UUID caseId = UUID.randomUUID();
        MDC.put(RequestId.MDC_KEY, requestId);
        new OutboxWriter(jdbc).write("SUPPORT_CASE", caseId.toString(), "support-events", caseId.toString(),
                new SupportCaseUpdatedEvent(caseId, UUID.randomUUID(), UUID.randomUUID(), "Bag question",
                        SupportCaseUpdatedEvent.Kind.ANSWERED, SupportCaseUpdatedEvent.AuthorRole.PARTNER,
                        UUID.randomUUID()));
        MDC.clear();
        return caseId;
    }

    private Map<String, Object> onlyRow() {
        return jdbc.queryForMap("SELECT *, (next_attempt_at > now()) AS delayed FROM outbox_event");
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> kafka() {
        return mock(KafkaTemplate.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writerThenRelayPublishesRowAgainstRealSchema() {
        UUID caseId = writeEvent("req-it-1");

        Map<String, Object> written = onlyRow();
        assertThat(written.get("type_id")).isEqualTo("supportCaseUpdated");
        assertThat((String) written.get("payload")).contains(caseId.toString());
        assertThat(written.get("published_at")).isNull();
        assertThat(written.get("request_id")).isEqualTo("req-it-1");
        assertThat(written.get("aggregate_type")).isEqualTo("SUPPORT_CASE");
        assertThat(written.get("topic")).isEqualTo("support-events");

        KafkaTemplate<String, String> kafka = kafka();
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        new OutboxRelay(jdbc, kafka, 50, 5000).relay();

        Map<String, Object> after = onlyRow();
        assertThat(after.get("published_at")).isNotNull();
        assertThat(after.get("attempts")).isEqualTo(0);
        assertThat(after.get("last_error")).isNull();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("support-events");
        assertThat(sent.getValue().key()).isEqualTo(caseId.toString());
        assertThat(sent.getValue().value()).isEqualTo(written.get("payload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayFailureKeepsRowUnpublishedAndSchedulesRetry() {
        writeEvent("req-it-2");

        KafkaTemplate<String, String> kafka = kafka();
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        new OutboxRelay(jdbc, kafka, 50, 5000).relay();

        Map<String, Object> row = onlyRow();
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat((String) row.get("last_error")).contains("broker down");
        assertThat(row.get("delayed")).isEqualTo(true);
    }
}
