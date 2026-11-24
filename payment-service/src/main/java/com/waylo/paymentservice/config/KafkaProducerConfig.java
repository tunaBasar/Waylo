package com.waylo.paymentservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Shared type tokens for the __TypeId__ header so that consumer services (which have
     * their own locally-declared event classes in a different package) can resolve the
     * payload type instead of trying to Class.forName() this service's own FQCN.
     * Must stay in sync with the TYPE_MAPPINGS token names used by every consumer
     * (notification-service).
     */
    public static final String TYPE_MAPPINGS =
            "paymentCompleted:com.waylo.paymentservice.event.PaymentCompletedEvent,"
          + "paymentRefunded:com.waylo.paymentservice.event.PaymentRefundedEvent,"
          + "payoutPaid:com.waylo.paymentservice.event.PayoutPaidEvent,"
          + "payoutFailed:com.waylo.paymentservice.event.PayoutFailedEvent";

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.TYPE_MAPPINGS, TYPE_MAPPINGS);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /** Raw string producer used by OutboxRelay: the payload is already serialized JSON stored in outbox_event. */
    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 8000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 4000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
