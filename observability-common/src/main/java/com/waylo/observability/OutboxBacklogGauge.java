package com.waylo.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

/** waylo_outbox_unpublished: rows of outbox_event not yet relayed to Kafka. Evaluated on every scrape. */
public final class OutboxBacklogGauge {

    private OutboxBacklogGauge() {
    }

    public static void register(MeterRegistry registry, JdbcTemplate jdbc) {
        Gauge.builder("waylo.outbox.unpublished", jdbc, j -> {
                    Long n = j.queryForObject("SELECT count(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
                    return n == null ? 0 : n;
                })
                .description("outbox_event rows with published_at IS NULL")
                .register(registry);
    }
}
