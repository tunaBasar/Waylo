-- V4: stay dates on order items (hotel) + per-item redemption vouchers.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS check_in DATE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS check_out DATE;

CREATE TABLE IF NOT EXISTS vouchers (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL UNIQUE REFERENCES order_items (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REDEEMED', 'VOID')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    redeemed_at TIMESTAMPTZ,
    redeemed_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
