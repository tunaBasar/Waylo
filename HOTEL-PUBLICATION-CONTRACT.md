# Hotel publication and content revision contract (RM-18, acceptance R1)

Binding for hotel-service (Flyway V10), api-gateway route, frontend partner and admin pages, and the live scripts. Sources: PRODUCT-ARCHITECTURE.md sec. 3, 4 "Icerik ve yayin", 11 (R1, R3, R10), WAYLO-MVP-ROADMAP.md RM-17/RM-18, auth-service/ORGANIZATION-DOMAIN.md. Written from the current code (hotel-service V1..V9, uncommitted V9 RLS included). All JSON uses the existing `ApiResponse` envelope, errors the existing `ErrorResponse` with an `error` code. Timestamps are `timestamptz` in the new tables.

Scope decisions (see sec. 9 for what the user may still change):
- Publication is a hotel-service concern. Organization approval (auth-service) stays a separate decision and never publishes a hotel.
- Paths stay `/api/hotels/{id}/...` (the style the rest of hotel-service uses). The roadmap's `/api/v1/partner/organizations/{orgId}/hotels/{hotelId}/...` paths are NOT used in this slice, because nothing in the repo uses `/api/v1`. Authorization still resolves the org from the hotel row (`HotelAccessGuard`), never from a client supplied org id.
- Photos are phase 2 (sec. 5). Phase 1 = state machine, content revision (text fields), admin review, public gate.

## 1. State machine

`hotels.publication_status`: `DRAFT | SUBMITTED | APPROVED | PUBLISHED | REJECTED | SUSPENDED | ARCHIVED`.

Meaning:
- DRAFT: partner is editing. Not public. A new hotel starts here.
- SUBMITTED: a publication request (kind INITIAL) is waiting for admin. Partner edits are locked (409 `HOTEL_LOCKED_FOR_REVIEW`).
- APPROVED: admin accepted the content, not yet on sale. Exists so approve and publish are two recorded decisions. Admin may do both in one call (`APPROVE_AND_PUBLISH`, sec. 4.3), which records two decision rows (APPROVE then PUBLISH).
- PUBLISHED: public and sellable (subject to `active` and org status below).
- REJECTED: admin said no, reason stored. Partner edits in place again and resubmits.
- SUSPENDED: new sales closed and hidden from public. Existing reservations keep working (sec. 1.3).
- ARCHIVED: terminal. Hidden, no sales, no transitions out. Rows are kept (reservations, reviews, orders reference them).

### 1.1 Transitions

Permission names are the existing auth-service ones: partner actor = `HOTEL_MANAGE` on that hotel (checked by `HotelAccessGuard.requireHotelManage`, ORGANIZATION_OWNER, HOTEL_OWNER or HOTEL_MANAGER of the hotel; HOTEL_STAFF cannot). Admin actor = gateway `X-User-Role: ADMIN` (platform admin). No new auth-service permission is added.

| # | From | To | Command | Actor / permission | Reason required | Audit row (`hotel_publication_decisions`) |
|---|---|---|---|---|---|---|
| T1 | (create) | DRAFT | `POST /api/hotels` | partner `HOTEL_CREATE` (exists) | no | none (the hotel row `created_at` is the evidence) |
| T2 | DRAFT, REJECTED | SUBMITTED | submit INITIAL request | partner `HOTEL_MANAGE` | no | request row created, decision row `SUBMIT` (actor = partner) |
| T3 | SUBMITTED | DRAFT | withdraw | partner `HOTEL_MANAGE` | no | decision `WITHDRAW` |
| T4 | SUBMITTED | APPROVED | `APPROVE` | admin | no (optional note) | decision `APPROVE` |
| T5 | SUBMITTED | REJECTED | `REJECT` | admin | **yes** (5..1000 chars) | decision `REJECT` with reason |
| T6 | APPROVED | PUBLISHED | `PUBLISH` (or `APPROVE_AND_PUBLISH` from SUBMITTED) | admin | no | decision `PUBLISH` (+ `APPROVE` row when combined) |
| T7 | PUBLISHED | SUSPENDED | `SUSPEND` | admin | **yes** | decision `SUSPEND` |
| T8 | SUSPENDED | PUBLISHED | `REINSTATE` | admin | **yes** | decision `REINSTATE` |
| T9 | DRAFT, REJECTED, APPROVED, SUSPENDED | ARCHIVED | `ARCHIVE` | admin, or partner for DRAFT and REJECTED only | **yes** | decision `ARCHIVE` |
| T10 | PUBLISHED | PUBLISHED | content change approved (kind CONTENT_CHANGE) | admin `APPROVE` | no | decision `APPROVE` (+ revision applied, sec. 4.3) |
| T11 | PUBLISHED, SUSPENDED | same | content change rejected | admin `REJECT` | **yes** | decision `REJECT`, hotel status unchanged, previous revision stays public |

Anything not in the table is 409 `INVALID_STATE_TRANSITION` (response carries `from` and `to`). `ARCHIVED` has no outgoing row. Every decision is optimistic: the request carries `expectedVersion` of the publication request and a mismatch is 409 `VERSION_CONFLICT`.

An ARCHIVE of a hotel with future CONFIRMED or HELD holds is allowed (it only closes new sales). Archive must not touch holds, quotes, inventory, rooms or reviews.

### 1.2 Interaction with `active`

`hotels.active` stays exactly as is (partner `deactivate`/`activate`, `POST /api/hotels/{id}/deactivate|activate`). It is the partner's own "pause sales" switch and is orthogonal to publication.

Public visibility and sellability rule (single definition, used everywhere in sec. 3.4):

```
visible = publication_status = 'PUBLISHED'
          AND active = true
          AND (organization_id IS NULL OR organization status is ACTIVE)   -- ActiveOrganizationIdsCache, fail closed
```

- Org status is read at query time from auth (`ActiveOrganizationIdsCache` for lists, `AuthAccessClient.activeOrganizationIds()` in quote and availability), fail closed, exactly as today. Nothing about org status is copied into the hotels table.
- Legacy hotels (`organization_id IS NULL`) skip the org clause as today. They are backfilled PUBLISHED (sec. 2.3) so they stay public. Partner write access to them stays locked (`HotelAccessGuard` throws AccessDenied for null org). Admin may still suspend or archive them via the decide endpoint (admin mode bypasses the org lookup, there is no partner check).
- `deactivate` on a PUBLISHED hotel keeps status PUBLISHED, hides and unsells it. `activate` does not change `publication_status`. `activate` must NOT make a non-PUBLISHED hotel visible (the rule above already guarantees this).
- Create sets `active = true` (unchanged), so a DRAFT hotel is `active=true, DRAFT`. Visibility is blocked by status, not by `active`.

### 1.3 Interaction with organization status

- Submitting a publication request requires the organization to be ACTIVE (not PENDING, REJECTED, SUSPENDED). Otherwise 409 `ORGANIZATION_NOT_ACTIVE`. Status comes from `activeOrganizationIds()`, fail closed (503 `SERVICE_UNAVAILABLE` if auth is down, same exception type quote already uses: `AuthorizationUnavailableException`). Rationale: RM-17 says org approval and hotel approval are separate, but admin should not review hotels of an org that is not itself approved.
- Admin APPROVE and PUBLISH do not re-check org status (admin decides). Public visibility still requires org ACTIVE at read time, so a later org suspension hides every hotel of the org without touching hotel rows (current behaviour kept). Org approval alone never changes any hotel status.
- Suspension semantics for "existing reservations keep working": `SUSPENDED`, `ARCHIVED`, org-not-active, and `active=false` all close only NEW quotes and availability. `HotelHoldServiceImpl.confirm/release/findById` and `/internal/hotels/{id}` do not consult publication status, only the quote creation does (sec. 3.4). A `confirm` of a hold created before suspension must still succeed (test U8).

