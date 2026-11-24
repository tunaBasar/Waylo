package com.waylo.observability.dlt;

import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.BackOffExecution;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DltErrorHandlersTest {

    // Outside Spring the global composite registry has no child registry, so every counter is a no-op that reads 0.
    // Attach a simple registry for these tests so BusinessMetrics increments are observable.
    private static final io.micrometer.core.instrument.simple.SimpleMeterRegistry REGISTRY =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @org.junit.jupiter.api.BeforeAll
    static void attachRegistry() {
        Metrics.addRegistry(REGISTRY);
    }

    @org.junit.jupiter.api.AfterAll
    static void detachRegistry() {
        Metrics.removeRegistry(REGISTRY);
        REGISTRY.close();
    }

    @Test
    void backOffIsThreeAttemptsOneTwoFourSecondsCappedAtTen() {
        ExponentialBackOffWithMaxRetries b = DltErrorHandlers.backOff();
        BackOffExecution e = b.start();
        assertThat(e.nextBackOff()).isEqualTo(1000L);
        assertThat(e.nextBackOff()).isEqualTo(2000L);
        assertThat(e.nextBackOff()).isEqualTo(4000L);
        assertThat(e.nextBackOff()).isEqualTo(BackOffExecution.STOP);
        assertThat(b.getMaxInterval()).isEqualTo(10_000L);
    }

    @Test
    void poisonExceptionsAreNotRetryableOthersAre() {
        DefaultErrorHandler h = DltErrorHandlers.create(mock(KafkaTemplate.class));
        // removeClassification returns FALSE for a not-retryable class and null for an unclassified (retryable) one
        assertThat(h.removeClassification(IllegalArgumentException.class)).isFalse();
        assertThat(h.removeClassification(DeserializationException.class)).isFalse();
        assertThat(h.removeClassification(IllegalStateException.class)).isNull();
    }

    @Test
    void recovererPublishesToOriginalTopicDltKeepingKeyValueAndHeaders() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        DefaultErrorHandler h = DltErrorHandlers.create(template);

        ConsumerRecord<String, Object> rec = new ConsumerRecord<>("order-events", 2, 17L, "k1", "payload");
        rec.headers().add("__TypeId__", "orderPaid".getBytes());
        double before = Metrics.counter(DltErrorHandlers.METRIC, "topic", "order-events.DLT").count();

        h.handleOne(new IllegalArgumentException("bad"), rec, mock(Consumer.class), mock(MessageListenerContainer.class));

        org.mockito.ArgumentCaptor<ProducerRecord> cap = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(cap.capture());
        ProducerRecord sent = cap.getValue();
        assertThat(sent.topic()).isEqualTo("order-events.DLT");
        assertThat(sent.key()).isEqualTo("k1");
        assertThat(sent.value()).isEqualTo("payload");
        assertThat(sent.headers().lastHeader("__TypeId__")).isNotNull();
        assertThat(sent.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
        assertThat(sent.headers().lastHeader("kafka_dlt-original-offset")).isNotNull();
        assertThat(sent.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
        assertThat(Metrics.counter(DltErrorHandlers.METRIC, "topic", "order-events.DLT").count()).isEqualTo(before + 1);
    }

    @Test
    void failedDltPublishIsNotCounted() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        DefaultErrorHandler h = DltErrorHandlers.create(template);
        ConsumerRecord<String, Object> rec = new ConsumerRecord<>("auth-events", 0, 1L, "k", "v");
        double before = Metrics.counter(DltErrorHandlers.METRIC, "topic", "auth-events.DLT").count();

        try {
            // not handled (false) or thrown: either way the record is not committed and stays in retry
            boolean handled = h.handleOne(new IllegalArgumentException("bad"), rec, mock(Consumer.class), mock(MessageListenerContainer.class));
            assertThat(handled).isFalse();
        } catch (RuntimeException expected) {
            assertThat(expected).isNotNull();
        }
        assertThat(Metrics.counter(DltErrorHandlers.METRIC, "topic", "auth-events.DLT").count()).isEqualTo(before);
    }
}
