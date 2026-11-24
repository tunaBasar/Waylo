-- RM-14: PostgreSQL row level security for partner-owned car rental data (same model as hotel-service V9).
-- Every application transaction runs `SET LOCAL ROLE waylo_app` (NOLOGIN, non-superuser, no BYPASSRLS, see
-- TenantAwareJpaDialect) with transaction-local app.mode / app.org_ids. Flyway keeps running as the owner.
-- waylo_app is cluster wide and may already exist (hotel-service V9), so everything here is idempotent.
--
-- Tenant-owned (RLS, own organization_id): branches, vehicles, category_rates, one_way_routes, documents.
-- Not covered: rentals (transactional: customers/holds/schedulers, partner reads are filtered in code),
-- processed_events (technical).
-- Modes: service/admin unrestricted, public reads the catalog but never writes, partner only its organizations.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'waylo_app') THEN
        CREATE ROLE waylo_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT;
    END IF;
EXCEPTION WHEN duplicate_object THEN
    NULL; -- another service migrated concurrently: roles are cluster wide
END
$$;

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


-- Tables carrying their own organization_id: public/service/admin read as before (public projection is
-- filtered in code), partner only its authorized organizations, writes only service/admin/own-org partner.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['branches', 'vehicles', 'category_rates', 'one_way_routes', 'documents']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR SELECT USING (app_mode() <> ''partner'' OR organization_id = ANY (app_org_ids()))', t || '_read', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR INSERT WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND organization_id = ANY (app_org_ids())))', t || '_insert', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR UPDATE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND organization_id = ANY (app_org_ids()))) WITH CHECK (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND organization_id = ANY (app_org_ids())))', t || '_update', t);
        EXECUTE format('CREATE POLICY %I ON %I FOR DELETE USING (app_mode() IN (''service'', ''admin'') OR (app_mode() = ''partner'' AND organization_id = ANY (app_org_ids())))', t || '_delete', t);
    END LOOP;
END
$$;
