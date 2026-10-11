-- package booking service initial schema

-- Kafka consumer idempotency tablosu
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Paket rezervasyonlari
CREATE TABLE package_bookings (
    id                      UUID           PRIMARY KEY,
    customer_id             UUID           NOT NULL,
    flight_id               UUID,
    hotel_reservation_id    UUID,
    vehicle_reservation_id  UUID,
    status                  VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    total_price             NUMERIC(12,2),
    notes                   TEXT,
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_customer_id ON package_bookings (customer_id);
CREATE INDEX idx_bookings_status ON package_bookings (status);
CREATE INDEX idx_bookings_customer_status ON package_bookings (customer_id, status);
CREATE INDEX idx_bookings_flight_id ON package_bookings (flight_id);
CREATE INDEX idx_bookings_hotel_id ON package_bookings (hotel_reservation_id);
CREATE INDEX idx_bookings_vehicle_id ON package_bookings (vehicle_reservation_id);
