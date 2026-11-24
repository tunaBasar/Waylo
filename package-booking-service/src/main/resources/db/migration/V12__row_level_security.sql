-- PostgreSQL row level security for partner reads of order items and vouchers (same model as hotel-service V9,
-- car-rental V6, bus). Every application transaction runs `SET LOCAL ROLE waylo_app` (NOLOGIN, non-superuser, no
-- BYPASSRLS, see TenantAwareJpaDialect) with transaction-local app.mode / app.org_ids. Flyway keeps running as the owner.
-- waylo_app is cluster wide and may already exist (hotel-service V9), so everything here is idempotent.
--
-- Tenant-owned (RLS): order_items (supplier_organization_id) and vouchers (through order_item_id).
-- Not covered: orders (customer reads and every partner path that needs an order go through order_items or the
-- internal APIs, partner queries never read orders directly), outbox_event, processed_events, idempotency and the
-- legacy package_bookings table.
-- Modes: only PARTNER is restricted, to its authorized organizations. Customer (public), service and admin keep the
-- behaviour they had before RLS (customer ownership is checked in code on orders.customer_id).

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

ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items FORCE ROW LEVEL SECURITY;
CREATE POLICY order_items_tenant ON order_items
    USING (app_mode() <> 'partner' OR supplier_organization_id = ANY (app_org_ids()))
    WITH CHECK (app_mode() <> 'partner' OR supplier_organization_id = ANY (app_org_ids()));

-- The subquery on order_items is itself filtered by order_items_tenant, so a partner only sees vouchers of its own items.
ALTER TABLE vouchers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vouchers FORCE ROW LEVEL SECURITY;
CREATE POLICY vouchers_tenant ON vouchers
    USING (app_mode() <> 'partner' OR EXISTS (
        SELECT 1 FROM order_items oi WHERE oi.id = vouchers.order_item_id AND oi.supplier_organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() <> 'partner' OR EXISTS (
        SELECT 1 FROM order_items oi WHERE oi.id = vouchers.order_item_id AND oi.supplier_organization_id = ANY (app_org_ids())));
