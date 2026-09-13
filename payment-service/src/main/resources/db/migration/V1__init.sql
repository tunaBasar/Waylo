-- payment service initial schema

-- Kafka consumer idempotency tablosu
-- Composite PK: aynı event farklı consumer group'larda bağımsız işlenir
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