## 2. Data: Flyway `V10__hotel_publication.sql`

Next free version is V10 (V9 is the RLS migration). The migration must run as the owner (Flyway does), like V9. It must be one transaction (Flyway default).

### 2.1 Columns on `hotels`

```sql
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
```

- The DEFAULT `'DRAFT'` only serves rows inserted after V10 by code that does not set it. The JPA entity sets it explicitly (sec. 3.1). The backfill below runs BEFORE any new insert and fixes every existing row.
- `source_type` is included because PRODUCT-ARCHITECTURE sec. 3 lists `Hotel(organizationId, timezone, publicationStatus, sourceType)`. It is only stored now, no behaviour. (`timezone` is out of scope here, not a publication concern.) If the user prefers a smaller slice, drop `source_type`, nothing else depends on it.
- `content_version` is the optimistic version of the live (published) content, bumped when a revision is applied or a DRAFT/REJECTED hotel is edited in place. `hotels.version` does not exist today and is not added.
- `published_revision_id` points at the `hotel_content_revisions` row currently public, NULL for backfilled rows (their content is the hotel row itself, no revision exists).

### 2.2 New tables

```sql
-- One row per partner submission. At most one open (SUBMITTED) request per hotel.
CREATE TABLE hotel_publication_requests (
    id               UUID PRIMARY KEY,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID        NOT NULL,            -- copied from hotels.organization_id at submit, tenant column for RLS
    kind             VARCHAR(16) NOT NULL,            -- INITIAL | CONTENT_CHANGE
    status           VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED | APPROVED | REJECTED | WITHDRAWN
    revision_id      UUID,                            -- the revision under review (required for CONTENT_CHANGE)
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
    organization_id  UUID,                            -- NULL for legacy hotels
    request_id       UUID        REFERENCES hotel_publication_requests(id) ON DELETE RESTRICT,  -- NULL for SUSPEND/REINSTATE/ARCHIVE/MIGRATION
    decision         VARCHAR(24) NOT NULL,
    from_status      VARCHAR(16),
    to_status        VARCHAR(16) NOT NULL,
    actor_user_id    UUID,                            -- NULL only for decision = 'MIGRATION'
    actor_role       VARCHAR(16) NOT NULL,            -- ADMIN | PARTNER | SYSTEM
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

-- Content revisions. Live content stays in hotels columns for published state (no join on the hot public path).
CREATE TABLE hotel_content_revisions (
    id               UUID PRIMARY KEY,
    hotel_id         UUID        NOT NULL REFERENCES hotels(id) ON DELETE RESTRICT,
    organization_id  UUID        NOT NULL,
    revision_no      INT         NOT NULL,            -- 1,2,3... per hotel
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | SUBMITTED | APPLIED | REJECTED | SUPERSEDED
    content          JSONB       NOT NULL,            -- allowlisted fields only, sec. 3.2
    base_content_version BIGINT  NOT NULL,            -- hotels.content_version at creation, for diff and stale checks
    version          BIGINT      NOT NULL DEFAULT 0,  -- optimistic version of this revision (expectedVersion in PATCH)
    created_by       UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at       TIMESTAMPTZ,
    CONSTRAINT chk_hcr_status CHECK (status IN ('DRAFT','SUBMITTED','APPLIED','REJECTED','SUPERSEDED')),
    CONSTRAINT chk_hcr_applied CHECK ((status = 'APPLIED') = (applied_at IS NOT NULL)),
    CONSTRAINT uq_hcr_hotel_revno UNIQUE (hotel_id, revision_no)
);
-- At most one working revision per hotel (DRAFT or SUBMITTED) so partners cannot fork content.
CREATE UNIQUE INDEX uq_hcr_one_working_per_hotel ON hotel_content_revisions (hotel_id) WHERE status IN ('DRAFT','SUBMITTED');
CREATE INDEX idx_hcr_org ON hotel_content_revisions (organization_id);

ALTER TABLE hotel_publication_requests
    ADD CONSTRAINT fk_hpr_revision FOREIGN KEY (revision_id) REFERENCES hotel_content_revisions(id) ON DELETE RESTRICT;
ALTER TABLE hotels
    ADD CONSTRAINT fk_hotels_published_revision FOREIGN KEY (published_revision_id) REFERENCES hotel_content_revisions(id) ON DELETE SET NULL;
```

Notes the implementer must keep:
- `hotels.id` rows referenced with `ON DELETE RESTRICT`: `HotelController.delete` (`DELETE /api/hotels/{id}`) currently deletes hotels. After V10 it must only be allowed for a DRAFT hotel that has no request/decision/revision rows (otherwise 409 `HOTEL_HAS_HISTORY`, use ARCHIVE). Because of RESTRICT, a delete of a hotel with history fails in the DB anyway, so catch `DataIntegrityViolationException` is not needed if the check is explicit.
- `organization_id` is NOT NULL on requests and revisions: only org-owned hotels can be submitted. Legacy hotels (org NULL) cannot have partner requests or revisions (admin transitions only, which write decisions with `organization_id = NULL`).
- Timestamps in V1 tables are `TIMESTAMP` (hotels.created_at) while the entity uses `Instant`. New tables use `TIMESTAMPTZ`, map with `Instant`.

### 2.3 Backfill (runs inside V10, right after the ALTERs, same transaction)

Goal: nothing public today disappears, 22 legacy hotels untouched.

Public today = `active AND (organization_id IS NULL OR org ACTIVE in auth)`. Org status lives in auth's database, SQL cannot read it (DB per service). So the rule is a superset on purpose:

```sql
UPDATE hotels
   SET publication_status = 'PUBLISHED',
       published_at       = created_at AT TIME ZONE 'UTC'   -- TIMESTAMP -> timestamptz, any stable value is fine
 WHERE true;                                                  -- ALL existing rows, active or not

INSERT INTO hotel_publication_decisions
       (id, hotel_id, organization_id, request_id, decision, from_status, to_status, actor_user_id, actor_role, reason)
SELECT gen_random_uuid(), id, organization_id, NULL, 'MIGRATION', NULL, 'PUBLISHED', NULL, 'SYSTEM',
       'V10 backfill: existing hotel grandfathered as PUBLISHED'
  FROM hotels;
```

(`gen_random_uuid()` is core in PostgreSQL 13+. The compose Postgres is 16. If a Flyway test container older than 13 is used, replace with `uuid_generate_v4()`.)

What this means, stated plainly:
- Active hotels of ACTIVE orgs: PUBLISHED, still visible (same org check at read time). Pass.
- 22 legacy `organization_id IS NULL` hotels: PUBLISHED, `active` unchanged, still visible, still read-only for partners. Pass. The migration test must assert the count of `organization_id IS NULL` rows is unchanged and all are PUBLISHED. The "22" is the value reported by the task, the test must compare before/after counts, not hardcode 22.
- Inactive hotels (`active=false`): PUBLISHED but hidden by `active`, and the partner can `activate` them as today. Intentional, so reactivation keeps working with no admin step.
- Hotels of PENDING/SUSPENDED orgs: also marked PUBLISHED (SQL cannot tell). Today they are hidden by the org clause, and stay hidden until the org becomes ACTIVE, at which point they become public with no hotel review. That is the one behavioural leftover. It only affects data created before V10 (dev/test data in this project: no production hotels exist for pending orgs per the pilot plan). **Open decision D1** (sec. 9): accept, or run a one-off admin SQL/script before deploy that moves non-active-org hotels to DRAFT. Not done in V10 because it cannot be computed there.
- `published_revision_id` stays NULL for all backfilled rows.

