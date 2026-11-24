-- Hotel publication workflow + content revisions (HOTEL-PUBLICATION-CONTRACT.md, RM-18).

ALTER TABLE hotels
    ADD COLUMN publication_status   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_revision_id UUID,
    ADD COLUMN published_at         TIMESTAMPTZ,
    ADD COLUMN content_version      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN source_type          VARCHAR(16) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE hotels
    ADD CONSTRAINT chk_hotels_publication_status
        CHECK (publication_status IN ('DRAFT','SUBMITTED','APPROVED','PUBLISHED','REJECTED','SUSPENDED','ARCHIVED')),
    ADD CONSTRAINT chk_hotels_source_type CHECK (source_type IN ('DIRECT','EXTERNAL')),
    ADD CONSTRAINT chk_hotels_published_has_stamp
        CHECK (publication_status <> 'PUBLISHED' OR published_at IS NOT NULL);

CREATE INDEX idx_hotels_publication_status ON hotels (publication_status);
CREATE INDEX idx_hotels_public_listing ON hotels (city, country) WHERE publication_status = 'PUBLISHED' AND active;

-- One row per partner submission. At most one open (SUBMITTED) request per hotel.
CREATE TABLE hotel_publication_requests (
    id               UUID PRIMARY KEY,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID        NOT NULL,
    kind             VARCHAR(16) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    revision_id      UUID,
    submitted_by     UUID        NOT NULL,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    submit_note      VARCHAR(1000),
    decided_at       TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_hpr_kind   CHECK (kind IN ('INITIAL','CONTENT_CHANGE')),
    CONSTRAINT chk_hpr_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
    CONSTRAINT chk_hpr_content_has_revision CHECK (kind <> 'CONTENT_CHANGE' OR revision_id IS NOT NULL),
    CONSTRAINT chk_hpr_decided CHECK ((status = 'SUBMITTED') = (decided_at IS NULL))
);
CREATE UNIQUE INDEX uq_hpr_one_open_per_hotel ON hotel_publication_requests (hotel_id) WHERE status = 'SUBMITTED';
CREATE INDEX idx_hpr_org     ON hotel_publication_requests (organization_id, submitted_at DESC);
CREATE INDEX idx_hpr_status  ON hotel_publication_requests (status, submitted_at);

-- Append only audit of every state change (partner and admin). Never updated, never deleted.
CREATE TABLE hotel_publication_decisions (
    id               UUID PRIMARY KEY,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID,
    request_id       UUID        REFERENCES hotel_publication_requests(id) ON DELETE RESTRICT,
    decision         VARCHAR(24) NOT NULL,
    from_status      VARCHAR(16),
    to_status        VARCHAR(16) NOT NULL,
    actor_user_id    UUID,
    actor_role       VARCHAR(16) NOT NULL,
    reason           VARCHAR(1000),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_hpd_decision CHECK (decision IN
        ('SUBMIT','WITHDRAW','APPROVE','REJECT','PUBLISH','SUSPEND','REINSTATE','ARCHIVE','MIGRATION')),
    CONSTRAINT chk_hpd_actor_role CHECK (actor_role IN ('ADMIN','PARTNER','SYSTEM')),
    CONSTRAINT chk_hpd_actor CHECK ((decision = 'MIGRATION') = (actor_user_id IS NULL)),
    CONSTRAINT chk_hpd_reason CHECK (decision NOT IN ('REJECT','SUSPEND','REINSTATE','ARCHIVE')
                                     OR length(btrim(coalesce(reason,''))) >= 5)
);
CREATE INDEX idx_hpd_hotel ON hotel_publication_decisions (hotel_id, created_at DESC);
CREATE INDEX idx_hpd_org   ON hotel_publication_decisions (organization_id, created_at DESC);

-- Content revisions. Live content stays in hotels columns for published state.
CREATE TABLE hotel_content_revisions (
    id               UUID PRIMARY KEY,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID        NOT NULL,
    revision_no      INT         NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    content          JSONB       NOT NULL,
    base_content_version BIGINT  NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_by       UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at       TIMESTAMPTZ,
    CONSTRAINT chk_hcr_status CHECK (status IN ('DRAFT','SUBMITTED','APPLIED','REJECTED','SUPERSEDED')),
    CONSTRAINT chk_hcr_applied CHECK ((status = 'APPLIED') = (applied_at IS NOT NULL)),
    CONSTRAINT uq_hcr_hotel_revno UNIQUE (hotel_id, revision_no)
);
CREATE UNIQUE INDEX uq_hcr_one_working_per_hotel ON hotel_content_revisions (hotel_id) WHERE status IN ('DRAFT','SUBMITTED');
CREATE INDEX idx_hcr_org ON hotel_content_revisions (organization_id);

ALTER TABLE hotel_publication_requests
    ADD CONSTRAINT fk_hpr_revision FOREIGN KEY (revision_id) REFERENCES hotel_content_revisions(id) ON DELETE RESTRICT;
ALTER TABLE hotels
    ADD CONSTRAINT fk_hotels_published_revision FOREIGN KEY (published_revision_id) REFERENCES hotel_content_revisions(id) ON DELETE SET NULL;

-- Backfill (D1): every existing hotel is grandfathered as PUBLISHED so nothing public disappears.
UPDATE hotels
   SET publication_status = 'PUBLISHED',
       published_at       = created_at AT TIME ZONE 'UTC'
 WHERE true;

INSERT INTO hotel_publication_decisions
       (id, hotel_id, organization_id, request_id, decision, from_status, to_status, actor_user_id, actor_role, reason)
SELECT gen_random_uuid(), id, organization_id, NULL, 'MIGRATION', NULL, 'PUBLISHED', NULL, 'SYSTEM',
       'V10 backfill: existing hotel grandfathered as PUBLISHED'
  FROM hotels;

-- RLS (V9 helpers app_mode()/app_org_ids() reused). Public SELECT is denied on these tables.
GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_publication_requests, hotel_publication_decisions, hotel_content_revisions TO waylo_app;

ALTER TABLE hotel_publication_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_publication_requests FORCE  ROW LEVEL SECURITY;
CREATE POLICY hpr_read ON hotel_publication_requests FOR SELECT
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hpr_insert ON hotel_publication_requests FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hpr_update ON hotel_publication_requests FOR UPDATE
    USING      (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hpr_delete ON hotel_publication_requests FOR DELETE USING (app_mode() = 'service');

ALTER TABLE hotel_content_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_content_revisions FORCE  ROW LEVEL SECURITY;
CREATE POLICY hcr_read ON hotel_content_revisions FOR SELECT
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hcr_insert ON hotel_content_revisions FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hcr_update ON hotel_content_revisions FOR UPDATE
    USING      (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())))
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hcr_delete ON hotel_content_revisions FOR DELETE
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids()) AND status = 'DRAFT'));

ALTER TABLE hotel_publication_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hotel_publication_decisions FORCE  ROW LEVEL SECURITY;
CREATE POLICY hpd_read ON hotel_publication_decisions FOR SELECT
    USING (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
CREATE POLICY hpd_insert ON hotel_publication_decisions FOR INSERT
    WITH CHECK (app_mode() IN ('service','admin') OR (app_mode() = 'partner' AND organization_id = ANY (app_org_ids())));
