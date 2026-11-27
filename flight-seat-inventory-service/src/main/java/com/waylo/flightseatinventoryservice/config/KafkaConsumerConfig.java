package com.waylo.flightseatinventoryservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import com.waylo.observability.dlt.DltAdminController;
import com.waylo.observability.dlt.DltBrowser;
import com.waylo.observability.dlt.DltErrorHandlers;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final String TYPE_MAPPINGS =
            "flightCreated:com.waylo.flightseatinventoryservice.event.FlightCreatedEvent,"
          + "flightCancelled:com.waylo.flightseatinventoryservice.event.FlightCancelledEvent";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

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
     * Retry 3 times (1s/2s/4s, max 10s), then publish to <topic>.DLT (see DltErrorHandlers). The offset is committed
     * only after the DLT publish succeeded. Poison records (deserialization, IllegalArgument, validation) go straight to the DLT.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.setAddTypeInfo(false); // original headers (incl. __TypeId__) are copied by the recoverer
        return DltErrorHandlers.create(bootstrapServers, json);
    }

    @Bean
    public DltBrowser dltBrowser() {
        return new DltBrowser(bootstrapServers, "flight-seat-inventory-service");
    }

    @Bean
    public DltAdminController dltAdminController(DltBrowser dltBrowser) {
        return new DltAdminController(dltBrowser);
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
