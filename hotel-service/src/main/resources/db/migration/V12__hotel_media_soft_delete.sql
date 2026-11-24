-- Photo deletion keeps the review audit: hotel_media_decisions is append-only and references hotel_media with
-- ON DELETE RESTRICT, so a reviewed photo cannot be hard-deleted. A partner "delete" now sets deleted_at (the file is
-- removed from the volume), and every read ignores deleted rows. Hard DELETE stays possible only for never-reviewed rows.
ALTER TABLE hotel_media ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_hm_hotel_live ON hotel_media (hotel_id, sort_order) WHERE deleted_at IS NULL;

-- Public reads must never see a deleted photo, even through a query that forgets the filter.
DROP POLICY hm_read ON hotel_media;
CREATE POLICY hm_read ON hotel_media FOR SELECT
    USING (app_mode() IN ('service','admin')
           OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids()))
           OR (app_mode() = 'public' AND scan_status = 'CLEAN' AND review_status = 'APPROVED' AND deleted_at IS NULL));

-- The partner guard also covers the new column: a partner may set deleted_at once (soft delete of its own photo, which
-- the service only allows when the photo is not live) but never clear it again.
CREATE OR REPLACE FUNCTION hotel_media_partner_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF app_mode() = 'partner' THEN
        IF NEW.hotel_id <> OLD.hotel_id OR NEW.organization_id <> OLD.organization_id
           OR NEW.storage_key <> OLD.storage_key OR NEW.scan_status <> OLD.scan_status
           OR NEW.review_status NOT IN (OLD.review_status, 'PENDING')
           OR (OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS DISTINCT FROM OLD.deleted_at) THEN
            RAISE EXCEPTION 'partner may not change review or storage fields of hotel_media';
        END IF;
        -- a live photo (APPROVED on a PUBLISHED hotel) cannot be soft-deleted by the partner either
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL AND OLD.review_status = 'APPROVED'
           AND EXISTS (SELECT 1 FROM hotels h WHERE h.id = OLD.hotel_id AND h.publication_status = 'PUBLISHED') THEN
            RAISE EXCEPTION 'partner may not delete a live photo of a published hotel';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