Nothing else in V1..V9 is altered. No existing column is dropped or retyped. The migration is additive and re-runnable by Flyway only once (no `IF NOT EXISTS` needed, follow V2's plain style).

### 2.4 RLS for the new tables (consistent with V9)

V9 gives `waylo_app` default privileges on tables created later (`ALTER DEFAULT PRIVILEGES ... GRANT SELECT, INSERT, UPDATE, DELETE`), so V10 tables are accessible to the role automatically. RLS is NOT automatic, V10 must enable and force it with the same helper functions (`app_mode()`, `app_org_ids()`, `app_hotel_in_scope(hotel_id)` already exist, reuse them, do not redefine).

Mode behaviour (TenantContextFilter: `/internal` = service, gateway ADMIN = admin, rest = public; `TenantContext.authorizePartner` upgrades to partner after `HotelAccessGuard` succeeds):

| Table | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| `hotel_publication_requests` | service/admin all, public none, partner own org only | partner own org, service, admin | same as insert (partner may only withdraw, enforced in code) | none for anyone except service |
| `hotel_content_revisions` | service/admin all, public none, partner own org only | partner own org, service, admin | partner own org, service, admin | partner own org while status DRAFT, service/admin |
| `hotel_publication_decisions` | service/admin all, public none, partner own org only | partner own org, service, admin | **nobody** (no policy = denied under FORCE RLS) | **nobody** |

Note the difference from V9 child tables: V9 SELECT lets public mode read everything (public filtering happens in code). The new tables hold review notes and unpublished content, so **public SELECT is denied in the policy**. The public projection reads only `hotels` (already public SELECT). The public detail never joins revisions.

Exact policy shape (copy the V9 style, loop-free is fine, three tables):

```sql
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
-- hotel_content_revisions: same four policies (hcr_*), delete = service/admin or partner own org.
-- hotel_publication_decisions: hpd_read and hpd_insert only. No update/delete policy.
```

Use `organization_id = ANY (app_org_ids())` directly (columns exist on these tables), not `app_hotel_in_scope`, so no function call per row. Decisions rows with `organization_id IS NULL` (legacy, MIGRATION) are visible only to service/admin (NULL never matches ANY).

Admin endpoints run in ADMIN mode (filter sets it from the gateway role), so they read and write all three tables. The admin decision also UPDATEs `hotels` (V9 allows admin). Partner submit runs in PARTNER mode after `requireHotelManage` (which calls `authorizePartner`). **Ordering trap:** the access guard must run before the first transaction touching these tables, as in `HotelController` today. Do the guard call in the controller, the transaction in the service.

Also: the scheduler/Kafka threads default to SERVICE mode (unchanged), so no job here breaks.

`TenantRlsPoolLeakIntegrationTest` must be extended (sec. 8, I2) to cover the new tables.

## 3. Domain and projection

### 3.1 Code changes (hotel-service, `com.waylo.hotelservice`)

- `domain/PublicationStatus.java` enum (7 values). `Hotel` gains `publicationStatus` (`@Enumerated(STRING)`, default DRAFT in the field initializer), `publishedRevisionId`, `publishedAt`, `contentVersion`, `sourceType`. `HotelMapper.toEntity` must leave status DRAFT even if a client sends one (`HotelRequest` does not get a status field).
- New package `publication/`: `HotelPublicationRequest`, `HotelPublicationDecision`, `HotelContentRevision` entities, three repositories, `HotelPublicationService` (all transitions, one transaction each, `@Version` on request and revision), `HotelPublicationController` (partner), `AdminHotelPublicationController`.
- `HotelResponse` gains `publicationStatus` (String) and `contentVersion`. Public responses must still show only published content, but exposing the status of a PUBLISHED hotel is harmless. Add them as new trailing record components and keep the 14-arg and 16-arg constructors working (`HotelResponse` has a convenience constructor and `withReviews`, update both).
- One constructor per Spring bean (CLAUDE.md rule 5). Run `python3 scripts/check-bean-constructors.py`.

### 3.2 Revision content allowlist

`hotel_content_revisions.content` JSON, keys exactly: `name` (1..255), `city`, `country` (1..255), `address` (1..500), `starRating` (1..5), `description` (<= 5000, nullable), `latitude`, `longitude` (nullable, -90..90 and -180..180). These are the editable fields of `HotelRequest` minus `organizationId`. Unknown keys are 400 `VALIDATION_ERROR` (reject, do not ignore) so business documents, owner PII or commercial fields can never be stored there. Photos are not in this JSON (phase 2). Prices, inventory, room types and rate plans are NOT content revisions (PRODUCT-ARCHITECTURE: "Musaitlik/fiyat guncellemesi icerik revision'indan ayridir") and keep their own endpoints unchanged.

`starRating` is shown as the partner declared classification, never as the review score (review score is `averageScore` from verified reviews, already separate in `HotelResponse`). Do not label it "official". Admin sees it while reviewing and can reject.

### 3.3 Where content edits go, by status

| Hotel status | `PUT /api/hotels/{id}` (existing) | Content revision endpoints |
|---|---|---|
| DRAFT, REJECTED | edits the hotel row directly (nothing public to protect), bumps `content_version` | not needed, 409 `USE_DIRECT_EDIT` |
| SUBMITTED | 409 `HOTEL_LOCKED_FOR_REVIEW` | 409 `HOTEL_LOCKED_FOR_REVIEW` |
| APPROVED | 409 `HOTEL_LOCKED_FOR_REVIEW` (admin is about to publish) | 409 same |
| PUBLISHED, SUSPENDED | **409 `CONTENT_REQUIRES_REVISION`** | allowed (sec. 4.2) |
| ARCHIVED | 409 `HOTEL_ARCHIVED` | 409 `HOTEL_ARCHIVED` |

`PUT /api/hotels/{id}` must still accept `organizationId` unchanged only: changing `organizationId` of an existing hotel via PUT is currently possible in `HotelMapper.updateEntity` (verify when implementing). If it sets the org, reject a changed value with 400 `ORGANIZATION_CHANGE_NOT_ALLOWED`. This closes a hand-off hole (R3) and is in scope because a partner could otherwise move a PUBLISHED hotel to another org.

`deactivate`/`activate` stay allowed in every status except ARCHIVED (409 `HOTEL_ARCHIVED`).

### 3.4 Public projection and sales gate (the rule change)

Introduce one predicate in `HotelServiceImpl.filterPublic` (replace `Hotel::isActive` stage) and one in `QuoteServiceImpl`/`AvailabilityServiceImpl`:
`publication_status == PUBLISHED && active && org clause` (sec. 1.2).

| Endpoint | Today | After V10 |
|---|---|---|
| `GET /api/hotels` (unscoped list) | active + org ACTIVE | + PUBLISHED |
| `GET /api/hotels?organizationId=X` anonymous or non member | `getPublicByOrganization` (filterPublic) | + PUBLISHED (via filterPublic) |
| `GET /api/hotels?organizationId=X` member or ADMIN | `getByOrganization`, unfiltered | unchanged, includes every status, response carries `publicationStatus` |
| `GET /api/hotels/search` (both variants, `HotelSearchService` goes through `HotelService.search`/`getAll`) | filterPublic | + PUBLISHED |
| `GET /api/hotels/{id}` | `isPublic(id)` else member/ADMIN, else 404 | `isPublic` includes PUBLISHED. Unpublished still 404 for non members. **Members and ADMIN see the current live row** (not the draft revision), plus `publicationStatus` |
| `GET /api/hotels/{id}/availability` | active + org ACTIVE, else "not available" body | + PUBLISHED. Not PUBLISHED returns the same non-bookable shape it returns for inactive today (keep response, do not turn it into 404) |
| `GET /api/hotels/{id}/room-types`, `/rate-plans`, `/rooms`, `/rates` (anonymous reads) | no visibility check at all | **add**: if the hotel is not public (`isPublic` false) and the caller is not a member/ADMIN of its org, 404. `includeInactive=true` paths already require manage. This closes anonymous enumeration of draft children (R1 "onaysiz otel public listede yok"). Implementation: one helper in `HotelAccessGuard` e.g. `requireReadable(userId, role, hotelId)`; do not change the manage paths |
| `GET /api/hotels/{id}/reviews` | public | unchanged. Hidden implicitly by `isPublic` only if you reuse the same helper, keep unchanged unless D3 (sec. 9) says otherwise |
| `POST /api/quotes` (`QuoteServiceImpl.createQuote`, after the `!hotel.isActive()` check at step 2) | `HOTEL_NOT_BOOKABLE` 422 when inactive, org check next | add `publication_status != PUBLISHED` -> same `BusinessRuleException("HOTEL_NOT_BOOKABLE")`. Order: inactive, not PUBLISHED, org |
| `POST /internal/hotel-holds` (`HotelHoldServiceImpl.hold`) | uses the quote, no hotel check | add a re-check right after quote lock: load the hotel, if not PUBLISHED or inactive -> `HOTEL_NOT_BOOKABLE`. A quote created before a suspension must not turn into a hold |
| `POST /internal/hotel-holds/{id}/confirm`, `/release`, `GET /internal/hotel-holds/{id}` | no hotel check | **unchanged, must NOT check publication** (existing reservations survive suspension) |
| `GET /internal/hotels/{id}` | unfiltered (auth-service role grant uses it while org is PENDING) | unchanged, returns `publicationStatus` too |
| `GET /api/hotels/{id}/quotes`/quote reads by owner | owner only | unchanged |

Quote validity: a quote already created stays valid until expiry only for the hold step above, and the hold step re-checks (so effective cutoff is the hold). Orders already CONFIRMED are untouched.

## 4. HTTP API (hotel-service, behind api-gateway)

Headers as everywhere: `X-User-Id`, `X-User-Role` set by the gateway from the JWT. Never trust a client org id. `ApiResponse` envelope omitted below for brevity, show only `data`.

Error codes used: 400 `VALIDATION_ERROR`; 401 `AUTHENTICATION_REQUIRED`; 403 `FORBIDDEN` (non member, staff, non admin, and also "Hotel has no owning organization"); 404 `NOT_FOUND` (unknown hotel/request, or hotel not owned: partner endpoints answer 403 for a real hotel of another org if the caller is authenticated but not a member, 404 only if the hotel id does not exist. This mirrors `HotelAccessGuard.requireHotelManage` which loads the hotel first, keep that behaviour, do not invent a different one); 409 as listed per endpoint; 503 `SERVICE_UNAVAILABLE` when auth is unreachable.

### 4.1 Partner: publication requests

`POST /api/hotels/{hotelId}/publication-requests` (guard: `requireHotelManage`, permission `HOTEL_MANAGE`)

Request (INITIAL, hotel DRAFT or REJECTED):
```json
{ "note": "Acilis icin hazir" }
```
`note` optional, max 1000. For a PUBLISHED or SUSPENDED hotel the same endpoint submits the working content revision (kind CONTENT_CHANGE):
```json
{ "note": "Aciklama guncellendi", "revisionId": "uuid", "expectedVersion": 3 }
```
`revisionId` and `expectedVersion` (the revision's `version`) required for CONTENT_CHANGE, forbidden for INITIAL (400).

Response 201:
```json
{ "id": "uuid", "hotelId": "uuid", "organizationId": "uuid", "kind": "INITIAL", "status": "SUBMITTED",
  "revisionId": null, "submittedBy": "uuid", "submittedAt": "2026-11-23T14:00:00Z", "note": "Acilis icin hazir",
  "version": 0, "hotelPublicationStatus": "SUBMITTED" }
```
Behaviour (one transaction): validate completeness for INITIAL (name, city, country, address, starRating present, which `HotelRequest` already enforces at create, so the extra gate is at least one ACTIVE room type, one ACTIVE rate plan and one inventory or rate row, otherwise 409 `HOTEL_INCOMPLETE` with `{"missing":["ROOM_TYPE","RATE_PLAN","INVENTORY"]}`; this stops empty shells reaching the admin. **Open decision D4**), check org ACTIVE (409 `ORGANIZATION_NOT_ACTIVE`), insert request, `UPDATE hotels SET publication_status='SUBMITTED'` (INITIAL only), insert decision `SUBMIT`.
Errors: 400 bad body; 403; 404 unknown hotel; 409 `INVALID_STATE_TRANSITION` (e.g. SUBMITTED, APPROVED, ARCHIVED hotel), 409 `REQUEST_ALREADY_OPEN` (unique index hit, catch the constraint violation and map), 409 `ORGANIZATION_NOT_ACTIVE`, 409 `HOTEL_INCOMPLETE`, 409 `VERSION_CONFLICT` (stale revision `expectedVersion`), 409 `REVISION_EMPTY` (revision identical to live content).

`GET /api/hotels/{hotelId}/publication-requests` (guard `requireHotelManage`) -> list newest first:
```json
[{ "id":"uuid","kind":"INITIAL","status":"REJECTED","submittedAt":"...","decidedAt":"...",
   "decision":{"decision":"REJECT","reason":"Adres eksik","actorRole":"ADMIN","createdAt":"..."} }]
```
The partner sees the decision `reason` and `decision` but never `actor_user_id` of the admin.

`POST /api/hotels/{hotelId}/publication-requests/{requestId}/withdraw` -> 200 same shape, status `WITHDRAWN`, hotel back to DRAFT (INITIAL) or revision back to DRAFT (CONTENT_CHANGE). 409 `INVALID_STATE_TRANSITION` if not SUBMITTED. `requestId` must belong to `hotelId`, else 404 (lookup is `id AND hotel_id`, R3).

### 4.2 Partner: content revision

Only for PUBLISHED or SUSPENDED hotels (sec. 3.3).

`GET /api/hotels/{hotelId}/content` (guard `requireHotelManage`) -> current working revision or live content:
```json
{ "hotelId":"uuid", "publicationStatus":"PUBLISHED", "contentVersion":2,
  "live": { "name":"...","city":"...","country":"TR","address":"...","starRating":4,"description":"...","latitude":null,"longitude":null },
  "revision": { "id":"uuid","revisionNo":3,"status":"DRAFT","version":1,"baseContentVersion":2, "content": { ...same keys... } } }
```
`revision` is null when none is open.

`PATCH /api/hotels/{hotelId}/content` (guard `requireHotelManage`)
```json
{ "expectedVersion": 1, "content": { "description": "Yeni aciklama", "starRating": 5 } }
```
- `content` is a partial patch over the working revision (or over live content when no revision exists yet, in which case `expectedVersion` must equal `contentVersion` and a revision row `DRAFT` is created with `revision_no = max+1`, prior open DRAFT impossible by the unique index).
- Response 200: the `revision` object above.
- Never touches the `hotels` row. Public readers keep seeing the previous content (acceptance: "taslak degisiklik public yanitta gorunmez").
- Errors: 400 unknown key or invalid value; 403; 404; 409 `VERSION_CONFLICT` (`expectedVersion` stale, body `{"currentVersion":N}`), 409 `HOTEL_LOCKED_FOR_REVIEW` (revision SUBMITTED), 409 `HOTEL_ARCHIVED`, 409 `USE_DIRECT_EDIT` (DRAFT/REJECTED).
- Child binding (R3): the revision is looked up by `hotel_id` taken from the path and `organization_id` from the hotel row, never from the body. A body `revisionId` is not accepted here.

`DELETE /api/hotels/{hotelId}/content/revision` discards a DRAFT revision (204; 409 if SUBMITTED, use withdraw). Optional, include only if cheap.

### 4.3 Admin: list and decide

Gateway ADMIN only (`X-User-Role: ADMIN`, same as `ReviewController.adminList`: services also check the header value, return 403 `FORBIDDEN` otherwise).

`GET /api/admin/hotel-publications?status=SUBMITTED&kind=&page=0&size=20` -> page:
```json
{ "items":[{ "requestId":"uuid","hotelId":"uuid","hotelName":"...","organizationId":"uuid","kind":"INITIAL","status":"SUBMITTED",
             "submittedBy":"uuid","submittedAt":"...","note":"...","hotelPublicationStatus":"SUBMITTED","version":0 }],
  "page":0,"size":20,"total":1 }
```
`status` defaults to SUBMITTED (the review queue). Also accepts `hotelId`. Items are ordered by `submittedAt` ascending for the queue.

`GET /api/admin/hotel-publications/{requestId}` -> request + `hotel` (live columns) + `revision` content (for CONTENT_CHANGE) + `diff` (list of `{field, from, to}`) + `decisions` history.

`POST /api/admin/hotel-publications/{requestId}/decisions`
```json
{ "decision": "APPROVE_AND_PUBLISH", "reason": null, "expectedVersion": 0 }
```
`decision` in `APPROVE | REJECT | APPROVE_AND_PUBLISH` for open requests. `reason` required (>= 5 chars) for REJECT. Response 200 = the request with `status` and `hotelPublicationStatus`.
- INITIAL APPROVE: hotel SUBMITTED -> APPROVED, request APPROVED. `APPROVE_AND_PUBLISH`: also APPROVED -> PUBLISHED, `published_at = now`.
- INITIAL REJECT: hotel -> REJECTED, request REJECTED.
- CONTENT_CHANGE APPROVE (and `APPROVE_AND_PUBLISH`, equivalent): in one transaction copy the revision `content` onto the `hotels` columns, revision -> APPLIED (`applied_at`), previous published revision -> SUPERSEDED, `hotels.published_revision_id` = this, `content_version + 1`; hotel status unchanged (PUBLISHED, or SUSPENDED if suspended). Hotel is re-checked: a stale `base_content_version` (live content changed since the revision was cut) -> 409 `STALE_REVISION`.
- CONTENT_CHANGE REJECT: revision -> REJECTED, request REJECTED, hotel and live content untouched.
- Any decision on a request that is not SUBMITTED: 409 `REQUEST_NOT_OPEN`. Stale `expectedVersion`: 409 `VERSION_CONFLICT`.
- Self review: if the deciding admin is also an active member of the request's organization, reject with 409 `SELF_REVIEW_NOT_ALLOWED` (check via `authAccessClient.check(adminId, orgId, null, "HOTEL_READ")`, fail closed). **Open decision D5**.

`POST /api/admin/hotels/{hotelId}/publication-status`
```json
{ "decision": "SUSPEND", "reason": "Sikayet incelemesi" }
```
`decision` in `PUBLISH` (from APPROVED), `SUSPEND`, `REINSTATE`, `ARCHIVE`. Reason required for all but PUBLISH. 200 returns the hotel with `publicationStatus`. 404 unknown hotel, 409 `INVALID_STATE_TRANSITION`. Writes a decision row with `request_id = NULL`.
(`PUBLISH` of an APPROVED hotel is the only way out of APPROVED when admin approved without publishing.)

Partner `ARCHIVE` of own DRAFT/REJECTED hotel: `POST /api/hotels/{hotelId}/archive {"reason":"..."}`, guard `requireHotelManage`, 409 for any other status.

### 4.4 Gateway

Checked `api-gateway/src/main/resources/application.yml`, route `hotel-service` Path list.
- **No change** for partner paths: `/api/hotels/**` already covers `/api/hotels/{id}/publication-requests`, `/content`, `/archive`.
- **Change needed** for admin: add `/api/admin/hotel-publications,/api/admin/hotel-publications/**,/api/admin/hotels/*/publication-status` to the `hotel-service` Path predicate. Without it `/api/admin/**` is matched by the `auth-service` route (`/api/admin/**`) and returns 404 from auth. The hotel-service route is declared before the auth route, which is why the existing `/api/admin/hotel-reviews` entries work, so keep it before.
- Verify `JwtAuthenticationFilter` treats these admin paths as authenticated (not in `isPublicCatalogBrowsePath`, `/api/hotels/**` GET is public browse: `GET /api/hotels/{id}/publication-requests` and `GET /api/hotels/{id}/content` are under that public prefix, so the gateway forwards them with a token if present (optional auth). The service guard `requireHotelManage` rejects missing identity with 401, so this is safe, but add a gateway negative test (anonymous GET -> 401 from the service) to `scripts/test-gateway-security.py`.)
- Spoofed `X-User-Role: ADMIN` must stay stripped by the gateway (existing test `test-gateway-security.py` line 98-100). Add one admin-publication path to that test.
- Same change must be applied to `application.yml` copies only if a docker profile duplicates routes. Grep showed only `api-gateway/src/main/resources/application.yml`; build/ copies are artifacts, do not edit.

## 5. Photos and media (phase 2, optional)

> Status 2026-11-24: implemented. Phase 2 (upload, review, public serving, soft delete) in V11/V12, API-DOCS 16.5.
> Phase 3 (a request carries `mediaIds`, approval of the request approves its still-pending photos, rejection leaves them
> pending) in V13, API-DOCS 16.9.

Phase 1 ships without photos. Reason: no hotel image field exists today (no `images`/`photos` column, `HotelResponse` has none), the car-rental storage code is document-oriented (PDF/JPEG/PNG, 10 MB, auth-only download), and there is no virus scanner anywhere in the stack. Building media doubles the slice. Phase 2 spec so it is not re-debated:

- Table `hotel_media(id, hotel_id, organization_id, kind PHOTO, sort_order int, caption varchar(200), rights_source varchar(30) CHECK in ('OWNED','LICENSED','PARTNER_ATTESTED'), rights_note varchar(500), storage_key uuid-name, content_type, size_bytes, width, height, scan_status CHECK in ('PENDING','CLEAN','REJECTED'), review_status CHECK in ('PENDING','APPROVED','REJECTED'), uploaded_by, created_at)`. Same RLS shape as `hotel_content_revisions`, delete only when not published.
- Types: JPEG, PNG, WebP by magic bytes (not the client content type, same approach as car `DocumentFiles.detectContentType`; add WebP, drop PDF). Max 5 MB per file, max 20 photos per hotel, min 800x600, max 8000x8000, EXIF stripped on store.
- Storage: reuse the car-rental approach (random UUID key, file on a volume, never user filename on disk). New volume `hotel_media` mounted `/data/hotel-media`, property `HOTEL_MEDIA_DIR`. Add the volume and env to the hotel-service entry in `docker-compose.yml` (the file the CLAUDE.md warns not to prune: add lines only).
- "Not public until approved": a photo is public only if `scan_status = 'CLEAN' AND review_status = 'APPROVED'` AND the hotel is PUBLISHED. There is no scanner, so `scan_status` becomes CLEAN only after magic byte plus dimension decode succeed server side, and `review_status` is set by the admin publication review (the same decision approves the photo set). A CONTENT_CHANGE request carries media ids; unapproved photos stay invisible while the previous approved set remains public.
- Serving: public `GET /api/hotels/{id}/media/{mediaId}` streams only public photos with `Cache-Control: public, max-age=300`. Partner `GET /api/hotels/{id}/media/{mediaId}/file` for any own photo (exact-object check `id AND hotel_id AND organization_id`). No signed URL needed in this slice (bytes are served by the service, not a bucket). Roadmap's "exact-object signed URL" applies when object storage arrives.
- Gateway: `/api/hotels/**` already covers it. Multipart size limit: `spring.servlet.multipart.max-file-size=5MB` for hotel-service.

## 6. Frontend

All data from the API, no fake badges (CLAUDE.md rule 6). Types in `frontend/src/lib/partner.ts` (`PartnerHotel`, `partnerApi`) and `frontend/src/lib/admin.ts`.

### 6.1 Partner (`frontend/src/app/partner/page.tsx`, the hotel cards list, and the hotel detail/edit area)
- `PartnerHotel` gains `publicationStatus` and `contentVersion`. The hotel card shows a status badge with Turkish labels: DRAFT "Taslak", SUBMITTED "Incelemede", APPROVED "Onaylandi, yayin bekliyor", PUBLISHED "Yayinda", REJECTED "Reddedildi", SUSPENDED "Askiya alindi", ARCHIVED "Arsivlendi". Plus the existing active/inactive state as a separate small marker ("Satis duraklatildi" when `active=false`). Never show "Yayinda" because the org is approved.
- `NewHotelForm` success message says the hotel was saved as a draft and is not visible to customers until reviewed (current copy must be checked, `createHotel` now returns DRAFT).
- "Yayin icin gonder" button on the card/detail, enabled for DRAFT and REJECTED and only for roles that can manage (`isOwner` or manager as the page already derives). Calls `partnerApi.submitPublication(hotelId, {note})`. Disabled with the reason text when the org is not ACTIVE (use the membership `organizationStatus` the page already has) and shows `HOTEL_INCOMPLETE.missing` as a checklist ("Oda tipi ekleyin" etc).
- REJECTED shows the admin `reason` from `GET .../publication-requests` (latest). SUBMITTED shows "Geri cek" (withdraw). PUBLISHED/SUSPENDED shows "Icerigi duzenle" opening a revision form (PATCH `/content`), a banner "Degisiklikler incelemeden sonra yayinlanir", the revision status and a "Incelemeye gonder" button (CONTENT_CHANGE). 409 `VERSION_CONFLICT` shows a reload prompt. SUSPENDED shows the reason read only.
- New API methods: `submitPublication`, `publicationRequests`, `withdrawPublication`, `getContent`, `patchContent`, `archiveHotel`.
- Existing edit form (`updateHotel`) must handle 409 `CONTENT_REQUIRES_REVISION` by switching to the revision form.

### 6.2 Admin
- New page `frontend/src/app/admin/hotel-publications/page.tsx` and nav item in `frontend/src/app/admin/layout.tsx`: `{ href: '/admin/hotel-publications', label: 'Otel Yayinlari', icon: 'storefront' }` (after `Isletmeler`). Pattern: copy `admin/car-documents/page.tsx` (list, filter by status tab, detail panel, approve, reject with required reason textarea) and the `isAdmin` guard used in `admin/reviews/page.tsx`.
- List columns: hotel name, organization id, kind (Ilk yayin / Icerik degisikligi), submitted at, status. Detail: hotel live fields, for CONTENT_CHANGE the `diff`, decision history. Buttons: "Onayla", "Onayla ve yayinla", "Reddet" (reason >= 5 chars, client validation matching server), and a separate hotel list action "Askiya al / Yeniden ac / Arsivle" with reason, calling `/api/admin/hotels/{id}/publication-status`. Loading, empty and error states shown. 409 shows the server message.
- `frontend/src/lib/admin.ts`: `adminApi.hotelPublications(status,page)`, `hotelPublication(id)`, `decideHotelPublication(id, body)`, `setHotelPublicationStatus(hotelId, body)`.
- The existing `admin/organizations` approve page should keep its copy, and add one line: "Isletme onayi oteli yayinlamaz, otel yayin onayi ayri verilir." (RM-17 requirement "onay rozetini yayin acik gibi sunma").

## 7. Migration impact list

After V10 + code, any script that creates a hotel via `POST /api/hotels` gets a DRAFT hotel that is not public, not searchable, not quotable. Backfilled rows are unaffected (sec. 2.3). Grep result (hotel creation sites), what each needs.

Shared helper to add in every Python script (one small function, copied or imported from a tiny new `scripts/_publish.py`, **open decision D6**: separate file vs inline copy; the current scripts are self contained, so inline copy is the default):

```
def publish_hotel(hotel_id, owner_token, admin_token):
    must POST /api/hotels/{id}/publication-requests {}          (owner)  -> requestId, requires org ACTIVE
    must POST /api/admin/hotel-publications/{requestId}/decisions {"decision":"APPROVE_AND_PUBLISH","expectedVersion":0}  (admin)
```
Sequence constraint: submit needs the org ACTIVE, so publish must come AFTER `POST /api/admin/organizations/{org}/approve`. Several scripts create the hotel before approving the org (see below), those need reordering of the two steps, not only the new call. Submit also requires `HOTEL_INCOMPLETE` to pass: room type, rate plan and inventory or rate must exist first, so publish after the room-type/rate-plan/inventory setup.

| File and line | Creates hotel (org) | Expects | Needs |
|---|---|---|---|
| `scripts/test-public-projection.py:88` | hotel under PENDING org, asserts hidden then (later) visible after approve | the visibility story itself | Rewrite assertions: PENDING org + DRAFT hidden; after org approve still hidden (new assertion, R1); after submit still hidden; after admin publish visible in list/search/GET id; SUSPEND hides, REINSTATE shows; deactivate hides. It is the main R1 live test. It has no admin login today (no approve call found), add one |
| `scripts/test-quote-hold.py:88` (approve at :106) | hotel, approves org at 106 | quote and hold succeed | publish after org approve and after room/rate/inventory setup, before first quote. Add assertion: quote on DRAFT hotel -> 422 `HOTEL_NOT_BOOKABLE` |
| `scripts/test-order.py:117` | hotel, 1 approve | order flow | publish after org approve |
| `scripts/test-package-order.py:48` | hotel, 1 approve | package order flow | publish after org approve |
| `scripts/test-cancellation.py:58` | hotel, 2 approve calls | cancel flow | publish after org approve |
| `scripts/test-payments.py:110` | hotel, 2 approve calls | payment flow | publish after org approve |
| `scripts/test-reviews.py:83` | `empty_hotel` only | `GET /hotels/{id}/reviews` on a hotel with no reviews | only needs it if it reads through `GET /api/hotels/{id}` as anonymous (then publish), else unchanged. It authenticates as owner so the member path works without publishing. Decide when implementing, a publish is safest for the public review read |
| `scripts/test-rls.py:79` | `hotel` fixtures for orgs A and B | RLS isolation | publication tables: add checks that org B cannot read/insert A's requests/revisions/decisions, public mode sees none, decisions cannot be updated or deleted (I2 below) |
| `scripts/test-hotel-calendar.py:87` (88 hits, hotel API heavy) | hotel for calendar, no public search | partner calendar/inventory only | partner and admin-only paths do not need PUBLISHED. Check if it calls quotes or availability publicly, if so publish. Grep for `/availability`, `/quotes` before changing |
| `scripts/test-gateway-security.py:67-100,` `scripts/test-observability.py:47-52` | no create, GET `/api/hotels` only | status 200 and headers | unchanged. Add the new admin path + spoofed header case to gateway test |
| `scripts/test-car-order.py`, `test-flight-order.py`, `test-hold-expiry.py`, `test-confirm-failure.py`, `test-partner-panel.py`, `test-outbox.py`, `test-traffic-sim.py`, `test-crm-link.py` | none matched `/api/hotels` creation in this grep (grep matched only the 11 files above for `/api/hotels`) | n/a | `test-hold-expiry.py`, `test-confirm-failure.py`, `test-partner-panel.py`, `test-traffic-sim.py` reach hotels through other modules (helpers or shared fixtures), **re-grep for `quote`/`hotel-holds`/`roomTypeId` in them at implementation time**: any script that books a hotel needs a PUBLISHED hotel |
| `scripts/seed-data.sh:249-268` `create_hotel()` | 8 hotels in a seed org (admin approves the org earlier, SEED admin login exists at lines 22-47) | demo data visible on the site, line 486 prints the public count | after the room/rate setup add, per hotel, submit + admin `APPROVE_AND_PUBLISH` (`post`, admin token helpers exist in the script). The idempotent skip path (`existing` hotel) must also publish if status is not PUBLISHED: read `publicationStatus` from `GET /api/hotels?organizationId=` and skip when PUBLISHED. Line 486 public count stays meaningful only after publish |
| `scripts/ui-fixture.py:87-100` | `UI Test Hotel`, org approved right after at :100 | public hotel for UI/mobile tests | after org approve and after the room/rate/inventory the script sets up later, add submit + admin publish. Reuse `owner_tok`, `admin_tok` already present. Keep state file keys |
| `frontend/e2e/global-setup.ts:24-31` | `E2E Hotel`, org approved at :25, room type, rate plan, inventory, rates created at :28-33 | customer search finds the hotel (`01-customer-hotel.spec.ts`) | after line ~33 (rates PUT) add `must('POST', /api/hotels/${hotelId}/publication-requests, {}, hp)` then admin `/api/admin/hotel-publications/{id}/decisions` with `APPROVE_AND_PUBLISH`. Needs `requestId` from the response. The commission-rate call can stay where it is |
| `frontend/e2e/01-customer-hotel.spec.ts`, `04-admin.spec.ts` | read the fixture | search/booking, admin pages | `01` works after global-setup publishes. `04-admin` should gain a case that opens `/admin/hotel-publications` (page exists, queue renders, decide flow with a second fixture hotel left SUBMITTED, sec. 8 E2) |
| `mobile/e2e/mobile-web.spec.ts` | none, uses `ui-fixture.py` state (`s.hotelName`, `s.hotelCity`) | search hit at line 32 | no code change, but it breaks unless `ui-fixture.py` publishes. List as dependent on the ui-fixture change |
| `frontend/src/**` (public pages `hotels/*`, `lib/hotel-stay.ts`) | consumers only | public API | no change, they get fewer results until published |

Legacy and other consumers that read hotels, to double check but not change: `auth-service` `RoleGrantService` uses `/internal/hotels/{id}` (unchanged, unfiltered), package-booking-service hotel order path goes through quote/hold (gated by sec. 3.4), crm/loyalty/pricing do not read hotels (grep before merge: `agentgrep "hotel-service" --type java -l`).

Deployment order: V10 runs at hotel-service start and all existing rows are PUBLISHED, so deploying hotel-service first changes nothing visible. Then deploy the gateway route and frontend. The scripts only need updating before their next run. Rollback: V10 is additive, old code ignores the new columns and keeps showing everything PUBLISHED or not, which re-opens unreviewed hotels created after V10 (they would be DRAFT rows the old code shows). Accept, state in the release note, no down migration.

## 8. Acceptance tests

Naming: U = unit (Mockito, constructors passed to the real constructor, one constructor per bean), I = Testcontainers/Postgres integration (the existing `tenant/TenantRlsPoolLeakIntegrationTest` pattern), L = live script, E = Playwright. R-ids from PRODUCT-ARCHITECTURE sec. 11.

### 8.1 Unit (hotel-service)

| ID | Test | Asserts | R |
|---|---|---|---|
| U1 | `HotelPublicationServiceTest` full transition matrix | every allowed row of sec. 1.1 succeeds with one decision row (actor, from, to, reason), every other (from, command) pair throws `INVALID_STATE_TRANSITION`. Table driven over all 7 x commands | R1 |
| U2 | reason rules | REJECT/SUSPEND/REINSTATE/ARCHIVE with null or 4 char reason -> 400; APPROVE without reason ok | R1 |
| U3 | submit gates | org not active -> `ORGANIZATION_NOT_ACTIVE`; auth down -> 503 fail closed; no room type -> `HOTEL_INCOMPLETE` listing missing; second open request -> `REQUEST_ALREADY_OPEN` | R1 |
| U4 | `HotelServiceImplTest.filterPublic` | DRAFT, SUBMITTED, APPROVED, REJECTED, SUSPENDED, ARCHIVED hidden; PUBLISHED+active+ACTIVE org shown; PUBLISHED+inactive hidden; PUBLISHED+org not active hidden; PUBLISHED+auth down hidden but legacy (org NULL) PUBLISHED still shown; legacy DRAFT hidden | R1, R10 |
| U5 | `QuoteServiceImplTest` | quote on non PUBLISHED -> 422 `HOTEL_NOT_BOOKABLE` for each non PUBLISHED status, message order inactive/not published/org | R1 |
| U6 | `AvailabilityServiceImplTest` | non PUBLISHED returns the same not-available shape as inactive | R1 |
| U7 | `HotelHoldServiceImplTest.hold` | quote exists, hotel since SUSPENDED -> `HOTEL_NOT_BOOKABLE`, no inventory held | R1 |
| U8 | `HotelHoldServiceImplTest.confirm/release` | hold created, hotel then SUSPENDED/ARCHIVED, confirm and release still succeed | R1 (suspension keeps existing rez) |
| U9 | content revision service | PATCH on PUBLISHED creates DRAFT revision and leaves hotel row byte-equal; stale `expectedVersion` -> 409 with `currentVersion`; unknown key -> 400; DRAFT hotel -> `USE_DIRECT_EDIT`; second revision while one open reuses it; apply on approve copies fields, bumps `content_version`, supersedes previous; reject keeps live; `STALE_REVISION` when base version moved | R1, R3 |
| U10 | `HotelControllerTest` | `PUT` on PUBLISHED -> `CONTENT_REQUIRES_REVISION`; organizationId change -> 400; delete with history -> 409; partner of org B on org A hotel: submit/content/withdraw -> 403 (guard mocked deny), requestId of another hotel -> 404; admin endpoints with role USER or missing -> 403 | R3 |
| U11 | `HotelAccessGuardTest` | new `requireReadable`: non member + unpublished -> 404, member + unpublished ok, ADMIN ok, auth outage fail closed | R1, R3 |
| U12 | mapper | `HotelRequest` cannot set status, `HotelResponse` carries `publicationStatus` | R1 |

### 8.2 Integration (Testcontainers Postgres 16, Flyway V1..V10)

| ID | Test | Asserts | R |
|---|---|---|---|
| I1 | V10 backfill (`V10MigrationIT`) | migrate to V9, insert 3 hotels (legacy org NULL active, org active, org inactive), migrate V10: all 3 PUBLISHED, `active` untouched, one `MIGRATION` decision each, `COUNT(*) WHERE organization_id IS NULL` equal before/after, all legacy PUBLISHED; CHECK rejects `publication_status='X'`; partial unique index rejects second SUBMITTED request and second working revision; decision CHECK rejects REJECT without reason | R10 |
| I2 | RLS on new tables, extend `TenantRlsPoolLeakIntegrationTest` pattern | as `waylo_app`: partner A sees only A rows in requests/revisions/decisions; public mode sees zero rows in all three; partner A cannot insert for org B (WITH CHECK fails); `UPDATE`/`DELETE` on `hotel_publication_decisions` affects 0 rows / denied for every mode incl. admin; context does not leak across pooled connections after a partner transaction; legacy decisions (org NULL) visible to admin/service only | R3, R10 |
| I3 | partial index plan | `EXPLAIN` not required; just the listing query returns correct rows | - |

### 8.3 Live scripts

Extend `scripts/test-public-projection.py` (R1) and add `scripts/test-hotel-publication.py` (new file, same helpers/style as other test scripts, creates its own users, orgs, hotels, cleans by deactivating). Needs admin login (existing scripts show how: `test-quote-hold.py` admin token).

| ID | Scenario | Expected | R |
|---|---|---|---|
| L1 | register owner, create org, admin approves org, create hotel | 201, hotel `publicationStatus=DRAFT`; anonymous `GET /api/hotels`, `/search?city=`, `/{id}` -> not listed / 404; owner `GET /{id}` 200 | R1 |
| L2 | anonymous `GET /api/hotels/{id}/room-types`, `/rate-plans`, `/availability`, `POST /api/quotes` as customer on the DRAFT hotel | 404 / not-available / 422 `HOTEL_NOT_BOOKABLE` | R1 |
| L3 | submit without room type | 409 `HOTEL_INCOMPLETE` with `missing`; with PENDING org (separate org) 409 `ORGANIZATION_NOT_ACTIVE` | R1 |
| L4 | add room type, rate plan, inventory, rates; submit | 201 SUBMITTED; hotel `SUBMITTED`; still hidden publicly; second submit 409 `REQUEST_ALREADY_OPEN`; owner `PUT` -> 409 `HOTEL_LOCKED_FOR_REVIEW` | R1 |
| L5 | admin list status=SUBMITTED contains it; non admin token on the admin routes | list has it; 403 `FORBIDDEN`; spoofed `X-User-Role: ADMIN` without JWT -> 401 | R1, R3 |
| L6 | admin REJECT without reason, then with | 400, then 200; owner sees reason via `GET publication-requests`; hotel `REJECTED`; still hidden; owner edits via PUT (allowed) and resubmits | R1 |
| L7 | admin `APPROVE_AND_PUBLISH` | hotel `PUBLISHED`; anonymous list, search, GET id show it; availability + quote + hold + order work; decisions table has SUBMIT, REJECT, SUBMIT, APPROVE, PUBLISH (check through admin detail endpoint) | R1 |
| L8 | owner PATCH content on PUBLISHED (`description`) | 200 revision DRAFT; public `GET` still old description; stale `expectedVersion` -> 409; `PUT` -> 409 `CONTENT_REQUIRES_REVISION`; submit CONTENT_CHANGE; admin reject with reason -> public old content still, request REJECTED; new revision, approve -> public new description, `contentVersion` +1 | R1, R3 |
| L9 | admin SUSPEND (reason) | public list/search/GET hide it; new quote -> 422 `HOTEL_NOT_BOOKABLE`; a hold/order created before suspension still `confirm`s and appears in partner order list; owner can still read it; REINSTATE shows it again | R1 |
| L10 | org owner B (other org) vs hotel of A | `POST/GET publication-requests`, `PATCH/GET content`, `archive`, `withdraw` with A's `hotelId` -> 403; B's own `requestId` against A's hotel path -> 403/404; B's hotel id with A's request id -> 404; no row changed (admin detail unchanged); `GET /api/hotels?organizationId=A` as B returns only PUBLISHED of A | R3 |
| L11 | org suspended by admin after hotel published | hotel disappears from public, hotel row status still PUBLISHED; org re-approved -> visible again with no hotel decision needed | R1 |
| L12 | `DELETE /api/hotels/{id}` on a hotel with history | 409 `HOTEL_HAS_HISTORY`; ARCHIVE then hidden, and `GET /internal/hotels/{id}` (service token) still returns it | R1 |
| L13 | legacy (R10) | before deploying V10 record `GET /api/hotels` ids (all with org NULL and active) and counts; after V10 and restart: same ids returned publicly, count unchanged; `PUT` on a legacy hotel by any partner -> 403 as before; admin `SUSPEND` on a legacy hotel hides it (then REINSTATE it back to leave data as found) | R10 |
| L14 | old mutation URLs cannot bypass (R10) | `POST /api/hotels/{id}/activate` on a DRAFT hotel does not make it public; `POST /internal/hotel-holds` with quote of a non PUBLISHED hotel is impossible to create (quote refused), a forged quote id -> 404 | R1, R10 |
| L15 | `test-rls.py` extension | owner of org B using A's ids sees none of A's publication rows (via API: 403/404), direct DB check of three tables as `waylo_app` is covered by I2 and is not repeated live | R3 |

### 8.4 E2E (Playwright, `frontend/e2e`)
- E1: global-setup publishes the fixture hotel, `01-customer-hotel.spec.ts` unchanged and passes.
- E2: partner page shows "Taslak" badge for a freshly created hotel, "Yayin icin gonder" disabled with reason when org not active, enabled after, badge becomes "Incelemede". Admin opens `/admin/hotel-publications`, sees the request, rejects with reason (button disabled while reason < 5 chars), partner sees "Reddedildi" and the reason; resubmit, admin "Onayla ve yayinla", badge "Yayinda", customer search finds it.
- E3: partner edits a published hotel description, banner shown, public page keeps old text until admin approves.

### 8.5 Gates
`./gradlew :hotel-service:test` and the Postgres integration task, `python3 scripts/check-bean-constructors.py`, `python3 scripts/check-yaml-duplicates.py` (gateway yml edit), frontend `tsc` and lint, live scripts L1..L15 against the compose stack. The report for the slice lists the command, environment, observed output per requirement id, as the architecture doc demands.

## 9. Open decisions for the user

Defaults below are what the implementer should do unless told otherwise.
- D1. Hotels of PENDING/SUSPENDED orgs that exist before V10 are backfilled PUBLISHED (hidden today by org status, visible once org is ACTIVE with no hotel review). Default: accept (dev/test data only). Alternative: a one-off pre-deploy script that sets them DRAFT.
- D2. `HOTEL_STAFF` cannot submit or edit content (only `HOTEL_MANAGE` holders). Default: yes.
- D3. Should the public `GET /api/hotels/{id}/reviews` also 404 for non public hotels? Default: yes via the same helper (cheap, consistent). Say no to keep today's behaviour.
- D4. Is the completeness gate (>= 1 room type, rate plan and inventory/rate before submit) wanted? Default: yes.
- D5. Block an admin who is also a member of the org from deciding its hotel (`SELF_REVIEW_NOT_ALLOWED`)? Default: yes.
- D6. Shared `scripts/_publish.py` helper or inline copies in each script? Default: inline, matches the self contained style.
- D7. Photos phase 2 as a separate slice. Default: yes (sec. 5).
- D8. Keep `/api/hotels/...` paths instead of the roadmap's `/api/v1/partner/organizations/{orgId}/hotels/{hotelId}/...`. Default: keep.

## 10. Files the implementation will touch

hotel-service: `src/main/resources/db/migration/V10__hotel_publication.sql` (new); `domain/Hotel.java`, `domain/PublicationStatus.java` (new); `publication/*` (new: 3 entities, 3 repositories, `HotelPublicationService`, partner and admin controllers, DTO records); `dto/HotelResponse.java`, `dto/HotelMapper.java`; `controller/HotelController.java`; `controller/RoomTypeController.java`, `RatePlanController.java`, `RoomController.java`, `RateController.java`, `AvailabilityController.java` (read guard); `security/HotelAccessGuard.java`; `service/HotelServiceImpl.java`, `QuoteServiceImpl.java`, `AvailabilityServiceImpl.java`, `HotelHoldServiceImpl.java`; tests under `src/test/.../publication`, `service`, `security`, `controller`, `tenant`, plus a V10 migration IT.
api-gateway: `src/main/resources/application.yml` (route paths), `scripts/test-gateway-security.py`.
frontend: `src/lib/partner.ts`, `src/lib/admin.ts`, `src/app/partner/page.tsx`, `src/app/admin/layout.tsx`, `src/app/admin/hotel-publications/page.tsx` (new), `src/app/admin/organizations/page.tsx` (one copy line), `e2e/global-setup.ts`, `e2e/04-admin.spec.ts`, a new e2e spec.
scripts: `test-public-projection.py`, `test-quote-hold.py`, `test-order.py`, `test-package-order.py`, `test-cancellation.py`, `test-payments.py`, `test-reviews.py`, `test-rls.py`, `test-hotel-calendar.py` (if it uses public quotes), `seed-data.sh`, `ui-fixture.py`, new `test-hotel-publication.py`.
Optional phase 2: `docker-compose.yml` (volume line only), hotel-service media classes.
Not touched: auth-service (no new permission), package-booking-service, payment, mobile code.
