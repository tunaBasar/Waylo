-- Transactional outbox: events are written in the business transaction and relayed to Kafka by OutboxRelay.
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    event_key       VARCHAR(100) NOT NULL,
    type_id         VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_event_pending ON outbox_event (next_attempt_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_id) WHERE published_at IS NULL;
