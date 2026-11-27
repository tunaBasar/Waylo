-- V14: rolling inventory and rate rules (inventory automation contract, section 2)

ALTER TABLE daily_inventory
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT chk_daily_inventory_source CHECK (source IN ('MANUAL','RULE'));

ALTER TABLE daily_rates
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT chk_daily_rates_source CHECK (source IN ('MANUAL','RULE'));

CREATE TABLE hotel_inventory_rules (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id          UUID NOT NULL,
    room_type_id      UUID NOT NULL,
    default_allotment INT NOT NULL CHECK (default_allotment BETWEEN 0 AND 10000),
    horizon_days      INT NOT NULL DEFAULT 365 CHECK (horizon_days BETWEEN 30 AND 730),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, room_type_id),
    CONSTRAINT fk_hir_room_type FOREIGN KEY (hotel_id, room_type_id)
        REFERENCES room_types (hotel_id, id) ON DELETE CASCADE
);

CREATE TABLE hotel_rate_rules (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id              UUID NOT NULL,
    room_type_id          UUID NOT NULL,
    rate_plan_id          UUID NOT NULL,
    name                  VARCHAR(80) NOT NULL,
    valid_from            DATE,
    valid_to              DATE,
    currency              CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    base_occupancy        INT NOT NULL CHECK (base_occupancy BETWEEN 1 AND 30),
    base_amount           NUMERIC(12,2) NOT NULL CHECK (base_amount > 0),
    weekend_base_amount   NUMERIC(12,2) CHECK (weekend_base_amount > 0),
    extra_person_amount   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (extra_person_amount >= 0),
    reduced_person_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (reduced_person_amount >= 0),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hrr_season_pair CHECK ((valid_from IS NULL) = (valid_to IS NULL)),
    CONSTRAINT chk_hrr_season_order CHECK (valid_to >= valid_from),
    CONSTRAINT fk_hrr_room_type FOREIGN KEY (hotel_id, room_type_id)
        REFERENCES room_types (hotel_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_hrr_rate_plan FOREIGN KEY (hotel_id, rate_plan_id)
        REFERENCES rate_plans (hotel_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_hotel_rate_rules_hotel ON hotel_rate_rules (hotel_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_inventory_rules, hotel_rate_rules TO waylo_app;

-- RLS: V9 child-table pattern
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['hotel_inventory_rules', 'hotel_rate_rules']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR SELECT USING (app_mode() <> ''partner'' OR app_hotel_in_scope(hotel_id))', t || '_read', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR INSERT WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_hotel_in_scope(hotel_id)))', t || '_insert', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR UPDATE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_hotel_in_scope(hotel_id))) WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_hotel_in_scope(hotel_id)))', t || '_update', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR DELETE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_hotel_in_scope(hotel_id)))', t || '_delete', t);
    END LOOP;
END
$$;
