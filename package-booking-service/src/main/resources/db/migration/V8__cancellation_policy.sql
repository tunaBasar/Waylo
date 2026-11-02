-- Cancellation policy snapshot per item (CANCELLATION-CONTRACT.md). Existing rows keep today's behavior: full refund.
ALTER TABLE order_items ADD COLUMN cancellation_policy VARCHAR(20) NOT NULL DEFAULT 'FREE_CANCELLATION';
ALTER TABLE order_items ADD COLUMN cancel_free_hours_before INT;
ALTER TABLE order_items ADD COLUMN cancel_outcome VARCHAR(20);
ALTER TABLE order_items ADD COLUMN refund_amount NUMERIC(12,2);
ALTER TABLE order_items ADD CONSTRAINT chk_item_cancel_policy CHECK (cancellation_policy IN ('FREE_CANCELLATION','FREE_UNTIL','NON_REFUNDABLE')
    AND (cancellation_policy <> 'FREE_UNTIL' OR cancel_free_hours_before >= 0));
ALTER TABLE order_items ADD CONSTRAINT chk_item_cancel_outcome CHECK (cancel_outcome IS NULL OR cancel_outcome IN ('REFUND','NO_REFUND','NO_SHOW'));
ALTER TABLE vouchers DROP CONSTRAINT vouchers_status_check;
ALTER TABLE vouchers ADD CONSTRAINT vouchers_status_check CHECK (status IN ('ACTIVE','REDEEMED','VOID','NO_SHOW'));
