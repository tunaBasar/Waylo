-- Online check-in against Order FLIGHT items (CHECKIN-CONTRACT.md). Legacy columns stay, now nullable.
ALTER TABLE checkins ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE checkins ALTER COLUMN flight_id DROP NOT NULL;
ALTER TABLE checkins ADD COLUMN order_item_id   UUID;
ALTER TABLE checkins ADD COLUMN order_id        UUID;
ALTER TABLE checkins ADD COLUMN pnr             VARCHAR(8);
ALTER TABLE checkins ADD COLUMN passenger_name  VARCHAR(170);
ALTER TABLE checkins ADD COLUMN flight_number   VARCHAR(10);
ALTER TABLE checkins ADD COLUMN departure_at    TIMESTAMP;
CREATE UNIQUE INDEX idx_checkins_order_item ON checkins (order_item_id);
CREATE INDEX idx_checkins_order_id ON checkins (order_id);
