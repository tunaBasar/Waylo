-- Daily inventory table (partner stock calendar)
CREATE TABLE daily_inventory (
    id                UUID PRIMARY KEY,
    hotel_id          UUID NOT NULL,
    room_type_id      UUID NOT NULL,
    stay_date         DATE NOT NULL,
    allotment         INT NOT NULL CHECK (allotment BETWEEN 0 AND 10000),
    out_of_service    INT NOT NULL DEFAULT 0 CHECK (out_of_service >= 0),
    held              INT NOT NULL DEFAULT 0 CHECK (held >= 0),
    confirmed         INT NOT NULL DEFAULT 0 CHECK (confirmed >= 0),
    stop_sell         BOOLEAN NOT NULL DEFAULT FALSE,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_daily_inventory_available CHECK (allotment - out_of_service - held - confirmed >= 0),
    UNIQUE (hotel_id, room_type_id, stay_date),
    FOREIGN KEY (hotel_id, room_type_id) REFERENCES room_types (hotel_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_daily_inventory_hotel_date ON daily_inventory (hotel_id, stay_date);

-- Append-only audit log for inventory adjustments
CREATE TABLE inventory_adjustments (
    id                    UUID PRIMARY KEY,
    hotel_id              UUID NOT NULL,
    room_type_id          UUID NOT NULL,
    stay_date             DATE NOT NULL,
    actor_user_id         UUID NOT NULL,
    reason                VARCHAR(500),
    old_allotment         INT,
    new_allotment         INT NOT NULL,
    old_out_of_service    INT,
    new_out_of_service    INT NOT NULL,
    old_stop_sell         BOOLEAN,
    new_stop_sell         BOOLEAN NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_adjustments_hotel_room_date ON inventory_adjustments (hotel_id, room_type_id, stay_date);
