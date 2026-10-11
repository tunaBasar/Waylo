-- saga orchestrator initial schema

CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

CREATE TABLE saga_instances (
    id             UUID         PRIMARY KEY,
    booking_id     UUID         NOT NULL UNIQUE,
    customer_id    UUID         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'STARTED',
    current_step   VARCHAR(30),
    failure_reason TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_instances_booking_id ON saga_instances (booking_id);
CREATE INDEX idx_saga_instances_customer_id ON saga_instances (customer_id);
CREATE INDEX idx_saga_instances_status ON saga_instances (status);

CREATE TABLE saga_steps (
    id                UUID         PRIMARY KEY,
    saga_instance_id  UUID         NOT NULL REFERENCES saga_instances(id) ON DELETE CASCADE,
    step_type         VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    step_order        INT          NOT NULL,
    request_payload   TEXT,
    response_payload  TEXT,
    error_message     TEXT,
    started_at        TIMESTAMP,
    completed_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_steps_instance_id ON saga_steps (saga_instance_id);
CREATE INDEX idx_saga_steps_status ON saga_steps (status);
