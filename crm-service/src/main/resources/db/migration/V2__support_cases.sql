-- V2: RM-36 owned-order support cases. No live chat: a case is a thread of asynchronous messages tied to the customer's own order.
-- customer_id / order_id / supplier_organization_id are cross-service references (no FK), verified against package-booking at open time.

CREATE TABLE support_cases (
    id                       UUID         PRIMARY KEY,
    customer_id              UUID         NOT NULL,
    order_id                 UUID         NOT NULL,
    order_item_id            UUID,
    supplier_organization_id UUID,
    subject                  VARCHAR(150) NOT NULL,
    status                   VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_support_case_status CHECK (status IN ('OPEN','ANSWERED','CLOSED'))
);
CREATE INDEX idx_support_cases_customer ON support_cases (customer_id, created_at DESC);
CREATE INDEX idx_support_cases_supplier ON support_cases (supplier_organization_id, created_at DESC);
CREATE INDEX idx_support_cases_status ON support_cases (status, created_at DESC);

CREATE TABLE support_messages (
    id             UUID          PRIMARY KEY,
    case_id        UUID          NOT NULL REFERENCES support_cases (id),
    author_role    VARCHAR(10)   NOT NULL,
    author_user_id UUID          NOT NULL,
    body           VARCHAR(4000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_support_message_role CHECK (author_role IN ('CUSTOMER','PARTNER','ADMIN'))
);
CREATE INDEX idx_support_messages_case ON support_messages (case_id, created_at);

-- Messages are append-only: UPDATE, DELETE, TRUNCATE rejected (same pattern as payment-service payout_append_only()).
CREATE FUNCTION support_messages_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_support_messages_row BEFORE UPDATE OR DELETE ON support_messages FOR EACH ROW EXECUTE FUNCTION support_messages_append_only();
CREATE TRIGGER trg_support_messages_trunc BEFORE TRUNCATE ON support_messages FOR EACH STATEMENT EXECUTE FUNCTION support_messages_append_only();
