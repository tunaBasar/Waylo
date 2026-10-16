-- V5: Daily rates calendar

CREATE TABLE IF NOT EXISTS daily_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id UUID NOT NULL,
    room_type_id UUID NOT NULL,
    rate_plan_id UUID NOT NULL,
    stay_date DATE NOT NULL,
    occupancy INT NOT NULL CHECK (occupancy BETWEEN 1 AND 30),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, room_type_id, rate_plan_id, stay_date, occupancy),
    CONSTRAINT fk_daily_rates_room_type
        FOREIGN KEY (hotel_id, room_type_id)
        REFERENCES room_types (hotel_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_rates_rate_plan
        FOREIGN KEY (hotel_id, rate_plan_id)
        REFERENCES rate_plans (hotel_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_daily_rates_hotel_date ON daily_rates (hotel_id, stay_date);
