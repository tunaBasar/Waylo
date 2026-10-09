-- flight seat inventory service initial schema

-- Kafka consumer idempotency tablosu
-- Composite PK: aynı event farklı consumer group'larda bağımsız işlenir
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Uçuş koltuk envanteri
CREATE TABLE flight_seats (
    id              UUID         PRIMARY KEY,
    flight_number   VARCHAR(10)  NOT NULL,
    seat_number     VARCHAR(5)   NOT NULL,
    seat_type       VARCHAR(20)  NOT NULL,
    price           NUMERIC(10,2) NOT NULL,
    is_available    BOOLEAN      NOT NULL DEFAULT TRUE,
    reservation_id  VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (flight_number, seat_number)
);

CREATE INDEX idx_flight_seats_flight_number ON flight_seats (flight_number);
CREATE INDEX idx_flight_seats_available ON flight_seats (flight_number, is_available);
CREATE INDEX idx_flight_seats_reservation ON flight_seats (reservation_id);
