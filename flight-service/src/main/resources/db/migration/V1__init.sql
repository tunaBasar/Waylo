-- flight-service initial schema

-- Kafka consumer idempotency tablosu
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Havalimanları
CREATE TABLE airports (
    id          UUID         PRIMARY KEY,
    iata_code   VARCHAR(3)   NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(255) NOT NULL,
    country     VARCHAR(255) NOT NULL,
    timezone    VARCHAR(64)  NOT NULL,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Uçuşlar (airports FK'ya bağımlı, sonra oluşturulur)
CREATE TABLE flights (
    id                     UUID         PRIMARY KEY,
    flight_number          VARCHAR(10)  NOT NULL UNIQUE,
    departure_airport_id   UUID         NOT NULL REFERENCES airports(id),
    arrival_airport_id     UUID         NOT NULL REFERENCES airports(id),
    departure_time         TIMESTAMP    NOT NULL,
    arrival_time           TIMESTAMP    NOT NULL,
    airline                VARCHAR(100) NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Performans index'leri
CREATE INDEX idx_flights_departure_airport ON flights (departure_airport_id);
CREATE INDEX idx_flights_arrival_airport ON flights (arrival_airport_id);
CREATE INDEX idx_flights_departure_time ON flights (departure_time);
CREATE INDEX idx_flights_dep_arr_time ON flights (departure_airport_id, arrival_airport_id, departure_time);
CREATE INDEX idx_flights_flight_number ON flights (flight_number);
CREATE INDEX idx_flights_status ON flights (status);
