-- Pricing-service discounts (PRICING-CONTRACT.md sections 2 and 7).
-- escrow_items.gross_amount stays the supplier price (commission base). charged_amount is what the customer paid (net).
ALTER TABLE escrow_items ADD COLUMN charged_amount NUMERIC(12,2);
ALTER TABLE escrow_items ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE escrow_items ADD COLUMN discount_funded_by VARCHAR(20);
UPDATE escrow_items SET charged_amount = gross_amount;
ALTER TABLE escrow_items ALTER COLUMN charged_amount SET NOT NULL;
ALTER TABLE escrow_items ADD CONSTRAINT chk_escrow_charged CHECK (charged_amount > 0 AND discount_amount >= 0
    AND charged_amount = gross_amount - discount_amount);
ALTER TABLE escrow_items ADD CONSTRAINT chk_escrow_funded_by CHECK (discount_funded_by IS NULL OR discount_funded_by IN ('PLATFORM','PARTNER'));
ALTER TABLE escrow_items DROP CONSTRAINT chk_escrow_split;
-- PLATFORM: supplier + commission = gross. PARTNER: supplier + commission = net (partner bears the discount).
ALTER TABLE escrow_items ADD CONSTRAINT chk_escrow_split CHECK (
    commission_amount + supplier_amount = CASE WHEN discount_funded_by = 'PARTNER' THEN charged_amount ELSE gross_amount END);

ALTER TABLE ledger_entries DROP CONSTRAINT chk_ledger_account;
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_account CHECK (account IN
    ('CUSTOMER_PAYMENT','CUSTOMER_FUNDS_HELD','PLATFORM_COMMISSION','SUPPLIER_PAYABLE',
     'SUPPLIER_PAID','CUSTOMER_REFUND','PROVIDER_FEE','PLATFORM_DISCOUNT_COST'));
