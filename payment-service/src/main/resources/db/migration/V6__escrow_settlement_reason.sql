-- Cancellation policy task (CANCELLATION-CONTRACT.md): why an escrow item left escrow.
ALTER TABLE escrow_items ADD COLUMN settlement_reason VARCHAR(30);
UPDATE escrow_items SET settlement_reason = release_trigger WHERE status = 'RELEASED';
UPDATE escrow_items SET settlement_reason = 'CANCEL_REFUND' WHERE status = 'REFUNDED';
ALTER TABLE escrow_items ADD CONSTRAINT chk_escrow_settlement_reason CHECK (settlement_reason IS NULL OR settlement_reason IN
    ('REDEMPTION','TICKETING','CANCEL_NO_REFUND','NO_SHOW','CANCEL_REFUND'));
