package com.waylo.observability;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Kafka side of the request id: header on send (from MDC), MDC on receive (from header). */
public final class KafkaRequestIdInterceptors {

    private KafkaRequestIdInterceptors() {
    }

    /** Adds the X-Request-Id header from the current MDC unless the record already carries one. */
    public static class Producer<K, V> implements ProducerInterceptor<K, V> {
        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            String id = MDC.get(RequestId.MDC_KEY);
            if (id != null && record.headers().lastHeader(RequestId.HEADER) == null) {
                record.headers().add(RequestId.HEADER, id.getBytes(StandardCharsets.UTF_8));
            }
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        }

        @Override
        public void close() {
        }

        @Override
        public void configure(Map<String, ?> configs) {
        }
    }

    /** Sets the MDC from the record header for the duration of the listener call. */
    public static class Listener<K, V> implements RecordInterceptor<K, V> {
        @Override
        public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
            Header h = record.headers().lastHeader(RequestId.HEADER);
            String id = h == null ? null : RequestId.sanitize(new String(h.value(), StandardCharsets.UTF_8));
            if (id != null) {
                MDC.put(RequestId.MDC_KEY, id);
            } else {
                MDC.remove(RequestId.MDC_KEY);
            }
            return record;
        }

        @Override
        public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
