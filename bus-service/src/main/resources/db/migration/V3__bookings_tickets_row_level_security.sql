-- Partner-mode row level security for bookings and tickets (same model as package-booking V12, builds on V2: waylo_app
-- role, grants, app_mode() and app_org_ids() already exist). Only PARTNER is restricted, to its authorized
-- organizations. Customer (public), service (HoldExpiryScheduler) and admin keep the behaviour they had before.
-- bookings carries organization_id, tickets only booking_id (and trip_id), so tickets follow their booking.

ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings FORCE ROW LEVEL SECURITY;
CREATE POLICY bookings_tenant ON bookings
    USING (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()))
    WITH CHECK (app_mode() <> 'partner' OR organization_id = ANY (app_org_ids()));

-- The subquery on bookings is itself filtered by bookings_tenant, so a partner only sees tickets of its own bookings.
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets FORCE ROW LEVEL SECURITY;
CREATE POLICY tickets_tenant ON tickets
    USING (app_mode() <> 'partner' OR EXISTS (
        SELECT 1 FROM bookings b WHERE b.id = tickets.booking_id AND b.organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() <> 'partner' OR EXISTS (
        SELECT 1 FROM bookings b WHERE b.id = tickets.booking_id AND b.organization_id = ANY (app_org_ids())));
