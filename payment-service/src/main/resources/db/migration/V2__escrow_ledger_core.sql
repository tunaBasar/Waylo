-- V2: escrow + ledger core (Task 7b). Money is NUMERIC(12,2) + CHAR(3) currency, never floats.
-- The old `payments` table (V1 mock) is kept read-only for history; no code path writes to it any more.

CREATE TABLE payment_intents (
    id                  UUID           PRIMARY KEY,
    order_id            UUID           NOT NULL UNIQUE,
    customer_id         UUID           NOT NULL,
    amount              NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    currency            CHAR(3)        NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status              VARCHAR(30)    NOT NULL,
    provider            VARCHAR(20)    NOT NULL,
    provider_payment_id VARCHAR(255),
    idempotency_key     VARCHAR(200)   NOT NULL UNIQUE,
    failure_code        VARCHAR(100),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_intent_status CHECK (status IN
        ('REQUIRES_PAYMENT','AUTHORIZED','PARTIALLY_RELEASED','RELEASED','REFUNDED',
         'PARTIALLY_REFUNDED','FAILED','CANCELLED'))
);
CREATE INDEX idx_payment_intents_customer ON payment_intents (customer_id);
CREATE INDEX idx_payment_intents_status ON payment_intents (status);

CREATE TABLE escrow_items (
    id                       UUID           PRIMARY KEY,
    payment_intent_id        UUID           NOT NULL REFERENCES payment_intents (id),
    order_id                 UUID           NOT NULL,
    order_item_id            UUID           NOT NULL UNIQUE,
    vertical                 VARCHAR(20)    NOT NULL,
    supplier_organization_id UUID           NOT NULL,
    gross_amount             NUMERIC(12,2)  NOT NULL CHECK (gross_amount > 0),
    commission_rate          NUMERIC(5,4)   NOT NULL CHECK (commission_rate >= 0 AND commission_rate <= 1),
    commission_amount        NUMERIC(12,2)  NOT NULL CHECK (commission_amount >= 0),
    supplier_amount          NUMERIC(12,2)  NOT NULL CHECK (supplier_amount >= 0),
    currency                 CHAR(3)        NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status                   VARCHAR(20)    NOT NULL,
    release_trigger          VARCHAR(20)    NOT NULL,
    provider_item_ref        VARCHAR(255),
    held_at                  TIMESTAMPTZ    NOT NULL,
    released_at              TIMESTAMPTZ,
    refunded_at              TIMESTAMPTZ,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_escrow_status CHECK (status IN ('HELD','RELEASED','REFUNDED')),
    CONSTRAINT chk_escrow_trigger CHECK (release_trigger IN ('REDEMPTION','TICKETING')),
    CONSTRAINT chk_escrow_vertical CHECK (vertical IN ('HOTEL','CAR','FLIGHT','BUS')),
    CONSTRAINT chk_escrow_split CHECK (commission_amount + supplier_amount = gross_amount)
);
CREATE INDEX idx_escrow_items_intent ON escrow_items (payment_intent_id);
CREATE INDEX idx_escrow_items_order ON escrow_items (order_id);
CREATE INDEX idx_escrow_items_supplier ON escrow_items (supplier_organization_id);

-- Admin managed. Rows are versions (never updated): the effective rate is the latest valid_from <= now.
-- supplier_organization_id NULL = vertical default, non-NULL = partner override. No default rows are seeded.
CREATE TABLE commission_rates (
    id                       UUID          PRIMARY KEY,
    vertical                 VARCHAR(20)   NOT NULL CHECK (vertical IN ('HOTEL','CAR','FLIGHT','BUS')),
    supplier_organization_id UUID,
    rate                     NUMERIC(5,4)  NOT NULL CHECK (rate >= 0 AND rate <= 1),
    valid_from               TIMESTAMPTZ   NOT NULL,
    created_by               UUID,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_commission_rates_lookup ON commission_rates (vertical, supplier_organization_id, valid_from);

CREATE TABLE supplier_payout_accounts (
    id                  UUID          PRIMARY KEY,
    organization_id     UUID          NOT NULL UNIQUE,
    provider            VARCHAR(20)   NOT NULL,
    sub_merchant_key    VARCHAR(255)  NOT NULL,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING','ACTIVE','SUSPENDED')),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Append-only double-entry ledger. One row per leg; every movement is written as a balanced pair
-- (same group_id, one debit leg + one credit leg of equal amount), so SUM(debit - credit) per order is 0.
CREATE TABLE ledger_entries (
    entry_id        UUID           PRIMARY KEY,
    group_id        UUID           NOT NULL,
    occurred_at     TIMESTAMPTZ    NOT NULL,
    order_id        UUID           NOT NULL,
    order_item_id   UUID,
    account         VARCHAR(30)    NOT NULL,
    debit_amount    NUMERIC(12,2)  NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(12,2)  NOT NULL DEFAULT 0,
    currency        CHAR(3)        NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    correlation_id  UUID           NOT NULL,
    description     VARCHAR(200),
    CONSTRAINT chk_ledger_account CHECK (account IN
        ('CUSTOMER_PAYMENT','CUSTOMER_FUNDS_HELD','PLATFORM_COMMISSION','SUPPLIER_PAYABLE',
         'SUPPLIER_PAID','CUSTOMER_REFUND','PROVIDER_FEE')),
    CONSTRAINT chk_ledger_one_sided CHECK (debit_amount >= 0 AND credit_amount >= 0
        AND (debit_amount = 0 OR credit_amount = 0) AND (debit_amount + credit_amount) > 0)
);
CREATE INDEX idx_ledger_order ON ledger_entries (order_id, occurred_at);
CREATE INDEX idx_ledger_group ON ledger_entries (group_id);

CREATE FUNCTION ledger_entries_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_no_update_delete
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_append_only();

CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_entries_append_only();
