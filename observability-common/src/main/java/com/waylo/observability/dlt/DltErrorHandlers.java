package com.waylo.observability.dlt;

import com.waylo.observability.BusinessMetrics;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

/**
 * One retry/DLT policy for every Kafka consumer: 3 retries with 1s/2s/4s backoff (max 10s), then the record goes to
 * {@code <topic>.DLT} with key, value, original headers, exception and original topic/partition/offset headers.
 * Poison records (deserialization, IllegalArgument, validation) skip the retries. The offset is committed only after
 * the DLT publish succeeded (commitRecovered). If the publish fails the recoverer throws and the record stays in retry.
 */
public final class DltErrorHandlers {

    public static final String DLT_SUFFIX = ".DLT";
    public static final String METRIC = "waylo.kafka.dlt.published";

    private DltErrorHandlers() {
    }

    public static ExponentialBackOffWithMaxRetries backOff() {
        ExponentialBackOffWithMaxRetries b = new ExponentialBackOffWithMaxRetries(3);
        b.setInitialInterval(1000L);
        b.setMultiplier(2.0);
        b.setMaxInterval(10_000L);
        return b;
    }

    /** Template used only for DLT publishing. Deliberately not a bean, so Boot's own KafkaTemplate stays in place. */
    public static KafkaTemplate<Object, Object> dltTemplate(String bootstrapServers, Serializer<Object> json) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // headers (incl. __TypeId__) are copied from the original record, so the serializer must not rewrite them
        Serializer<Object> passThroughOrJson = new Serializer<>() {
            @Override
            public byte[] serialize(String topic, Object data) {
                return data instanceof byte[] b ? b : json.serialize(topic, data);
            }
        };
        StringSerializer str = new StringSerializer();
        Serializer<Object> key = new Serializer<>() {
            @Override
            public byte[] serialize(String topic, Object data) {
                return data instanceof byte[] b ? b : str.serialize(topic, data == null ? null : data.toString());
            }
        };
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, key, passThroughOrJson));
    }

    /** @param json value serializer of the service (a JsonSerializer with setAddTypeInfo(false)), used for non-byte[] values */
    public static DefaultErrorHandler create(String bootstrapServers, Serializer<Object> json) {
        return create(dltTemplate(bootstrapServers, json));
    }

    public static DefaultErrorHandler create(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer dlpr = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        dlpr.setFailIfSendResultIsError(true);
        dlpr.setLogRecoveryRecord(true);
        ConsumerRecordRecoverer counted = (record, ex) -> {
            dlpr.accept(record, ex); // throws when the publish fails: no commit, record stays in retry
            BusinessMetrics.increment(METRIC, "topic", record.topic() + DLT_SUFFIX);
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(counted, backOff());
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Exception> v = (Class<? extends Exception>) Class.forName("jakarta.validation.ValidationException");
            handler.addNotRetryableExceptions(v);
        } catch (ClassNotFoundException ignored) {
            // service without bean validation on the classpath
        }
        // DeserializationException is not-retryable by Spring default. Commit only after a successful recovery.
        handler.setCommitRecovered(true);
        return handler;
    }
}
