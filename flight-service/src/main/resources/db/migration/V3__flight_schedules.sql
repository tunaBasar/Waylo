-- Recurring flight schedules (contract 4.2). weekdays: bit (DayOfWeek.getValue() - 1), MONDAY = 1, SUNDAY = 64.
CREATE TABLE flight_schedules (
    id                    UUID         PRIMARY KEY,
    code                  VARCHAR(5)   NOT NULL UNIQUE,
    airline               VARCHAR(100) NOT NULL,
    departure_airport_id  UUID         NOT NULL REFERENCES airports(id),
    arrival_airport_id    UUID         NOT NULL REFERENCES airports(id),
    weekdays              SMALLINT     NOT NULL CHECK (weekdays BETWEEN 1 AND 127),
    departure_time        TIME         NOT NULL,
    duration_minutes      INT          NOT NULL CHECK (duration_minutes BETWEEN 20 AND 1200),
    valid_from            DATE         NOT NULL,
    valid_to              DATE,
    horizon_days          INT          NOT NULL DEFAULT 30 CHECK (horizon_days BETWEEN 1 AND 120),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK (departure_airport_id <> arrival_airport_id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE flight_schedule_cabins (
    id           UUID          PRIMARY KEY,
    schedule_id  UUID          NOT NULL REFERENCES flight_schedules(id) ON DELETE CASCADE,
    seat_type    VARCHAR(20)   NOT NULL CHECK (seat_type IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),
    price        NUMERIC(10,2) NOT NULL CHECK (price > 0),
    from_row     INT           NOT NULL CHECK (from_row BETWEEN 1 AND 999),
    to_row       INT           NOT NULL CHECK (to_row BETWEEN 1 AND 999),
    letters      VARCHAR(11)   NOT NULL,
    CHECK (from_row <= to_row),
    UNIQUE (schedule_id, seat_type) DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE flights
    ADD COLUMN schedule_id UUID NULL REFERENCES flight_schedules(id) ON DELETE SET NULL;

CREATE INDEX idx_flights_schedule_departure ON flights (schedule_id, departure_time);
