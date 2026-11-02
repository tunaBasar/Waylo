-- Prepaid balance we hold at an external flight supplier (FLIGHT-SUPPLIER-CONTRACT.md 3.1).
-- Balance only changes together with an append-only supplier_ledger row, never automatically topped up.
CREATE TABLE supplier_accounts (
    supplier    VARCHAR(30)   PRIMARY KEY,
    currency    CHAR(3)       NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    balance     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE supplier_ledger (
    id              UUID          PRIMARY KEY,
    supplier        VARCHAR(30)   NOT NULL REFERENCES supplier_accounts (supplier),
    entry_type      VARCHAR(20)   NOT NULL CHECK (entry_type IN ('TOPUP','TICKET_PAYOUT','REFUND_CREDIT','ADJUSTMENT')),
    amount          NUMERIC(12,2) NOT NULL CHECK (amount <> 0),
    balance_after   NUMERIC(12,2) NOT NULL CHECK (balance_after >= 0),
    currency        CHAR(3)       NOT NULL,
    order_id        UUID,
    order_item_id   UUID,
    reference       VARCHAR(200),
    idempotency_key VARCHAR(200)  NOT NULL UNIQUE,
    created_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_supplier_ledger_supplier ON supplier_ledger (supplier, created_at DESC);

CREATE FUNCTION supplier_ledger_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'supplier_ledger is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_supplier_ledger_no_update_delete
    BEFORE UPDATE OR DELETE ON supplier_ledger
    FOR EACH ROW EXECUTE FUNCTION supplier_ledger_append_only();
CREATE TRIGGER trg_supplier_ledger_no_truncate
    BEFORE TRUNCATE ON supplier_ledger
    FOR EACH STATEMENT EXECUTE FUNCTION supplier_ledger_append_only();
