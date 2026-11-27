-- Weekly bus timetables (inventory automation contract section 3). A daily job and the partner API materialise
-- them into regular trips. RLS follows V2: schedules by organization_id, children via app_schedule_in_scope.

CREATE TABLE trip_schedules (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    bus_id          UUID        NOT NULL REFERENCES buses (id),
    code            VARCHAR(40) NOT NULL,
    currency        VARCHAR(3)  NOT NULL,
    weekdays        SMALLINT    NOT NULL CHECK (weekdays BETWEEN 1 AND 127),
    departure_time  TIME        NOT NULL,
    valid_from      DATE        NOT NULL,
    valid_to        DATE,
    horizon_days    INT         NOT NULL DEFAULT 30 CHECK (horizon_days BETWEEN 1 AND 120),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, code),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_trip_schedules_org ON trip_schedules (organization_id);

CREATE TABLE trip_schedule_stops (
    id                       UUID PRIMARY KEY,
    schedule_id              UUID NOT NULL REFERENCES trip_schedules (id) ON DELETE CASCADE,
    seq                      INT  NOT NULL CHECK (seq >= 0),
    terminal_id              UUID NOT NULL REFERENCES terminals (id),
    arrival_offset_minutes   INT  NOT NULL CHECK (arrival_offset_minutes >= 0),
    departure_offset_minutes INT  NOT NULL,
    UNIQUE (schedule_id, seq),
    CHECK (departure_offset_minutes >= arrival_offset_minutes)
);

CREATE TABLE trip_schedule_fares (
    id          UUID PRIMARY KEY,
    schedule_id UUID          NOT NULL REFERENCES trip_schedules (id) ON DELETE CASCADE,
    from_seq    INT           NOT NULL,
    to_seq      INT           NOT NULL,
    price       NUMERIC(12,2) NOT NULL CHECK (price > 0),
    UNIQUE (schedule_id, from_seq, to_seq),
    CHECK (to_seq > from_seq)
);

ALTER TABLE trips ADD COLUMN schedule_id UUID REFERENCES trip_schedules (id) ON DELETE SET NULL;
CREATE INDEX idx_trips_schedule ON trips (schedule_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON trip_schedules, trip_schedule_stops, trip_schedule_fares TO waylo_app;

ALTER TABLE trip_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY trip_schedules_read ON trip_schedules FOR SELECT
    USING (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()));
CREATE POLICY trip_schedules_insert ON trip_schedules FOR INSERT
    WITH CHECK (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY trip_schedules_update ON trip_schedules FOR UPDATE
    USING (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY trip_schedules_delete ON trip_schedules FOR DELETE
    USING (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));

CREATE OR REPLACE FUNCTION app_schedule_in_scope(p_schedule_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT EXISTS (SELECT 1 FROM trip_schedules s WHERE s.id = p_schedule_id AND s.organization_id = ANY (app_org_ids())) $$;

REVOKE ALL ON FUNCTION app_schedule_in_scope(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_schedule_in_scope(uuid) TO waylo_app;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['trip_schedule_stops', 'trip_schedule_fares']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR SELECT USING (app_mode() <> ''partner'' OR app_schedule_in_scope(schedule_id))', t || '_read', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR INSERT WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_schedule_in_scope(schedule_id)))', t || '_insert', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR UPDATE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_schedule_in_scope(schedule_id))) WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_schedule_in_scope(schedule_id)))', t || '_update', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR DELETE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND app_schedule_in_scope(schedule_id)))', t || '_delete', t);
    END LOOP;
END
$$;
