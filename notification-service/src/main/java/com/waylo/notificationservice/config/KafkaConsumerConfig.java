package com.waylo.notificationservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Maps the __TypeId__ header tokens that producers (flight-service, package-booking-service,
     * payment-service) stamp on their events to this service's own locally-declared event
     * classes of the same shape. Producers and consumers each have their own copy of these
     * classes in their own package, so Jackson's default "use the sender's FQCN" behavior
     * would otherwise fail with ClassNotFoundException for every cross-service event.
     * Token names must stay in sync with each producer's TYPE_MAPPINGS.
     */
    private static final String TYPE_MAPPINGS =
            "bookingCreated:com.waylo.notificationservice.event.BookingCreatedEvent,"
          + "bookingCancelled:com.waylo.notificationservice.event.BookingCancelledEvent,"
          + "flightCreated:com.waylo.notificationservice.event.FlightCreatedEvent,"
          + "flightCancelled:com.waylo.notificationservice.event.FlightCancelledEvent,"
          + "paymentCompleted:com.waylo.notificationservice.event.PaymentCompletedEvent,"
          + "paymentRefunded:com.waylo.notificationservice.event.PaymentRefundedEvent";

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waylo.*");
        props.put(JsonDeserializer.TYPE_MAPPINGS, TYPE_MAPPINGS);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Without an error handler, a record this consumer can never deserialize or process
     * (a "poison pill", e.g. an old message stamped with an unrecognized type header, or a
     * genuinely malformed payload) would be redelivered forever and permanently block every
     * later, valid record behind it on the same partition. This retries a failing record a
     * couple of times (in case the failure is transient, e.g. a dependency briefly down) and
     * then logs and skips it so the partition keeps moving.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Skipping unprocessable Kafka record after retries: topic={}, partition={}, offset={}, error={}",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()),
                new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class);
        // Listener never runs (and never acks) for a skipped record under MANUAL_IMMEDIATE, so
        // commit its offset here; otherwise lag stays non-zero and it is re-consumed on restart.
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler());
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        return factory;
    }
}
