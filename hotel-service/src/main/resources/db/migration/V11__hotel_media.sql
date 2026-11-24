-- Hotel photos, phase 2 (HOTEL-PUBLICATION-CONTRACT.md sec. 5).
-- Files live on a volume under a random storage_key, never under the client file name.

CREATE TABLE hotel_media (
    id               UUID PRIMARY KEY,
    hotel_id         UUID         NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID         NOT NULL,
    kind             VARCHAR(16)  NOT NULL DEFAULT 'PHOTO',
    sort_order       INT          NOT NULL DEFAULT 0,
    caption          VARCHAR(200),
    rights_source    VARCHAR(30)  NOT NULL,
    rights_note      VARCHAR(500),
    storage_key      UUID         NOT NULL,
    content_type     VARCHAR(30)  NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    width            INT          NOT NULL,
    height           INT          NOT NULL,
    scan_status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    review_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    review_reason    VARCHAR(1000),
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    uploaded_by      UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_hm_kind          CHECK (kind IN ('PHOTO')),
    CONSTRAINT chk_hm_rights_source CHECK (rights_source IN ('OWNED','LICENSED','PARTNER_ATTESTED')),
    CONSTRAINT chk_hm_content_type  CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    CONSTRAINT chk_hm_size          CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT chk_hm_dimensions    CHECK (width BETWEEN 800 AND 8000 AND height BETWEEN 600 AND 8000),
    CONSTRAINT chk_hm_scan_status   CHECK (scan_status IN ('PENDING','CLEAN','REJECTED')),
    CONSTRAINT chk_hm_review_status CHECK (review_status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT chk_hm_reject_reason CHECK (review_status <> 'REJECTED' OR length(btrim(coalesce(review_reason,''))) >= 5),
    CONSTRAINT uq_hm_storage_key    UNIQUE (storage_key)
);
CREATE INDEX idx_hm_hotel  ON hotel_media (hotel_id, sort_order, created_at);
CREATE INDEX idx_hm_org    ON hotel_media (organization_id);
CREATE INDEX idx_hm_review ON hotel_media (review_status, created_at);

-- Append only audit of every admin review decision.
CREATE TABLE hotel_media_decisions (
    id               UUID PRIMARY KEY,
    media_id         UUID         NOT NULL REFERENCES hotel_media(id) ON DELETE RESTRICT,
    hotel_id         UUID         NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID         NOT NULL,
    decision         VARCHAR(16)  NOT NULL,
    from_status      VARCHAR(16)  NOT NULL,
    to_status        VARCHAR(16)  NOT NULL,
    reason           VARCHAR(1000),
    actor_user_id    UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_hmd_decision CHECK (decision IN ('APPROVE','REJECT')),
    CONSTRAINT chk_hmd_reason   CHECK (decision <> 'REJECT' OR length(btrim(coalesce(reason,''))) >= 5)
);
CREATE INDEX idx_hmd_media ON hotel_media_decisions (media_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_media, hotel_media_decisions TO waylo_app;

-- RLS (V9 helpers reused).
-- hotel_media: public SELECT only for CLEAN + APPROVED rows (the service additionally requires a PUBLISHED hotel).
ALTER TABLE hotel_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_media FORCE  ROW LEVEL SECURITY;
CREATE POLICY hm_read ON hotel_media FOR SELECT
    USING (app_mode() IN ('service','admin')
           OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids()))
           OR (app_mode() = 'public' AND scan_status = 'CLEAN' AND review_status = 'APPROVED'));
CREATE POLICY hm_insert ON hotel_media FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin')
                OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids()) AND review_status = 'PENDING'));
CREATE POLICY hm_update ON hotel_media FOR UPDATE
    USING      (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
-- Partner may delete own photos unless the photo is live (APPROVED on a PUBLISHED hotel). Admin and service: always.
CREATE POLICY hm_delete ON hotel_media FOR DELETE
    USING (app_mode() IN ('service','admin')
           OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())
               AND NOT (review_status = 'APPROVED'
                        AND EXISTS (SELECT 1 FROM hotels h WHERE h.id = hotel_id AND h.publication_status = 'PUBLISHED'))));

-- A partner can never approve or re-scan its own photo, even with a bug in the service code.
-- It may only keep review_status or send it back to PENDING (a caption edit on a live photo needs a new review).
CREATE FUNCTION hotel_media_partner_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF app_mode() = 'partner' THEN
        IF NEW.hotel_id <> OLD.hotel_id OR NEW.organization_id <> OLD.organization_id
           OR NEW.storage_key <> OLD.storage_key OR NEW.scan_status <> OLD.scan_status
           OR NEW.review_status NOT IN (OLD.review_status, 'PENDING') THEN
            RAISE EXCEPTION 'partner may not change review or storage fields of hotel_media';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_hotel_media_partner_guard BEFORE UPDATE ON hotel_media
    FOR EACH ROW EXECUTE FUNCTION hotel_media_partner_guard();

-- Audit: admin/service read all, partner reads own org, nobody updates or deletes. Public sees nothing.
ALTER TABLE hotel_media_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_media_decisions FORCE  ROW LEVEL SECURITY;
CREATE POLICY hmd_read ON hotel_media_decisions FOR SELECT
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hmd_insert ON hotel_media_decisions FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin'));
