-- checkin service initial schema

CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

CREATE TABLE checkins (
    id                UUID         PRIMARY KEY,
    booking_id        UUID         NOT NULL,
    customer_id       UUID         NOT NULL,
    flight_id         UUID         NOT NULL,
    seat_number       VARCHAR(10),
    boarding_pass_url VARCHAR(500),
    status            VARCHAR(30)  NOT NULL DEFAULT 'NOT_CHECKED_IN',
    checked_in_at     TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkins_booking_id ON checkins (booking_id);
CREATE INDEX idx_checkins_customer_id ON checkins (customer_id);
CREATE INDEX idx_checkins_flight_id ON checkins (flight_id);
CREATE INDEX idx_checkins_status ON checkins (status);
CREATE UNIQUE INDEX idx_checkins_booking_flight ON checkins (booking_id, flight_id);
