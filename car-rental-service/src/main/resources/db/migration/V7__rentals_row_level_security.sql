-- Partner-mode row level security for rentals (same model as package-booking V12, builds on V6: waylo_app role,
-- grants, app_mode() and app_org_ids() already exist). Only PARTNER is restricted, to its authorized organizations.
-- Customer (public), service (HoldExpiryScheduler, Kafka) and admin keep the behaviour they had before.
-- rentals is the only transaction table with organization_id (holds and quotes are columns of rentals, not tables).
-- Not covered: processed_events (technical).

ALTER TABLE rentals ENABLE ROW LEVEL SECURITY;
ALTER TABLE rentals FORCE ROW LEVEL SECURITY;
CREATE POLICY rentals_tenant ON rentals
    USING (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()))
    WITH CHECK (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()));
