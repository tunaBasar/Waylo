CREATE TABLE rentals (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    customer_id UUID NOT NULL,
    pickup_date DATE NOT NULL,
    return_date DATE NOT NULL,
    daily_rate NUMERIC(10,2) NOT NULL,
    total_price NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CHECK (return_date > pickup_date)
);
CREATE INDEX idx_rentals_vehicle_dates ON rentals (vehicle_id, pickup_date, return_date);
CREATE INDEX idx_rentals_customer ON rentals (customer_id);
