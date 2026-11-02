-- pricing-service commercial layer (PRICING-CONTRACT.md section 6). Old rows: gross = net = unit_amount * quantity, no discount.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS gross_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS discount_funded_by VARCHAR(20);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS net_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS promo_code VARCHAR(50);
UPDATE order_items SET gross_amount = unit_amount * quantity, net_amount = unit_amount * quantity WHERE gross_amount IS NULL;
