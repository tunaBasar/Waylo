-- hotel service initial schema

-- Kafka consumer idempotency tablosu
-- Composite PK: ayni event farkli consumer group'larda bagimsiz islenir
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- Oteller
CREATE TABLE hotels (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    city         VARCHAR(255) NOT NULL,
    country      VARCHAR(255) NOT NULL,
    address      VARCHAR(500) NOT NULL,
    star_rating  INT          NOT NULL CHECK (star_rating BETWEEN 1 AND 5),
    description  TEXT,
    latitude     NUMERIC(9,6),
    longitude    NUMERIC(9,6),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hotels_city ON hotels (city);
CREATE INDEX idx_hotels_country ON hotels (country);
CREATE INDEX idx_hotels_city_country ON hotels (city, country);
CREATE INDEX idx_hotels_star_rating ON hotels (star_rating);
CREATE INDEX idx_hotels_active ON hotels (active);

-- Odalar (hotels FK'ya bagimli)
CREATE TABLE rooms (
    id              UUID           PRIMARY KEY,
    hotel_id        UUID           NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    room_number     VARCHAR(20)    NOT NULL,
    room_type       VARCHAR(20)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    capacity        INT            NOT NULL CHECK (capacity >= 1),
    price_per_night NUMERIC(10,2)  NOT NULL CHECK (price_per_night >= 0),
    floor           INT,
    description     TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, room_number)
);

CREATE INDEX idx_rooms_hotel_id ON rooms (hotel_id);
CREATE INDEX idx_rooms_hotel_status ON rooms (hotel_id, status);
CREATE INDEX idx_rooms_hotel_type ON rooms (hotel_id, room_type);
CREATE INDEX idx_rooms_hotel_type_status ON rooms (hotel_id, room_type, status);
CREATE INDEX idx_rooms_hotel_price ON rooms (hotel_id, price_per_night);
