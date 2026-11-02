-- order-level seat holds (internal flight supplier), mirrors car-rental holds
CREATE TABLE seat_holds (
    id                   UUID          PRIMARY KEY,
    idempotency_key      VARCHAR(100)  NOT NULL,
    order_correlation_id VARCHAR(100),
    principal_user_id    UUID          NOT NULL,
    seat_id              UUID          NOT NULL REFERENCES flight_seats (id),
    flight_number        VARCHAR(10)   NOT NULL,
    seat_number          VARCHAR(5)    NOT NULL,
    cabin                VARCHAR(20)   NOT NULL,
    passenger_first_name VARCHAR(80)   NOT NULL,
    passenger_last_name  VARCHAR(80)   NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'TRY',
    total_amount         NUMERIC(12,2) NOT NULL,
    departure_time       TIMESTAMP     NOT NULL,
    arrival_time         TIMESTAMP     NOT NULL,
    departure_airport    VARCHAR(10)   NOT NULL,
    arrival_airport      VARCHAR(10)   NOT NULL,
    airline              VARCHAR(100)  NOT NULL,
    status               VARCHAR(12)   NOT NULL,
    hold_expires_at      TIMESTAMP     NOT NULL,
    pnr                  VARCHAR(8),
    ticket_number        VARCHAR(20),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seat_holds_idem UNIQUE (idempotency_key),
    CONSTRAINT ck_seat_holds_status CHECK (status IN ('HELD','CONFIRMED','CANCELLED','EXPIRED'))
);

-- one live (HELD or CONFIRMED) hold per seat, enforced by the database as a second line of defence
CREATE UNIQUE INDEX uq_seat_holds_live_seat ON seat_holds (seat_id) WHERE status IN ('HELD','CONFIRMED');
CREATE INDEX idx_seat_holds_expiry ON seat_holds (status, hold_expires_at);
