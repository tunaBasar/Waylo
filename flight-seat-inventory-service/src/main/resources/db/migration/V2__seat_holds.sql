-- seat holds: optimistic locking + owner + expiry
ALTER TABLE flight_seats ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE flight_seats ADD COLUMN reserved_by UUID NULL;
ALTER TABLE flight_seats ADD COLUMN hold_expires_at TIMESTAMP NULL;

CREATE INDEX idx_flight_seats_hold_expires ON flight_seats (hold_expires_at);
