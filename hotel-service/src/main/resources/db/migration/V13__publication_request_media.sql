-- Media phase 3 (contract sec. 5): a publication request carries the photos it asks the admin to decide.
-- Link rows are immutable (no UPDATE policy); only service mode may delete.

CREATE TABLE hotel_publication_request_media (
    request_id       UUID        NOT NULL REFERENCES hotel_publication_requests(id) ON DELETE RESTRICT,
    media_id         UUID        NOT NULL REFERENCES hotel_media(id) ON DELETE RESTRICT,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, media_id)
);
CREATE INDEX idx_hprm_media ON hotel_publication_request_media (media_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_publication_request_media TO waylo_app;

ALTER TABLE hotel_publication_request_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_publication_request_media FORCE  ROW LEVEL SECURITY;
CREATE POLICY hprm_read ON hotel_publication_request_media FOR SELECT
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hprm_insert ON hotel_publication_request_media FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hprm_delete ON hotel_publication_request_media FOR DELETE USING (app_mode() = 'service');
