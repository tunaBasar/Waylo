-- V3: unique guard on commission_rates (vertical, supplier_organization_id, valid_from).
-- Only EXACT duplicates (same vertical, organization, valid_from AND rate) are removed, keeping one row.
-- Rows that share the key but have different rates are a real conflict: they are NOT deleted, so the
-- unique index below fails and the migration stops loudly instead of silently picking a rate.
-- The ledger table is not touched.

DELETE FROM commission_rates cr
USING commission_rates keep
WHERE cr.vertical = keep.vertical
  AND COALESCE(cr.supplier_organization_id, '00000000-0000-0000-0000-000000000000'::uuid)
      = COALESCE(keep.supplier_organization_id, '00000000-0000-0000-0000-000000000000'::uuid)
  AND cr.valid_from = keep.valid_from
  AND cr.rate = keep.rate
  AND cr.id::text > keep.id::text;

CREATE UNIQUE INDEX uk_commission_rates_vertical_org_validfrom
    ON commission_rates (vertical, COALESCE(supplier_organization_id, '00000000-0000-0000-0000-000000000000'::uuid), valid_from);
