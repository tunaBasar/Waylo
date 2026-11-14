package com.waylo.packagebookingservice.config;

import com.waylo.observability.OutboxBacklogGauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Registers waylo_outbox_unpublished (outbox_event rows waiting for the relay). */
@Configuration
public class OutboxMetricsConfig {

    public OutboxMetricsConfig(MeterRegistry registry, JdbcTemplate jdbc) {
        OutboxBacklogGauge.register(registry, jdbc);
    }
}
