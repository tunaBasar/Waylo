-- Room types catalog
CREATE TABLE room_types (
    id              UUID PRIMARY KEY,
    hotel_id        UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    code            VARCHAR(30) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    max_adults      INT NOT NULL CHECK (max_adults >= 1 AND max_adults <= 20),
    max_children    INT NOT NULL CHECK (max_children >= 0 AND max_children <= 10),
    max_occupancy   INT NOT NULL CHECK (max_occupancy >= max_adults AND max_occupancy <= max_adults + max_children),
    size_sqm        INT CHECK (size_sqm IS NULL OR (size_sqm >= 1 AND size_sqm <= 2000)),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, code),
    UNIQUE (hotel_id, id)
);

CREATE INDEX idx_room_types_hotel_status ON room_types (hotel_id, status);

-- Rate plans catalog
CREATE TABLE rate_plans (
    id                          UUID PRIMARY KEY,
    hotel_id                    UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    code                        VARCHAR(30) NOT NULL,
    name                        VARCHAR(120) NOT NULL,
    description                 TEXT,
    meal_plan                   VARCHAR(20) NOT NULL,
    cancellation_policy         VARCHAR(30) NOT NULL,
    free_cancellation_days_before INT CHECK ((cancellation_policy = 'FREE_CANCELLATION' AND free_cancellation_days_before >= 0 AND free_cancellation_days_before <= 365) OR (cancellation_policy = 'NON_REFUNDABLE' AND free_cancellation_days_before IS NULL)),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, code),
    UNIQUE (hotel_id, id)
);

CREATE INDEX idx_rate_plans_hotel_status ON rate_plans (hotel_id, status);
