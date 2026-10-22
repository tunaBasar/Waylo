CREATE TABLE hotel_quotes (
    id                            UUID PRIMARY KEY,
    hotel_id                      UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    room_type_id                  UUID NOT NULL,
    rate_plan_id                  UUID NOT NULL,
    check_in                      DATE NOT NULL,
    check_out                     DATE NOT NULL,
    rooms                         INT NOT NULL CHECK (rooms BETWEEN 1 AND 10),
    adults                        INT NOT NULL CHECK (adults BETWEEN 1 AND 20),
    children_ages                 VARCHAR(100) NOT NULL DEFAULT '',  -- CSV of ints, '' = none
    occupancy                     INT NOT NULL CHECK (occupancy BETWEEN 1 AND 30),
    currency                      CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount                  NUMERIC(12,2) NOT NULL CHECK (total_amount > 0),
    meal_plan                     VARCHAR(20) NOT NULL,
    cancellation_policy           VARCHAR(30) NOT NULL,
    free_cancellation_days_before INT,
    room_type_code                VARCHAR(30) NOT NULL,
    room_type_name                VARCHAR(120) NOT NULL,
    rate_plan_code                VARCHAR(30) NOT NULL,
    rate_plan_name                VARCHAR(120) NOT NULL,
    created_at                    TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at                    TIMESTAMP NOT NULL,
    principal_user_id             UUID NOT NULL,
    CONSTRAINT chk_hotel_quotes_stay CHECK (check_out > check_in),
    CONSTRAINT fk_hotel_quotes_room_type FOREIGN KEY (hotel_id, room_type_id)
        REFERENCES room_types (hotel_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_hotel_quotes_rate_plan FOREIGN KEY (hotel_id, rate_plan_id)
        REFERENCES rate_plans (hotel_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_hotel_quotes_hotel ON hotel_quotes (hotel_id, check_in);

-- Frozen nightly price snapshot. Immutable: never UPDATEd after quote creation.
CREATE TABLE hotel_quote_nights (
    id         UUID PRIMARY KEY,
    quote_id   UUID NOT NULL REFERENCES hotel_quotes(id) ON DELETE CASCADE,
    stay_date  DATE NOT NULL,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency   CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    UNIQUE (quote_id, stay_date)
);
CREATE INDEX idx_hotel_quote_nights_quote ON hotel_quote_nights (quote_id);

CREATE TABLE hotel_holds (
    id                   UUID PRIMARY KEY,
    quote_id             UUID NOT NULL REFERENCES hotel_quotes(id),
    hotel_id             UUID NOT NULL,
    room_type_id         UUID NOT NULL,
    rate_plan_id         UUID NOT NULL,
    check_in             DATE NOT NULL,
    check_out            DATE NOT NULL,
    rooms                INT NOT NULL CHECK (rooms BETWEEN 1 AND 10),
    status               VARCHAR(20) NOT NULL,
    idempotency_key      VARCHAR(100) NOT NULL,
    principal_user_id            UUID NOT NULL,
    order_correlation_id UUID,
    currency             CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount         NUMERIC(12,2) NOT NULL CHECK (total_amount > 0),
    expires_at           TIMESTAMP NOT NULL,
    confirmed_at         TIMESTAMP,
    released_at          TIMESTAMP,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hotel_holds_status CHECK (status IN ('HELD','CONFIRMED','RELEASED','EXPIRED')),
    CONSTRAINT chk_hotel_holds_stay CHECK (check_out > check_in),
    CONSTRAINT uq_hotel_holds_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_hotel_holds_status_expires ON hotel_holds (status, expires_at);
CREATE INDEX idx_hotel_holds_quote ON hotel_holds (quote_id);
