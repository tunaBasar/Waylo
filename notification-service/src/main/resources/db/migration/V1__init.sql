-- notification service initial schema

CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

CREATE TABLE notifications (
    id            UUID          PRIMARY KEY,
    customer_id   UUID          NOT NULL,
    booking_id    UUID,
    type          VARCHAR(10)   NOT NULL,
    recipient     VARCHAR(255)  NOT NULL,
    subject       VARCHAR(500),
    body          TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count   INT           NOT NULL DEFAULT 0,
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_customer_id ON notifications (customer_id);
CREATE INDEX idx_notifications_booking_id ON notifications (booking_id);
CREATE INDEX idx_notifications_status ON notifications (status);
CREATE INDEX idx_notifications_type ON notifications (type);
