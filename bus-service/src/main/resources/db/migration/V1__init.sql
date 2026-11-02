-- Bus vertical: partner bus companies, trips with route stops, seat tickets.
CREATE TABLE terminals (
    id         UUID PRIMARY KEY,
    city       VARCHAR(100) NOT NULL,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (city, name)
);
CREATE INDEX idx_terminals_city ON terminals (lower(city));

CREATE TABLE buses (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    plate_number    VARCHAR(20)  NOT NULL UNIQUE,
    brand           VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    year            INT          NOT NULL,
    layout          VARCHAR(20)  NOT NULL,
    row_count       INT          NOT NULL CHECK (row_count BETWEEN 5 AND 20),
    seat_count      INT          NOT NULL CHECK (seat_count BETWEEN 1 AND 80),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_buses_org ON buses (organization_id);

CREATE TABLE trips (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    bus_id          UUID         NOT NULL REFERENCES buses (id),
    code            VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    departure_at    TIMESTAMPTZ  NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (organization_id, code, departure_at)
);
CREATE INDEX idx_trips_org ON trips (organization_id);
CREATE INDEX idx_trips_departure ON trips (departure_at);

CREATE TABLE trip_stops (
    id           UUID PRIMARY KEY,
    trip_id      UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    seq          INT         NOT NULL CHECK (seq >= 0),
    terminal_id  UUID        NOT NULL REFERENCES terminals (id),
    arrival_at   TIMESTAMPTZ,
    departure_at TIMESTAMPTZ,
    UNIQUE (trip_id, seq)
);

CREATE TABLE trip_fares (
    id       UUID PRIMARY KEY,
    trip_id  UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    from_seq INT           NOT NULL,
    to_seq   INT           NOT NULL,
    price    NUMERIC(12,2) NOT NULL CHECK (price > 0),
    UNIQUE (trip_id, from_seq, to_seq),
    CHECK (to_seq > from_seq)
);

CREATE TABLE bookings (
    id                   UUID PRIMARY KEY,
    organization_id      UUID          NOT NULL,
    customer_id          UUID          NOT NULL,
    trip_id              UUID          NOT NULL REFERENCES trips (id),
    from_seq             INT           NOT NULL,
    to_seq               INT           NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    total_amount         NUMERIC(12,2) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    hold_expires_at      TIMESTAMPTZ   NOT NULL,
    idempotency_key      VARCHAR(100)  NOT NULL UNIQUE,
    order_correlation_id VARCHAR(100),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_customer ON bookings (customer_id);
CREATE INDEX idx_bookings_org ON bookings (organization_id, status);

CREATE TABLE tickets (
    id              UUID PRIMARY KEY,
    booking_id      UUID          NOT NULL REFERENCES bookings (id),
    trip_id         UUID          NOT NULL REFERENCES trips (id),
    seat_no         INT           NOT NULL,
    from_seq        INT           NOT NULL,
    to_seq          INT           NOT NULL,
    passenger_name  VARCHAR(150)  NOT NULL,
    gender          VARCHAR(10)   NOT NULL,
    identity_number VARCHAR(20),
    price           NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    hold_expires_at TIMESTAMPTZ   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_trip ON tickets (trip_id, seat_no);
CREATE INDEX idx_tickets_booking ON tickets (booking_id);

CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    bus_id          UUID REFERENCES buses (id),
    type            VARCHAR(40)  NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    valid_until     DATE,
    status          VARCHAR(20)  NOT NULL,
    reject_reason   VARCHAR(500),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    file_name       VARCHAR(200) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_key     VARCHAR(80)  NOT NULL,
    uploaded_by     UUID         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_org ON documents (organization_id);
CREATE INDEX idx_documents_bus ON documents (bus_id);
