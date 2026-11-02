-- Car rental v2: partner-owned fleet. Old dev seed vehicles/rentals have no owner and are dropped.
DROP TABLE IF EXISTS rentals;
DROP TABLE IF EXISTS vehicles;

CREATE TABLE branches (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    address         VARCHAR(300) NOT NULL,
    phone           VARCHAR(40),
    open_time       TIME         NOT NULL,
    close_time      TIME         NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (close_time > open_time)
);
CREATE INDEX idx_branches_org ON branches (organization_id);
CREATE INDEX idx_branches_city ON branches (lower(city));

CREATE TABLE vehicles (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    home_branch_id  UUID         NOT NULL REFERENCES branches (id),
    plate_number    VARCHAR(20)  NOT NULL UNIQUE,
    brand           VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    year            INT          NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    transmission    VARCHAR(20)  NOT NULL,
    fuel_type       VARCHAR(20)  NOT NULL,
    seat_count      INT          NOT NULL CHECK (seat_count BETWEEN 1 AND 20),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicles_org ON vehicles (organization_id);
CREATE INDEX idx_vehicles_cat ON vehicles (organization_id, category, transmission);

CREATE TABLE category_rates (
    id                 UUID PRIMARY KEY,
    organization_id    UUID          NOT NULL,
    branch_id          UUID          NOT NULL REFERENCES branches (id),
    category           VARCHAR(20)   NOT NULL,
    transmission       VARCHAR(20)   NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    daily_rate         NUMERIC(12,2) NOT NULL CHECK (daily_rate > 0),
    deposit_amount     NUMERIC(12,2) NOT NULL CHECK (deposit_amount >= 0),
    km_limit_per_day   INT,
    extra_km_fee       NUMERIC(12,2),
    min_driver_age     INT           NOT NULL CHECK (min_driver_age BETWEEN 18 AND 99),
    min_license_years  INT           NOT NULL CHECK (min_license_years BETWEEN 0 AND 50),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (branch_id, category, transmission)
);
CREATE INDEX idx_rates_org ON category_rates (organization_id);

CREATE TABLE one_way_routes (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL,
    from_branch_id  UUID          NOT NULL REFERENCES branches (id),
    to_branch_id    UUID          NOT NULL REFERENCES branches (id),
    fee             NUMERIC(12,2) NOT NULL CHECK (fee >= 0),
    currency        VARCHAR(3)    NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (from_branch_id, to_branch_id),
    CHECK (from_branch_id <> to_branch_id)
);
CREATE INDEX idx_routes_org ON one_way_routes (organization_id);

CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    vehicle_id      UUID REFERENCES vehicles (id),
    type            VARCHAR(30)  NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    valid_until     DATE,
    status          VARCHAR(20)  NOT NULL,
    reject_reason   VARCHAR(500),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(60)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_key     VARCHAR(64)  NOT NULL UNIQUE,
    uploaded_by     UUID         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_org ON documents (organization_id);
CREATE INDEX idx_documents_vehicle ON documents (vehicle_id);
CREATE INDEX idx_documents_status ON documents (status);

CREATE TABLE rentals (
    id                   UUID PRIMARY KEY,
    organization_id      UUID          NOT NULL,
    customer_id          UUID          NOT NULL,
    vehicle_id           UUID          NOT NULL REFERENCES vehicles (id),
    pickup_branch_id     UUID          NOT NULL REFERENCES branches (id),
    return_branch_id     UUID          NOT NULL REFERENCES branches (id),
    category             VARCHAR(20)   NOT NULL,
    transmission         VARCHAR(20)   NOT NULL,
    pickup_at            TIMESTAMPTZ   NOT NULL,
    return_at            TIMESTAMPTZ   NOT NULL,
    chargeable_days      INT           NOT NULL,
    daily_rate           NUMERIC(12,2) NOT NULL,
    one_way_fee          NUMERIC(12,2) NOT NULL,
    total_amount         NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    deposit_amount       NUMERIC(12,2) NOT NULL,
    km_limit_per_day     INT,
    driver_age           INT           NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    hold_expires_at      TIMESTAMPTZ   NOT NULL,
    idempotency_key      VARCHAR(100)  NOT NULL UNIQUE,
    order_correlation_id VARCHAR(100),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (return_at > pickup_at)
);
CREATE INDEX idx_rentals_vehicle ON rentals (vehicle_id, pickup_at, return_at);
CREATE INDEX idx_rentals_customer ON rentals (customer_id);
CREATE INDEX idx_rentals_org ON rentals (organization_id, status);
