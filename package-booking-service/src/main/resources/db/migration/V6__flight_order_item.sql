-- flight order items (internal flight supplier, see FLIGHT-SUPPLIER-CONTRACT.md)
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS flight_number VARCHAR(10);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS departure_at TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS seat_number VARCHAR(5);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS passenger_first_name VARCHAR(80);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS passenger_last_name VARCHAR(80);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS pnr VARCHAR(8);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS ticket_number VARCHAR(20);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier VARCHAR(20);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_order_ref VARCHAR(100);
