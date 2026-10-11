-- car rental service initial schema

-- Kafka consumer idempotency tablosu
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Araclar
CREATE TABLE vehicles (
    id            UUID           PRIMARY KEY,
    plate_number  VARCHAR(20)    NOT NULL UNIQUE,
    brand         VARCHAR(100)   NOT NULL,
    model         VARCHAR(100)   NOT NULL,
    year          INT            NOT NULL,
    vehicle_type  VARCHAR(20)    NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    color         VARCHAR(50),
    seat_count    INT            NOT NULL CHECK (seat_count >= 1),
    daily_rate    NUMERIC(10,2)  NOT NULL CHECK (daily_rate >= 0),
    location      VARCHAR(255)   NOT NULL,
    mileage       INT,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vehicles_plate_number ON vehicles (plate_number);
CREATE INDEX idx_vehicles_location ON vehicles (location);
CREATE INDEX idx_vehicles_vehicle_type ON vehicles (vehicle_type);
CREATE INDEX idx_vehicles_status ON vehicles (status);
CREATE INDEX idx_vehicles_location_status ON vehicles (location, status);
CREATE INDEX idx_vehicles_location_type_status ON vehicles (location, vehicle_type, status);
CREATE INDEX idx_vehicles_daily_rate ON vehicles (daily_rate);
CREATE INDEX idx_vehicles_active ON vehicles (active);
