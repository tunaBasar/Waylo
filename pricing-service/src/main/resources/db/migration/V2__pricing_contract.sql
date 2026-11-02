-- PRICING-CONTRACT §0a: migrate old pricing_rules, add new tables

-- Step 1: rename old pricing_rules (keep for audit)
ALTER TABLE pricing_rules RENAME TO pricing_rules__old;

-- Step 2: create campaigns table
CREATE TABLE campaigns (
    id                   UUID           PRIMARY KEY,
    name                 VARCHAR(255)   NOT NULL,
    vertical             VARCHAR(20)    NOT NULL,
    scope_org_id         UUID,
    discount_type        VARCHAR(20)    NOT NULL,
    discount_value       NUMERIC(10,4)  NOT NULL,
    min_order_amount     NUMERIC(12,2)  NOT NULL DEFAULT 0,
    start_at             TIMESTAMP      NOT NULL,
    end_at               TIMESTAMP      NOT NULL,
    max_uses_global      INTEGER,
    max_uses_per_user    INTEGER,
    funded_by            VARCHAR(20)    NOT NULL,
    description          TEXT,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_campaigns_name_vertical_start
    ON campaigns (name, vertical, start_at);

-- Step 3: create promo_codes table
CREATE TABLE promo_codes (
    id               UUID           PRIMARY KEY,
    campaign_id      UUID           NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    code             VARCHAR(50)    NOT NULL,
    max_uses_total   INTEGER,
    uses_count       INTEGER        NOT NULL DEFAULT 0,
    valid_from       TIMESTAMP      NOT NULL,
    valid_until      TIMESTAMP      NOT NULL
);

CREATE UNIQUE INDEX uq_promo_codes_campaign_code
    ON promo_codes (campaign_id, code);

-- Step 4: create promo_reservations table
CREATE TABLE promo_reservations (
    id               UUID           PRIMARY KEY,
    promo_code_id    UUID           NOT NULL REFERENCES promo_codes(id) ON DELETE CASCADE,
    order_id         UUID           NOT NULL UNIQUE,
    customer_id      VARCHAR(100)   NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'RESERVED',
    gross_amount     NUMERIC(12,2)  NOT NULL,
    discount_amount  NUMERIC(12,2)  NOT NULL DEFAULT 0,
    net_amount       NUMERIC(12,2)  NOT NULL,
    discount_funded_by VARCHAR(20),
    tax_amount       NUMERIC(12,2),
    discount_json    TEXT,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promo_reservations_promo_code_id
    ON promo_reservations (promo_code_id);

CREATE INDEX idx_promo_reservations_customer_id
    ON promo_reservations (customer_id);

-- Step 5: create tax_rates table
CREATE TABLE tax_rates (
    id               UUID           PRIMARY KEY,
    vertical         VARCHAR(20)    NOT NULL,
    scope_org_id     UUID,
    tax_type         VARCHAR(50)    NOT NULL,
    rate             NUMERIC(5,4)   NOT NULL CHECK (rate >= 0 AND rate <= 1),
    inclusive        BOOLEAN        NOT NULL,
    valid_from       TIMESTAMP      NOT NULL
);
