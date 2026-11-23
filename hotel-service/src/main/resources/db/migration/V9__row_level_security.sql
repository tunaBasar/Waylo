-- RM-14: PostgreSQL row level security as defense in depth for partner-owned catalog data.
--
-- The login role (waylo) is a superuser and owns every table, so RLS would never apply to it.
-- Every application transaction therefore runs `SET LOCAL ROLE waylo_app` (see TenantAwareJpaDialect):
-- a NOLOGIN, non-superuser, non-BYPASSRLS role. SET LOCAL / set_config(..., true) are transaction-scoped,
-- so nothing survives COMMIT/ROLLBACK on a pooled connection. Flyway keeps running as the owner.
--
-- Context (transaction-local GUCs, set only by the application after its access checks):
--   app.mode    = public | partner | service | admin   (missing/empty => public)
--   app.org_ids = comma separated organization UUIDs (partner mode only)
--
-- Policy per table:
--   SELECT : public/service/admin unrestricted (public projection is filtered in code, as today),
--            partner sees only rows whose hotel belongs to app.org_ids.
--   writes : service/admin unrestricted, partner only for its own organizations' hotels,
--            public denied (catalog). Exception: hotel_reviews INSERT is allowed in public mode
--            (customer submits a review; eligibility is enforced in code).
-- Legacy hotels with organization_id NULL: readable as before in public/service/admin; no partner can
-- see or write them (the code already locks them), service/admin keep full access.
-- Not covered (user-owned / platform-global / transactional, documented in the report):
--   hotel_quotes, hotel_quote_nights, hotel_holds, hotel_review_moderation_events, processed_events.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'waylo_app') THEN
        CREATE ROLE waylo_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT;
    END IF;
END
$$;

-- Lets a non-superuser login role switch too (superusers may always SET ROLE).
DO $$
BEGIN
    EXECUTE format('GRANT waylo_app TO %I', current_user);
END
$$;

GRANT USAGE ON SCHEMA public TO waylo_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO waylo_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO waylo_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO waylo_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO waylo_app;

CREATE OR REPLACE FUNCTION app_mode() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(nullif(current_setting('app.mode', true), ''), 'public') $$;

CREATE OR REPLACE FUNCTION app_org_ids() RETURNS uuid[]
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(string_to_array(nullif(current_setting('app.org_ids', true), ''), ',')::uuid[], '{}'::uuid[]) $$;

-- SECURITY DEFINER (owner) so child-table policies can look up the hotel's owner without
-- recursing into the hotels policy.
CREATE OR REPLACE FUNCTION app_hotel_in_scope(p_hotel_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT EXISTS (SELECT 1 FROM hotels h WHERE h.id = p_hotel_id AND h.organization_id = ANY (app_org_ids())) $$;

REVOKE ALL ON FUNCTION app_hotel_in_scope(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_mode(), app_org_ids(), app_hotel_in_scope(uuid) TO waylo_app;

-- hotels: scoped by its own organization_id
ALTER TABLE hotels ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotels FORCE ROW LEVEL SECURITY;
CREATE POLICY hotels_read ON hotels FOR SELECT
    USING (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()));
CREATE POLICY hotels_insert ON hotels FOR INSERT
    WITH CHECK (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hotels_update ON hotels FOR UPDATE
    USING (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hotels_delete ON hotels FOR DELETE
    USING (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));

-- Child catalog tables: scoped through hotel_id
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['rooms', 'room_types', 'rate_plans', 'daily_inventory', 'inventory_adjustments', 'daily_rates']
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

-- hotel_reviews: customers insert in public mode; partners may only update (respond to) their own hotels' reviews.
ALTER TABLE hotel_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY hotel_reviews_read ON hotel_reviews FOR SELECT
    USING (app_mode() <> 'partner' OR app_hotel_in_scope(hotel_id));
CREATE POLICY hotel_reviews_insert ON hotel_reviews FOR INSERT
    WITH CHECK (app_mode() IN ('public', 'service', 'admin'));
CREATE POLICY hotel_reviews_update ON hotel_reviews FOR UPDATE
    USING (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND app_hotel_in_scope(hotel_id)))
    WITH CHECK (app_mode() IN ('service', 'admin') OR (app_mode() = 'partner' AND app_hotel_in_scope(hotel_id)));
CREATE POLICY hotel_reviews_delete ON hotel_reviews FOR DELETE
    USING (app_mode() IN ('service', 'admin'));
