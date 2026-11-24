-- V11: recorded hotel check-out (RM-29). Lives on the voucher: it is the 1:1 fulfillment record of an order item and
-- already carries redeemed_at / redeemed_by_user_id (check-in). version enables optimistic expectedVersion checks.
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS checked_out_at TIMESTAMPTZ;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS checked_out_by_user_id UUID;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vouchers ADD CONSTRAINT vouchers_checkout_requires_redeemed
    CHECK (checked_out_at IS NULL OR (status = 'REDEEMED' AND redeemed_at IS NOT NULL AND checked_out_at >= redeemed_at));
