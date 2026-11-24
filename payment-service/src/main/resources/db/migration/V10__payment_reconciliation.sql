-- V10: RM-27 payment/order reconciliation log. One row per repair or manual-review decision of PaymentReconciliationJob.
-- Append-only (UPDATE, DELETE, TRUNCATE rejected) with the payout_append_only() function created in V9.

CREATE TABLE payment_reconciliations (
    id                  UUID PRIMARY KEY,
    payment_intent_id   UUID NOT NULL,
    order_id            UUID NOT NULL,
    provider_payment_id VARCHAR(255),
    intent_status       VARCHAR(30) NOT NULL,
    order_status        VARCHAR(20),
    status              VARCHAR(24) NOT NULL,
    detail              VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reconciliation_status CHECK (status IN ('ESCROW_REPAIRED','PROVIDER_CANCELLED','MANUAL_REVIEW'))
);
CREATE INDEX idx_reconciliation_status ON payment_reconciliations (status, created_at DESC);
CREATE INDEX idx_reconciliation_intent ON payment_reconciliations (payment_intent_id);

CREATE TRIGGER trg_reconciliation_row BEFORE UPDATE OR DELETE ON payment_reconciliations FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_reconciliation_trunc BEFORE TRUNCATE ON payment_reconciliations FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
