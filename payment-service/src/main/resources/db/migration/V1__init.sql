-- payment service initial schema

CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

CREATE TABLE payments (
    id              UUID           PRIMARY KEY,
    booking_id      UUID           NOT NULL,
    customer_id     UUID           NOT NULL,
    amount          NUMERIC(12,2)  NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    payment_method  VARCHAR(20)    NOT NULL DEFAULT 'MOCK',
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(255)   UNIQUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_booking_id ON payments (booking_id);
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_transaction_ref ON payments (transaction_ref);
