-- V13: hotel guest counts on the order item (F31). Nullable, no backfill. The lead guest name reuses
-- passenger_first_name / passenger_last_name (V6).
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS adults INT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS children_count INT;
