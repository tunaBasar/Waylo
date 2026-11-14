-- push device token registry (one row per user + token)

CREATE TABLE device_tokens (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL,
    token       VARCHAR(512)  NOT NULL,
    platform    VARCHAR(10)   NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_tokens_user_token UNIQUE (user_id, token)
);

CREATE INDEX idx_device_tokens_token ON device_tokens (token);
