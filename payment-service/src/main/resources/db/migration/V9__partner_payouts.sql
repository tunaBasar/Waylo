-- Partner payouts (PAYOUT-CONTRACT.md). Money NUMERIC(12,2) + CHAR(3). Balance is derived, never stored.

CREATE TABLE payout_wallets (            -- lock rows only
    organization_id UUID    NOT NULL,
    currency        CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, currency)
);

CREATE TABLE partner_bank_accounts (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL UNIQUE,
    account_holder_name VARCHAR(140) NOT NULL,
    iban_ciphertext     TEXT NOT NULL,
    iban_last4          CHAR(4) NOT NULL,
    iban_hash           CHAR(64) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    verified_by         UUID,
    verified_at         TIMESTAMPTZ,
    reject_reason       VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          UUID NOT NULL,
    updated_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bank_status CHECK (status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','DISABLED')),
    CONSTRAINT chk_bank_verified CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL AND verified_by IS NOT NULL))
);
CREATE UNIQUE INDEX uq_bank_iban_hash_live ON partner_bank_accounts (iban_hash) WHERE status <> 'DISABLED';

CREATE TABLE partner_bank_account_changes (
    id               UUID PRIMARY KEY,
    bank_account_id  UUID NOT NULL REFERENCES partner_bank_accounts (id),
    organization_id  UUID NOT NULL,
    change_type      VARCHAR(16) NOT NULL CHECK (change_type IN ('CREATED','REPLACED','DISABLED','VERIFIED','REJECTED','REVEALED')),
    old_last4        CHAR(4),
    new_last4        CHAR(4),
    old_iban_hash    CHAR(64),
    new_iban_hash    CHAR(64),
    actor_user_id    UUID NOT NULL,
    actor_type       VARCHAR(8) NOT NULL CHECK (actor_type IN ('PARTNER','ADMIN')),
    reason           VARCHAR(500),
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bank_changes_org ON partner_bank_account_changes (organization_id, occurred_at DESC);

CREATE TABLE partner_payouts (
    id                    UUID PRIMARY KEY,
    organization_id       UUID NOT NULL,
    currency              CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    amount                NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status                VARCHAR(16) NOT NULL,
    idempotency_key       VARCHAR(150) NOT NULL,
    request_hash          CHAR(64) NOT NULL,
    bank_account_id       UUID NOT NULL REFERENCES partner_bank_accounts (id),
    bank_account_version  BIGINT NOT NULL,
    iban_ciphertext       TEXT NOT NULL,           -- snapshot at request time
    iban_last4            CHAR(4) NOT NULL,
    account_holder_name   VARCHAR(140) NOT NULL,
    requested_by          UUID NOT NULL,
    requested_by_email    VARCHAR(255),            -- gateway X-User-Email at request time, for the notification
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    processing_at         TIMESTAMPTZ,
    paid_by               UUID,
    paid_at               TIMESTAMPTZ,
    payment_reference     VARCHAR(100),
    failure_reason        VARCHAR(500),
    cancelled_by          UUID,
    cancelled_by_type     VARCHAR(8),
    cancelled_at          TIMESTAMPTZ,
    cancel_reason         VARCHAR(500),
    version               BIGINT NOT NULL DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payout_status CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED','CANCELLED')),
    CONSTRAINT chk_payout_paid CHECK (status <> 'PAID' OR (payment_reference IS NOT NULL AND paid_at IS NOT NULL AND paid_by IS NOT NULL)),
    CONSTRAINT chk_payout_failed CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT chk_payout_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by_type IN ('PARTNER','ADMIN'))),
    CONSTRAINT uq_payout_idem UNIQUE (organization_id, idempotency_key)
);
CREATE UNIQUE INDEX uq_payout_open ON partner_payouts (organization_id, currency)
    WHERE status IN ('REQUESTED','APPROVED','PROCESSING');
CREATE UNIQUE INDEX uq_payout_reference ON partner_payouts (payment_reference) WHERE payment_reference IS NOT NULL;
CREATE INDEX idx_payout_org ON partner_payouts (organization_id, requested_at DESC);
CREATE INDEX idx_payout_queue ON partner_payouts (status, requested_at);

CREATE TABLE payout_events (
    id           UUID PRIMARY KEY,
    payout_id    UUID NOT NULL REFERENCES partner_payouts (id),
    from_status  VARCHAR(16),
    to_status    VARCHAR(16) NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_type   VARCHAR(8) NOT NULL CHECK (actor_type IN ('PARTNER','ADMIN','SYSTEM')),
    reason       VARCHAR(500),
    reference    VARCHAR(100),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payout_events_payout ON payout_events (payout_id, occurred_at);

-- Balance movements and payout accounting legs. payable_delta is the ONLY input to the payable formula (sec. 2).
CREATE TABLE payout_ledger (
    id               UUID PRIMARY KEY,
    group_id         UUID NOT NULL,
    organization_id  UUID NOT NULL,
    payout_id        UUID REFERENCES partner_payouts (id),
    entry_type       VARCHAR(20) NOT NULL,
    payable_delta    NUMERIC(12,2) NOT NULL,
    account          VARCHAR(30),
    debit_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    credit_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency         CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    order_item_id    UUID,
    idempotency_key  VARCHAR(150),
    reference        VARCHAR(500),
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payout_ledger_type CHECK (entry_type IN ('RESERVE','RESERVE_RELEASE','PAID','ADJUSTMENT_DEBIT','ADJUSTMENT_CREDIT')),
    CONSTRAINT chk_payout_ledger_shape CHECK (
        (entry_type = 'RESERVE'           AND payable_delta < 0 AND payout_id IS NOT NULL AND account IS NULL)
     OR (entry_type = 'RESERVE_RELEASE'   AND payable_delta > 0 AND payout_id IS NOT NULL AND account IS NULL)
     OR (entry_type = 'PAID'              AND payable_delta = 0 AND payout_id IS NOT NULL AND account IN ('SUPPLIER_PAYABLE','SUPPLIER_PAID'))
     OR (entry_type = 'ADJUSTMENT_DEBIT'  AND payable_delta < 0 AND payout_id IS NULL AND account IS NULL AND idempotency_key IS NOT NULL)
     OR (entry_type = 'ADJUSTMENT_CREDIT' AND payable_delta > 0 AND payout_id IS NULL AND account IS NULL AND idempotency_key IS NOT NULL)),
    CONSTRAINT chk_payout_ledger_sides CHECK (debit_amount >= 0 AND credit_amount >= 0 AND (debit_amount = 0 OR credit_amount = 0)
        AND ((entry_type = 'PAID') = (debit_amount + credit_amount > 0)))
);
CREATE UNIQUE INDEX uq_payout_ledger_once ON payout_ledger (payout_id, entry_type, account) NULLS NOT DISTINCT
    WHERE payout_id IS NOT NULL;
CREATE UNIQUE INDEX uq_payout_ledger_adj_idem ON payout_ledger (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payout_ledger_org ON payout_ledger (organization_id, currency);

-- Append-only: UPDATE, DELETE, TRUNCATE rejected (same pattern as V2 and V4).
CREATE FUNCTION payout_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_payout_ledger_row BEFORE UPDATE OR DELETE ON payout_ledger FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_ledger_trunc BEFORE TRUNCATE ON payout_ledger FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_events_row BEFORE UPDATE OR DELETE ON payout_events FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_events_trunc BEFORE TRUNCATE ON payout_events FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_bank_changes_row BEFORE UPDATE OR DELETE ON partner_bank_account_changes FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_bank_changes_trunc BEFORE TRUNCATE ON partner_bank_account_changes FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
