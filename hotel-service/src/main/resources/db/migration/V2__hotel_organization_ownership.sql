-- Existing rows may exist: column is nullable now; NOT NULL comes after a reviewed backfill.
ALTER TABLE hotels ADD COLUMN organization_id UUID;
CREATE INDEX idx_hotels_organization_id ON hotels (organization_id);
