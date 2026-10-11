-- pricing service initial schema

-- Kafka consumer idempotency tablosu
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Fiyatlandirma kurallari
CREATE TABLE pricing_rules (
    id                   UUID           PRIMARY KEY,
    name                 VARCHAR(255)   NOT NULL,
    pricing_type         VARCHAR(30)    NOT NULL,
    base_price           NUMERIC(12,2)  NOT NULL CHECK (base_price >= 0),
    discount_percentage  NUMERIC(5,2),
    currency             VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    description          TEXT,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pricing_rules_type ON pricing_rules (pricing_type);
CREATE INDEX idx_pricing_rules_active ON pricing_rules (active);
CREATE INDEX idx_pricing_rules_type_active ON pricing_rules (pricing_type, active);
CREATE INDEX idx_pricing_rules_currency ON pricing_rules (currency);
