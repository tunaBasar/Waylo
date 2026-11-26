# Partner payout contract (RM-31 finance slice: paying partners what they earned)

Binding for payment-service (Flyway V9), api-gateway route, auth-service route allowlist, notification-service, frontend partner and admin pages, and the live script. Sources: PRODUCT-ARCHITECTURE.md sec. 5 (append-only ledger, estimated and realized revenue separate, merchant-of-record is an open business decision, this document is no legal approval), WAYLO-MVP-ROADMAP.md RM-31 (finance ledger, reconciliation, real KPI binding), CANCELLATION-CONTRACT.md, CLAUDE.md "Agent working rules" (simplicity, surgical, one constructor per Spring bean, no fake data in the UI). All JSON uses the existing `ApiResponse` envelope, errors the existing `ErrorResponse` (`{status, error, message, path}`, `error` is the code). Money is `NUMERIC(12,2)` + `CHAR(3)` currency, `RoundingMode.HALF_EVEN`, never floats. Timestamps are `timestamptz`.

Scope decisions (sec. 10 lists what the user may change):
- No real money movement. A platform admin sends the bank transfer outside the system and marks the payout PAID with a bank reference. No provider call is made. `PaymentProvider` is not touched.
- Payouts are balance based, not item based: a payout takes an amount out of the per organization and currency payable balance. It does not pin escrow items.
- All new code lives in payment-service. One edit each in api-gateway, auth-service (allowlist) and notification-service.

## 1. Current state (evidence)

What exists:
- Escrow: `EscrowItem` per order item with snapshots `commission_rate`, `commission_amount`, `supplier_amount` and `currency` (`payment-service/.../domain/EscrowItem.java:21-83`), unique `order_item_id`, `supplier_organization_id`, status `HELD -> RELEASED | REFUNDED`, both terminal (`EscrowStatus.java:3-4`). `supplier_amount` is already net of commission: `commission_amount + supplier_amount = gross_amount` (or `charged_amount` when the PARTNER funds the discount) (`V2__escrow_ledger_core.sql` chk_escrow_split, replaced in `V5__order_item_discounts.sql:12-13`). So "released minus commission" is exactly `SUM(supplier_amount)`. Commission is never recomputed after capture.
- Release triggers, all through `EscrowService.doRelease` which sets `status=RELEASED`, `settlement_reason`, `released_at` (`EscrowService.java:56-77`, lines 71-73):
  - `release` (QR scan REDEMPTION), called from `package-booking-service VoucherRedemptionService.java:114` (voucher flips to REDEEMED in the same transaction, a failure rolls it back).
  - `cancel` with NO_REFUND policy gives `CANCEL_NO_REFUND` (`EscrowService.java:130-131`, caller `OrderServiceImpl.java:644`).
  - `noShow` gives `NO_SHOW` (`EscrowService.java:138-143`, caller `NoShowService`).
  - `TICKETING` exists in the enum (`ReleaseTrigger.java`) but nothing yields it (`PaymentIntentService.java:225-226`).
  Check-out is NOT a trigger anywhere. Hotels release on QR scan, not on check-out.
- Refund after release is impossible today: `doRefund` throws 422 `ESCROW_ALREADY_RELEASED` (`EscrowService.java:86-88`) and `cancel` throws the same (`:120-125`). Seller money is never clawed back (CANCELLATION-CONTRACT.md sec. 2). There is therefore no row, state or ledger entry for "refund after release".
- Ledger: append-only `ledger_entries`, UPDATE, DELETE and TRUNCATE rejected by trigger (`V2__escrow_ledger_core.sql`, `LedgerEntryRepository.java` blocks deletes). `LedgerService.recordRelease` writes `DR CUSTOMER_FUNDS_HELD`, `DR PLATFORM_DISCOUNT_COST`, `CR SUPPLIER_PAYABLE`, `CR PLATFORM_COMMISSION` (`LedgerService.java:41-60`). `LedgerAccount.SUPPLIER_PAID` exists but is a placeholder, nothing writes it (`LedgerAccount.java:3-10`). `ledger_entries.order_id` is `NOT NULL` and there is no organization column, so a payout (not tied to one order) cannot be written there. Payouts need their own append-only table (sec. 5).
- Partner earnings: `GET /api/partner/organizations/{organizationId}/earnings` (`PartnerEarningsController.java:75-92`). `PartnerEarningsService.earnings` checks `AuthAccessClient.check(userId, orgId, "RESERVATION_READ")`, a denied decision gives `AccessDeniedException` 403 (`:36-40`), fails closed with 503 through `FeignException` if auth is down. It sums `supplier_amount` per status into `totals {held, released, refunded}` and takes `currency` from the FIRST item only (`:48`), so it is wrong for multi-currency and it ignores payouts. It filters by `held_at`, not `released_at`. It is estimated-plus-realized mixed in one response: `held` is estimated, `released` is realized.
- `supplier_accounts` and `SupplierBalanceController` are the platform's PREPAID balance at external FLIGHT suppliers (`SupplierAccount.java:13-14`, `V4__supplier_accounts.sql`, `/api/admin/supplier-accounts`, `/internal/supplier-payouts`). Despite the name `payout` they have nothing to do with partner payouts (they debit a prepaid airline balance at ticketing). Do not reuse or rename them. Name new things `partner payout`.
- `supplier_payout_accounts` (`SupplierPayoutAccount.java`, `V2`): maps an organization to a provider sub-merchant key (iyzico Pazaryeri). Provider routing, not a bank account. Not reused for IBAN.
- Admin finance: `AdminFinanceController` has commission-rates and order ledger only (`:140-185`). No payout, no balance endpoint. Admin check helper is `requireAdmin(userId, role)` with `X-User-Role = ADMIN`.
- Provider: `PaymentProvider` is escrow-shaped (`authorize`, `releaseItem`, `refundItem`, `cancel`). `releaseItem` moves money to the sub-merchant at provider side in a real marketplace. TestPaymentProvider is a stub. There is no bank-transfer concept.
- Outbox: `outbox_event` (V7, V8), `OutboxWriter.write` is `Propagation.MANDATORY` (`OutboxWriter.java:35`), topic `payment-events`, type tokens in `KafkaProducerConfig.TYPE_MAPPINGS` (`:30-31`, only `paymentCompleted`, `paymentRefunded`). notification-service `PaymentEventListener` logs only and dispatches by `instanceof` (`PaymentEventListener.java:30-47`), `KafkaConsumerConfig.TYPE_MAPPINGS` must hold the same tokens (`:36-50`).
- Gateway: payment-service route Path list is `/api/payments/**,/api/partner/organizations/*/earnings,/api/admin/commission-rates,/api/admin/ledger,/api/admin/supplier-accounts,/api/admin/supplier-accounts/**` (`api-gateway/src/main/resources/application.yml:66-69`). It precedes the auth-service route that catches `/api/admin/**` (`:82-85`). Gateway injects `X-User-Id`, `X-User-Email`, `X-User-Role` (`JwtAuthenticationFilter.java:166-174`).
- Frontend: `frontend/src/app/partner/earnings/page.tsx` (89 lines) shows held, released, refunded totals plus a static text "Odeme takvimi henuz tanimlanmadi" (line 39) and a single `currency`. `frontend/src/app/admin/supplier-balance/page.tsx` is the flight prepaid page. Admin nav is `frontend/src/app/admin/layout.tsx` (list at lines 7-18), partner nav `frontend/src/app/partner/layout.tsx:108,124,134`. API wrappers `frontend/src/lib/partner.ts:384-418`, `frontend/src/lib/admin.ts`. API docs: `API-DOCS.md:1185-1230`.
- Tests that exist: `PartnerEarningsServiceTest`, `LedgerImmutabilityTest`, `PaymentFlowTest`, `SupplierBalanceServiceTest`, `ControllerAuthTest`, `OutboxTest` (payment-service/src/test), live `scripts/test-payments.py` (helpers `req`, `must`, `psql`, `register`) and `scripts/test-cancellation.py`.

What is missing: payable balance, any payout entity or state machine, payout ledger, bank account storage, partner and admin payout endpoints and pages, payout notifications, the gateway routes, a way to correct released money (chargeback, dispute) other than editing data, multi-currency correct earnings, and the finance-only permission (the only permission today is `RESERVATION_READ`, which `HOTEL_STAFF` also holds, `AccessService.java:96-98`).

## 2. Payable balance

Unit: one balance per `(supplier_organization_id, currency)`. A currency is never converted or mixed. Every response lists a balance per currency.

Definitions, for organization `:org`, currency `:cur`, cutoff `:cutoff = now() - holdbackDays` (`waylo.payouts.holdback-days`, default 0):

```
eligibleReleased = SUM(supplier_amount) of escrow_items
                   WHERE supplier_organization_id = :org AND currency = :cur
                     AND status = 'RELEASED' AND released_at <= :cutoff
delta            = SUM(payable_delta) of payout_ledger WHERE organization_id = :org AND currency = :cur
payable          = eligibleReleased + delta          -- delta is negative or zero normally
```

`delta` carries everything after release, so the formula "released minus commission, minus refunds after release, minus payouts" is: commission is already excluded in `supplier_amount`, refunds after release are `ADJUSTMENT_DEBIT` rows (sec. 3.5), payouts are `RESERVE` rows (negative) undone by `RESERVE_RELEASE` (positive) when a payout fails or is cancelled. PAID keeps its `RESERVE` (payable stays reduced).

JPQL (repository `EscrowItemRepository`, new methods):

```java
@Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
       "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.RELEASED " +
       "and e.releasedAt <= :cutoff group by e.currency")
List<Object[]> eligibleReleasedByCurrency(UUID orgId, Instant cutoff);

@Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
       "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.RELEASED " +
       "and e.releasedAt > :cutoff group by e.currency")
List<Object[]> releasedInHoldbackByCurrency(UUID orgId, Instant cutoff);

@Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
       "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.HELD group by e.currency")
List<Object[]> heldByCurrency(UUID orgId);
```

Plain SQL for the delta (`PayoutLedgerRepository`, JPQL equivalent `select l.currency, coalesce(sum(l.payableDelta),0) from PayoutLedgerEntry l where l.organizationId = :orgId group by l.currency`):

```sql
SELECT currency, COALESCE(SUM(payable_delta), 0) FROM payout_ledger WHERE organization_id = :org GROUP BY currency;
```

Response fields per currency (all `NUMERIC(12,2)`):
- `payable` = eligibleReleased + delta, floored at 0 for display and for requests. If the raw value is negative, `negative = true` and `owed` shows the absolute value (platform claims it back from the next earnings). Requests are rejected while `payable <= 0`.
- `releasedNotYetPayable` = releasedInHoldback (realized but inside the holdback window).
- `held` = heldByCurrency (ESTIMATED, still in escrow, may be refunded). Never added to `payable`. UI labels it as estimated.
- `reserved` = `-SUM(payable_delta)` of payouts in REQUESTED, APPROVED, PROCESSING (open money).
- `paidTotal` = `SUM(amount)` of payouts PAID.
Invariant (check in tests and in the admin reconciliation view): `eligibleReleased + releasedNotYetPayable = SUM(supplier_amount) RELEASED = SUM(credit_amount) of ledger_entries WHERE account='SUPPLIER_PAYABLE'` joined on `order_item_id` of that organization's items (`ledger_entries` has no organization column, join through `escrow_items.order_item_id`).

Timing rules:
- An item becomes payable when `status = RELEASED` and `released_at <= now - holdbackDays`. The trigger is whatever calls `doRelease` today (QR redemption, non-refundable cancel, no-show). Check-out is not a trigger (sec. 1). Do not add one in this slice.
- The balance is computed at request time inside the lock (sec. 3.4). Releases that commit later only increase the balance and are covered by the next payout.
- Commission timing: the snapshot taken at capture is the only commission. A commission rate change after capture never changes a payable amount (`commission_rates` rows are versions, `V2`). A payout never recomputes commission.
- Items `REFUNDED` never enter the balance. `HELD` never enters the balance.
- `released_at` is set only by `doRelease` (`EscrowService.java:73`). A RELEASED row with `released_at IS NULL` is a data defect and is excluded by the `<=` predicate. Add `CHECK (status <> 'RELEASED' OR released_at IS NOT NULL)` in V9 only if existing rows satisfy it (implementation runs `SELECT count(*) ... WHERE status='RELEASED' AND released_at IS NULL` first, and backfills `released_at = held_at` if any).
- Existing escrow data before this feature is paid out for the first time by the first request. No backfill of ledger.

## 3. Payout lifecycle

### 3.1 States and transitions

`partner_payouts.status`: `REQUESTED -> APPROVED -> PROCESSING -> PAID | FAILED`, plus `CANCELLED`.

| # | From | To | Actor | Required input | Ledger rows written (append-only) |
|---|---|---|---|---|---|
| P1 | (new) | REQUESTED | partner (org OWNER role, sec. 3.2) | amount, currency, `Idempotency-Key` | `RESERVE` (`payable_delta = -amount`) |
| P2 | REQUESTED | APPROVED | admin | none | none (decision row in `payout_events`) |
| P3 | APPROVED | PROCESSING | admin | none ("bank transfer started") | none |
| P4 | PROCESSING | PAID | admin | `reference` (bank transfer ref, 3..100 chars, unique), optional `paidAt` (default now, not in the future, not before the minute of `requestedAt`: 400 `PAID_AT_BEFORE_REQUEST`) | `PAID` legs: `DR SUPPLIER_PAYABLE amount`, `CR SUPPLIER_PAID amount`, `payable_delta = 0` |
| P5 | PROCESSING | FAILED | admin | `reason` (5..500) | `RESERVE_RELEASE` (`payable_delta = +amount`) |
| P6 | REQUESTED | CANCELLED | partner (own org, owner role) or admin | partner: none. admin: `reason` | `RESERVE_RELEASE` |
| P7 | APPROVED | CANCELLED | admin only | `reason` (5..500) | `RESERVE_RELEASE` |

Admin "reject" of a request is P6/P7 with `cancelledBy = ADMIN` and a reason. There is no REJECTED state. Anything not in the table is 409 `INVALID_STATE_TRANSITION` (body carries `from`, `to`). `PAID`, `FAILED`, `CANCELLED` are terminal. A FAILED payout is retried by the partner or admin creating a NEW payout (new row, new idempotency key). Repeating a transition whose target is already the current status returns 200 with the current row and writes nothing (idempotent). A mismatch on optimistic `version` (JPA `@Version`) is 409 `VERSION_CONFLICT`.

Every transition writes one `payout_events` row (`payout_id`, `from_status`, `to_status`, `actor_user_id`, `actor_type PARTNER|ADMIN|SYSTEM`, `reason`, `reference`, `occurred_at`). Both tables carry the same append-only triggers as `supplier_ledger` (`V4`). The `payout_events` row and the ledger rows commit in the same transaction as the status change.

### 3.2 Who

- Platform admin = `X-User-Role: ADMIN` at the gateway (`requireAdmin`, same helper as `AdminFinanceController`). Admin approves, starts, marks paid or failed, cancels, verifies bank accounts, posts adjustments.
- Partner read (balance, list, detail): `AuthAccessClient.check(userId, orgId, "RESERVATION_READ")` allowed, exactly as `PartnerEarningsService.earnings` does (denied or null decision gives 403 `ACCESS_DENIED`, auth down gives 503 `DEPENDENCY_UNAVAILABLE`).
- Partner write (request, cancel own, bank account PUT and DELETE): the same check AND `decision.roles()` contains `ORGANIZATION_OWNER` (`AccessDecision(boolean allowed, List<String> roles)`, `AuthAccessClient.java:73`). This avoids adding a permission to auth-service (HOTEL_STAFF and HOTEL_MANAGER hold RESERVATION_READ and must not move money). A non-owner member gets 403 `PAYOUT_OWNER_ONLY`.
- The organization must be ACTIVE for a partner request: payment-service asks auth for `GET /internal/organizations/active-ids` (exists, `InternalOrganizationController.java:35`), fail closed. Otherwise 409 `ORGANIZATION_NOT_ACTIVE`. This needs a new Feign method on `AuthAccessClient` and `payment-service` added to the `callers` of `/internal/organizations` in `auth-service/src/main/resources/application.yml:69-70` (and the same entry in any config-service copy of that file, implementation greps `config-service` for `/internal/organizations`).
- Cross-organization: the organization id comes from the path and is authorized by the access check. A payout id that belongs to another organization is 404 `NOT_FOUND` (never 403, no existence leak) on partner endpoints. A partner reading another org's path gets 403 from the access check.
- Segregation of duties (open decision 5): by default one admin may approve and mark paid. The data model records both actors so a four-eyes rule can be switched on later (`waylo.payouts.require-different-admins`, default false).

### 3.3 Amounts and idempotency

- Single currency per payout: `partner_payouts.currency CHAR(3)`. One request pays one currency. Payable is checked in that currency only.
- Supported payout currencies: `waylo.payouts.minimum-amount.{CUR}` map, default `TRY: 100.00`. A currency without an entry gets 422 `PAYOUT_CURRENCY_NOT_SUPPORTED` (the IBAN is Turkish, so only TRY is payable by default, open decision 2). Its balance is still shown.
- `amount` is `0.01..` with scale 2, `minimum <= amount <= payable`. Below minimum 422 `PAYOUT_BELOW_MINIMUM` (body carries `minimum`), above payable 422 `INSUFFICIENT_PAYABLE_BALANCE` (body carries `payable`). Partial amounts are allowed.
- Idempotency: header `Idempotency-Key` (1..150 chars, required, 400 `VALIDATION_ERROR` if missing), unique per `(organization_id, idempotency_key)`. `request_hash = SHA-256(currency|amount|bankAccountId)` is stored. Same key and same hash returns the existing payout (200, not 201). Same key and different hash is 409 `IDEMPOTENCY_KEY_REUSED`. Admin transitions need no key (they are state guarded).
- At most one OPEN payout (REQUESTED, APPROVED, PROCESSING) per `(organization, currency)`: partial unique index (sec. 5). A second request gets 409 `OPEN_PAYOUT_EXISTS` (body carries the open payout id). This also bounds blast radius of a compromised owner account.
- Bank account must be `VERIFIED` (sec. 4) or 422 `BANK_ACCOUNT_NOT_VERIFIED` (no account at all: 422 `BANK_ACCOUNT_REQUIRED`). The payout snapshots `bank_account_id`, `iban_ciphertext`, `account_holder_name` and `bank_account_version` at P1 so the money goes where the owner said at request time. At P2 (approve) and P4 (paid) the current account `version` must still equal `bank_account_version` or 409 `BANK_ACCOUNT_CHANGED` (admin cancels, partner re-requests). That is the re-verification guard.

### 3.4 Concurrency: a balance is never paid twice

Three layers, all required:
1. Lock row. Table `payout_wallets (organization_id, currency)` primary key, no other data. Request, cancel, fail, admin adjustment and approve-time recheck all do `INSERT INTO payout_wallets ... ON CONFLICT DO NOTHING` then `SELECT ... FOR UPDATE` (JPA `@Lock(PESSIMISTIC_WRITE)` on a `findForUpdate` query, same pattern as `EscrowItemRepository.findByOrderItemIdForUpdate`, `:319-321`). Lock order is always wallet first, then payout row (`SELECT ... FOR UPDATE` on `partner_payouts.id`), never the reverse, to avoid deadlock. Escrow rows are NOT locked (releases only increase the balance).
2. Balance is computed after the lock is held and inside the same transaction as the `RESERVE` insert. Two parallel requests of `payable` each: the second waits, then sees `delta` reduced and gets 422 `INSUFFICIENT_PAYABLE_BALANCE` (or 409 `OPEN_PAYOUT_EXISTS`).
3. Database rules: partial unique index for one open payout per organization and currency; unique `(organization_id, idempotency_key)`; unique `payment_reference` (a bank reference cannot mark two payouts paid); `CHECK (amount > 0)`; status guarded UPDATE (`... WHERE id = :id AND status = :from`, zero rows updated gives 409) plus `@Version`. PAID is written once because P4 requires status PROCESSING under the payout row lock and the ledger `PAID` rows are keyed `UNIQUE (payout_id, entry_type)` for the entry types `RESERVE`, `PAID`, `RESERVE_RELEASE` (a payout reserves once and either releases once or is paid once).

Rejected design: computing a running balance column. The balance is always derived from `escrow_items` plus `payout_ledger`, so there is no counter that can drift.

### 3.5 Refund or chargeback after release

The platform never claws back from escrow (CANCELLATION-CONTRACT.md sec. 2), so this slice adds the minimum to be correct: an admin adjustment.
- `POST /api/admin/organizations/{orgId}/payout-adjustments` `{currency, amount, direction DEBIT|CREDIT, reason, orderItemId?, idempotencyKey}` writes `ADJUSTMENT_DEBIT` (`payable_delta = -amount`) or `ADJUSTMENT_CREDIT` (`+amount`) under the wallet lock. Idempotent on `(organization_id, idempotency_key)`. `reason` required (5..500). Admin only.
- A DEBIT may take the balance negative. A negative balance is shown as `owed` and blocks payout requests until earnings cover it. It never changes escrow rows and never reverses commission.
- Automatic reversal when an escrow item is refunded after release does not exist and is not built here (it cannot happen, sec. 1). If a future task allows refund after release, it must call the same adjustment path inside the `EscrowService` transaction (call out in the code review, see sec. 10 item 6).

## 4. Bank account (IBAN)

Where: payment-service, new table `partner_bank_accounts` (sec. 5). Not in auth-service (organization profile has no financial data) and not in `supplier_payout_accounts` (that is provider routing).

Fields: `organization_id`, `account_holder_name` (2..140, must equal the organization legal name, checked by the admin at verification, not by code), `iban_ciphertext` (AES-256-GCM, random 12 byte IV prefixed, key from `waylo.payouts.iban-key` env `WAYLO_PAYOUT_IBAN_KEY`, base64 of 32 bytes, startup fails in the prod profile if missing, same fail-fast style as the TEST provider refusal in `PaymentProviderConfig`), `iban_last4`, `iban_hash` (HMAC-SHA256 of the normalized IBAN with a second key `waylo.payouts.iban-hmac-key`, used for duplicate detection across organizations), `status`, `version`.

Validation (`IbanValidator`, pure static class, unit tested):
1. Normalize: strip spaces and hyphens, uppercase. Input may be grouped.
2. Must match `^TR[0-9]{24}$` (26 chars). Non-TR IBAN gives 400 `INVALID_IBAN` (open decision 2 for foreign accounts).
3. ISO 13616 mod-97: move the first four chars to the end, map letters to numbers (A=10 .. Z=35), the big integer mod 97 must be 1. Implement with `BigInteger` or digit-by-digit remainder. Test vector valid: `TR33 0006 1005 1978 6457 8413 26`. Invalid: same with the last digit changed (`...1327`), wrong length, `DE89 3704 0044 0532 0130 00`.
4. Bank code is chars 5-9 of the TR BBAN (not validated against a list in this slice).
5. Uniqueness: the same `iban_hash` active for two organizations gives 409 `IBAN_ALREADY_IN_USE` (fraud signal, admin sees it via the verification queue).

Lifecycle `partner_bank_accounts.status`: `PENDING_VERIFICATION -> VERIFIED | REJECTED`, and `DISABLED` (partner deleted it). `PUT /bank-account` by an owner: if the organization has an account, it is replaced IN PLACE (same row, `version + 1`, status back to `PENDING_VERIFICATION`, `verified_at/by` cleared). A change therefore always forces re-verification and invalidates open payout snapshots (sec. 3.3 `BANK_ACCOUNT_CHANGED`). Open payouts are not blocked from completing if the account did not change. `DELETE` sets `DISABLED`, allowed only when no OPEN payout exists (409 `OPEN_PAYOUT_EXISTS`). One live (non DISABLED) account per organization (`UNIQUE (organization_id)`). Admin verification is manual (call or document check outside the system): `POST /api/admin/bank-accounts/{id}/verify` or `/reject {reason}`. Admin may not verify an account whose `updated_by` is the same user id (admins are not partners, so this only matters if an admin also owns an org, enforced as a simple check).

Change audit: append-only `partner_bank_account_changes (id, bank_account_id, organization_id, change_type CREATED|REPLACED|DISABLED|VERIFIED|REJECTED|REVEALED, old_last4, new_last4, old_iban_hash, new_iban_hash, actor_user_id, actor_type, reason, occurred_at)`. Never stores a full IBAN. Same triggers as `supplier_ledger`. Writes in the same transaction as the change. Audit only, no outbox event (account change emails are a possible follow-up, sec. 10 item 7).

Masking (every response, log line and event):
- Binding format: responses carry `ibanLast4` (4 chars) and `maskedIban` = `"TR** **** **** **** **** **" + last4.substring(2)`, e.g. `TR** **** **** **** **** **26`. Check digits and the middle are never shown. One helper `IbanMask.mask(last4)` is the only code that formats an IBAN. Tests assert the full IBAN and its middle digits never appear in any response body, log, event payload or email.
- The ciphertext and hash never leave the service (no field in any DTO, `@JsonIgnore` is not enough: DTOs are separate records without them).
- The only place the full IBAN is returned: `GET /api/admin/payouts/{id}/bank-details` (ADMIN, status APPROVED or PROCESSING only, otherwise 409 `INVALID_STATE_TRANSITION`). It returns the snapshot IBAN and holder name so the admin can send the transfer, and writes a `REVEALED` row in `partner_bank_account_changes` (actor, payout id in `reason`). Response header `Cache-Control: no-store`. This is the one deliberate exception to "masked everywhere" and is open decision 4 (alternative: show masked and require the admin to read it from the bank portal, unworkable).
- Also: `GET /api/admin/bank-accounts/{id}/iban` (ADMIN only, any account status, 404 if unknown) returns `{bankAccountId, accountHolderName, iban}` so the admin can check a PENDING_VERIFICATION account against the organization's documents. It writes a `REVEALED` row with `reason` = `bank-account:{id}` and returns `Cache-Control: no-store`. The UI keeps the IBAN in component state only.
- Request logging must not log request bodies of the bank-account endpoints (implementation verifies there is no body logging filter).

No real money movement: provider integration is out of scope. `PROCESSING` means the admin has started a manual bank transfer. `PAID` records `reference` and `paid_by`. A later task may replace P3-P4 with a provider call behind a new port, without changing the lifecycle.

## 5. Data: payment-service Flyway `V9__partner_payouts.sql`

Next free version is V9 (V1..V8 exist). One transaction (Flyway default). Existing tables are not altered. The `ledger_entries` account CHECK is not touched.

```sql
-- Partner payouts (PAYOUT-CONTRACT.md). Money NUMERIC(12,2) + CHAR(3). Balance is derived, never stored.

CREATE TABLE payout_wallets (            -- lock rows only
    organization_id UUID    NOT NULL,
    currency        CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, currency)
);

CREATE TABLE partner_bank_accounts (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL UNIQUE,
    account_holder_name VARCHAR(140) NOT NULL,
    iban_ciphertext     TEXT NOT NULL,
    iban_last4          CHAR(4) NOT NULL,
    iban_hash           CHAR(64) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    verified_by         UUID,
    verified_at         TIMESTAMPTZ,
    reject_reason       VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          UUID NOT NULL,
    updated_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bank_status CHECK (status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','DISABLED')),
    CONSTRAINT chk_bank_verified CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL AND verified_by IS NOT NULL))
);
CREATE UNIQUE INDEX uq_bank_iban_hash_live ON partner_bank_accounts (iban_hash) WHERE status <> 'DISABLED';

CREATE TABLE partner_bank_account_changes (
    id               UUID PRIMARY KEY,
    bank_account_id  UUID NOT NULL REFERENCES partner_bank_accounts (id),
    organization_id  UUID NOT NULL,
    change_type      VARCHAR(16) NOT NULL CHECK (change_type IN ('CREATED','REPLACED','DISABLED','VERIFIED','REJECTED','REVEALED')),
    old_last4        CHAR(4),
    new_last4        CHAR(4),
    old_iban_hash    CHAR(64),
    new_iban_hash    CHAR(64),
    actor_user_id    UUID NOT NULL,
    actor_type       VARCHAR(8) NOT NULL CHECK (actor_type IN ('PARTNER','ADMIN')),
    reason           VARCHAR(500),
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bank_changes_org ON partner_bank_account_changes (organization_id, occurred_at DESC);

CREATE TABLE partner_payouts (
    id                    UUID PRIMARY KEY,
    organization_id       UUID NOT NULL,
    currency              CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    amount                NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status                VARCHAR(16) NOT NULL,
    idempotency_key       VARCHAR(150) NOT NULL,
    request_hash          CHAR(64) NOT NULL,
    bank_account_id       UUID NOT NULL REFERENCES partner_bank_accounts (id),
    bank_account_version  BIGINT NOT NULL,
    iban_ciphertext       TEXT NOT NULL,           -- snapshot at request time
    iban_last4            CHAR(4) NOT NULL,
    account_holder_name   VARCHAR(140) NOT NULL,
    requested_by          UUID NOT NULL,
    requested_by_email    VARCHAR(255),            -- gateway X-User-Email at request time, for the notification
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    processing_at         TIMESTAMPTZ,
    paid_by               UUID,
    paid_at               TIMESTAMPTZ,
    payment_reference     VARCHAR(100),
    failure_reason        VARCHAR(500),
    cancelled_by          UUID,
    cancelled_by_type     VARCHAR(8),
    cancelled_at          TIMESTAMPTZ,
    cancel_reason         VARCHAR(500),
    version               BIGINT NOT NULL DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payout_status CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED','CANCELLED')),
    CONSTRAINT chk_payout_paid CHECK (status <> 'PAID' OR (payment_reference IS NOT NULL AND paid_at IS NOT NULL AND paid_by IS NOT NULL)),
    CONSTRAINT chk_payout_failed CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT chk_payout_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by_type IN ('PARTNER','ADMIN'))),
    CONSTRAINT uq_payout_idem UNIQUE (organization_id, idempotency_key)
);
CREATE UNIQUE INDEX uq_payout_open ON partner_payouts (organization_id, currency)
    WHERE status IN ('REQUESTED','APPROVED','PROCESSING');
CREATE UNIQUE INDEX uq_payout_reference ON partner_payouts (payment_reference) WHERE payment_reference IS NOT NULL;
CREATE INDEX idx_payout_org ON partner_payouts (organization_id, requested_at DESC);
CREATE INDEX idx_payout_queue ON partner_payouts (status, requested_at);

CREATE TABLE payout_events (
    id           UUID PRIMARY KEY,
    payout_id    UUID NOT NULL REFERENCES partner_payouts (id),
    from_status  VARCHAR(16),
    to_status    VARCHAR(16) NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_type   VARCHAR(8) NOT NULL CHECK (actor_type IN ('PARTNER','ADMIN','SYSTEM')),
    reason       VARCHAR(500),
    reference    VARCHAR(100),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payout_events_payout ON payout_events (payout_id, occurred_at);

-- Balance movements and payout accounting legs. payable_delta is the ONLY input to the payable formula (sec. 2).
CREATE TABLE payout_ledger (
    id               UUID PRIMARY KEY,
    group_id         UUID NOT NULL,
    organization_id  UUID NOT NULL,
    payout_id        UUID REFERENCES partner_payouts (id),
    entry_type       VARCHAR(20) NOT NULL,
    payable_delta    NUMERIC(12,2) NOT NULL,
    account          VARCHAR(30),
    debit_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    credit_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency         CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    order_item_id    UUID,
    idempotency_key  VARCHAR(150),
    reference        VARCHAR(500),
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payout_ledger_type CHECK (entry_type IN ('RESERVE','RESERVE_RELEASE','PAID','ADJUSTMENT_DEBIT','ADJUSTMENT_CREDIT')),
    CONSTRAINT chk_payout_ledger_shape CHECK (
        (entry_type = 'RESERVE'           AND payable_delta < 0 AND payout_id IS NOT NULL AND account IS NULL)
     OR (entry_type = 'RESERVE_RELEASE'   AND payable_delta > 0 AND payout_id IS NOT NULL AND account IS NULL)
     OR (entry_type = 'PAID'              AND payable_delta = 0 AND payout_id IS NOT NULL AND account IN ('SUPPLIER_PAYABLE','SUPPLIER_PAID'))
     OR (entry_type = 'ADJUSTMENT_DEBIT'  AND payable_delta < 0 AND payout_id IS NULL AND account IS NULL AND idempotency_key IS NOT NULL)
     OR (entry_type = 'ADJUSTMENT_CREDIT' AND payable_delta > 0 AND payout_id IS NULL AND account IS NULL AND idempotency_key IS NOT NULL)),
    CONSTRAINT chk_payout_ledger_sides CHECK (debit_amount >= 0 AND credit_amount >= 0 AND (debit_amount = 0 OR credit_amount = 0)
        AND ((entry_type = 'PAID') = (debit_amount + credit_amount > 0)))
);
CREATE UNIQUE INDEX uq_payout_ledger_once ON payout_ledger (payout_id, entry_type, account) NULLS NOT DISTINCT
    WHERE payout_id IS NOT NULL;
CREATE UNIQUE INDEX uq_payout_ledger_adj_idem ON payout_ledger (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payout_ledger_org ON payout_ledger (organization_id, currency);

-- Append-only: UPDATE, DELETE, TRUNCATE rejected (same pattern as V2 and V4).
CREATE FUNCTION payout_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_payout_ledger_row BEFORE UPDATE OR DELETE ON payout_ledger FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_ledger_trunc BEFORE TRUNCATE ON payout_ledger FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_events_row BEFORE UPDATE OR DELETE ON payout_events FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_payout_events_trunc BEFORE TRUNCATE ON payout_events FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_bank_changes_row BEFORE UPDATE OR DELETE ON partner_bank_account_changes FOR EACH ROW EXECUTE FUNCTION payout_append_only();
CREATE TRIGGER trg_bank_changes_trunc BEFORE TRUNCATE ON partner_bank_account_changes FOR EACH STATEMENT EXECUTE FUNCTION payout_append_only();
```

Notes for the implementer:
- `partner_payouts` and `partner_bank_accounts` are mutable (status, version) by design. Their history is in the append-only `payout_events` and `partner_bank_account_changes`. This is the same split as `escrow_items` (mutable) plus `ledger_entries` (append-only).
- `NULLS NOT DISTINCT` needs PostgreSQL 15. Check `docker-compose.yml` postgres image version first. If older, use `COALESCE(account,'')` in a functional unique index instead.
- `ddl-auto: validate` is on (`application.yml`): entities must match column types exactly (`bpchar(3)` columnDefinition as in `EscrowItem.java:62`, `CHAR(4)` and `CHAR(64)` columns need `columnDefinition = "bpchar(n)"` the same way).
- The `payout_ledger` PAID rows do not enter `ledger_entries` (that table needs `order_id`). The reconciliation view for admin adds the two sources: `SUM(supplier_amount) RELEASED = SUM(payable_delta of ADJUSTMENT/RESERVE rows) + payable + ...`. Concretely `released = payable + releasedNotYetPayable + reservedOpen + paidTotal - adjustmentsNet` (adjustmentsNet = sum of ADJUSTMENT deltas). Tests assert this identity.
- Config (`payment-service/src/main/resources/application.yml`): `waylo.payouts.holdback-days: 0`, `waylo.payouts.minimum-amount.TRY: 100.00`, `waylo.payouts.iban-key: ${WAYLO_PAYOUT_IBAN_KEY}`, `waylo.payouts.iban-hmac-key: ${WAYLO_PAYOUT_IBAN_HMAC_KEY}`, `waylo.payouts.require-different-admins: false`. Add the two env vars to `.env.example`/docker-compose only if those files already list `INTERNAL_API_TOKEN` style secrets (implementer checks, surgical edit only).

### Outbox events (no schema change, uses `outbox_event` V7/V8)

Written with `OutboxWriter.write("PAYOUT", payoutId, KafkaTopics.PAYMENT_EVENTS, organizationId, event)` inside the transition transaction (P4 and P5 only):
- `PayoutPaidEvent` (`eventType PAYOUT_PAID`, `source payment-service`, extends `BaseEvent`): `payoutId, organizationId, requestedByUserId, recipientEmail, amount, currency, ibanLast4, reference, paidAt`.
- `PayoutFailedEvent` (`PAYOUT_FAILED`): `payoutId, organizationId, requestedByUserId, recipientEmail, amount, currency, ibanLast4, reason`.
- Register tokens in `KafkaProducerConfig.TYPE_MAPPINGS` (`:30-31`): `payoutPaid:com.waylo.paymentservice.event.PayoutPaidEvent,payoutFailed:...PayoutFailedEvent`. Same two tokens in `notification-service KafkaConsumerConfig.TYPE_MAPPINGS` (`:36-50`) mapping to new notification-side classes `PayoutPaidEvent`, `PayoutFailedEvent` in `com.waylo.notificationservice.event`. Events never contain the full IBAN. Failure reason is the admin's free text and is shown to the partner (the admin UI warns not to put account data in it).
- P1 (requested), P2, P3, P6, P7 emit no event (open decision: notify the admin of new requests, default no, the queue page is the inbox).

## 6. Endpoints

All under existing conventions. Partner paths use the org in the path, authorized with the access check of sec. 3.2. Success is `{success:true, data, message?}` (existing `ApiResponse`).

### 6.1 Partner

| Method and path | Auth | Notes |
|---|---|---|
| `GET /api/partner/organizations/{orgId}/payouts/balance` | RESERVATION_READ | balances per currency |
| `GET /api/partner/organizations/{orgId}/payouts?status=&page=0&size=20` | RESERVATION_READ | newest first, max size 100 |
| `GET /api/partner/organizations/{orgId}/payouts/{payoutId}` | RESERVATION_READ | 404 if not this org |
| `POST /api/partner/organizations/{orgId}/payouts` | owner | header `Idempotency-Key`, 201 (200 on replay) |
| `POST /api/partner/organizations/{orgId}/payouts/{payoutId}/cancel` | owner | only REQUESTED, P6 |
| `GET /api/partner/organizations/{orgId}/bank-account` | RESERVATION_READ | 404 `NOT_FOUND` if none |
| `PUT /api/partner/organizations/{orgId}/bank-account` | owner | create or replace, 200 |
| `DELETE /api/partner/organizations/{orgId}/bank-account` | owner | sets DISABLED, 200 |

The existing `GET .../earnings` is fixed in the same slice to group by currency: `currency` stays for backward compatibility only when exactly one currency exists, and `totals` becomes `totalsByCurrency: [{currency, held, released, refunded}]`. Existing frontend type `PartnerEarnings` is updated in the same change (no consumer outside `frontend/src/app/partner/earnings/page.tsx` and `frontend/src/lib/partner.ts`, implementer greps first). The earnings `from/to` filter on `held_at` is unchanged.

`GET balance` response `data`:
```json
{ "organizationId": "uuid", "holdbackDays": 0,
  "balances": [ { "currency": "TRY", "payable": "1250.00", "releasedNotYetPayable": "0.00", "held": "480.00",
                  "reserved": "0.00", "paidTotal": "3000.00", "owed": "0.00", "minimumPayout": "100.00",
                  "canRequest": true, "blockedReason": null } ],
  "bankAccount": { "status": "VERIFIED", "maskedIban": "TR** **** **** **** **** **26" } }
```
`blockedReason` is one of `NO_BANK_ACCOUNT | BANK_NOT_VERIFIED | BELOW_MINIMUM | OPEN_PAYOUT | NEGATIVE_BALANCE | CURRENCY_NOT_SUPPORTED | NOT_OWNER | ORGANIZATION_NOT_ACTIVE | null`, so the UI never guesses. Money is serialized as the existing DTOs do (BigDecimal).

`POST payouts` body `{ "currency": "TRY", "amount": 1000.00 }` (`@NotBlank @Pattern ^[A-Z]{3}$`, `@NotNull @DecimalMin("0.01")` max `10000000.00`, scale max 2). Response `PayoutResponse`:
```json
{ "id":"uuid","organizationId":"uuid","currency":"TRY","amount":"1000.00","status":"REQUESTED",
  "maskedIban":"TR** **** **** **** **** **26","accountHolderName":"Acme Otel AS",
  "requestedAt":"...","approvedAt":null,"processingAt":null,"paidAt":null,"paymentReference":null,
  "failureReason":null,"cancelReason":null,"cancelledByType":null,"version":0 }
```
`PUT bank-account` body `{ "accountHolderName": "Acme Otel AS", "iban": "TR33 0006 1005 1978 6457 8413 26" }`. Response `{id, accountHolderName, ibanLast4, maskedIban, status, verifiedAt, rejectReason, updatedAt, version}`. The request body iban is never echoed.

### 6.2 Admin (`X-User-Role: ADMIN`, 401 `UNAUTHORIZED` no user, 403 `Administrator role required`)

| Method and path | Notes |
|---|---|
| `GET /api/admin/payouts?status=&organizationId=&currency=&page=&size=` | queue, default status filter REQUESTED,APPROVED,PROCESSING, oldest first |
| `GET /api/admin/payouts/{id}` | detail with `events[]`, masked IBAN, org balance snapshot |
| `GET /api/admin/payouts/{id}/bank-details` | full IBAN reveal (sec. 4), audited, `no-store` |
| `GET /api/admin/bank-accounts/{id}/iban` | full IBAN reveal for a bank account (sec. 4), audited `bank-account:{id}`, `no-store` |
| `POST /api/admin/payouts/{id}/approve` | P2 |
| `POST /api/admin/payouts/{id}/processing` | P3 |
| `POST /api/admin/payouts/{id}/paid` | P4, body `{ "reference": "TRF-2026-11-24-001", "paidAt": "optional instant" }` |
| `POST /api/admin/payouts/{id}/failed` | P5, body `{ "reason": "..." }` |
| `POST /api/admin/payouts/{id}/cancel` | P6/P7, body `{ "reason": "..." }` (the "reject" action) |
| `GET /api/admin/bank-accounts?status=PENDING_VERIFICATION` | verification queue, masked |
| `POST /api/admin/bank-accounts/{id}/verify` | body `{}`, status must be PENDING_VERIFICATION |
| `POST /api/admin/bank-accounts/{id}/reject` | body `{ "reason": "..." }` |
| `GET /api/admin/organizations/{orgId}/payout-balance` | same shape as partner balance plus `adjustmentsNet`, `reconciles` (boolean of the identity in sec. 5) |
| `POST /api/admin/organizations/{orgId}/payout-adjustments` | sec. 3.5, 201 |

All admin transition bodies may carry `expectedVersion`; if present and different, 409 `VERSION_CONFLICT`.

### 6.3 Error codes

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | bean validation, missing `Idempotency-Key` |
| 400 | `INVALID_IBAN` | format or checksum |
| 401 | `UNAUTHORIZED` | no user |
| 403 | `ACCESS_DENIED` (existing code of `AccessDeniedException`) | access check denied, not admin |
| 403 | `PAYOUT_OWNER_ONLY` | member without ORGANIZATION_OWNER on a write |
| 404 | `NOT_FOUND` | payout, bank account not of this org or missing |
| 409 | `INVALID_STATE_TRANSITION` | not in the table (carries `from`, `to`) |
| 409 | `VERSION_CONFLICT` | `@Version` or `expectedVersion` mismatch (map `ObjectOptimisticLockingFailureException`, already imported in `GlobalExceptionHandler`) |
| 409 | `OPEN_PAYOUT_EXISTS` | one open payout per org and currency |
| 409 | `IDEMPOTENCY_KEY_REUSED` | same key, different body |
| 409 | `IBAN_ALREADY_IN_USE` | hash unique |
| 409 | `BANK_ACCOUNT_CHANGED` | account version differs from the payout snapshot at P2 or P4 |
| 409 | `ORGANIZATION_NOT_ACTIVE` | org not ACTIVE |
| 409 | `PAYMENT_REFERENCE_IN_USE` | `uq_payout_reference` violation |
| 422 | `INSUFFICIENT_PAYABLE_BALANCE` | amount > payable (carries `payable`) |
| 422 | `PAYOUT_BELOW_MINIMUM` | amount < minimum (carries `minimum`) |
| 422 | `PAYOUT_CURRENCY_NOT_SUPPORTED` | no configured minimum for currency |
| 422 | `BANK_ACCOUNT_REQUIRED` / `BANK_ACCOUNT_NOT_VERIFIED` | none, or not VERIFIED |
| 503 | `DEPENDENCY_UNAVAILABLE` | auth down (existing handler, fails closed) |

New codes with 409 need exceptions or a small `ConflictException(code, message)` mapped in `GlobalExceptionHandler` (existing 409s there are `DuplicateResourceException` code `CONFLICT` and `CommissionRateConflictException`, neither carries a custom code). `BusinessRuleException(code, message)` already maps to 422 (`GlobalExceptionHandler.java:88-99`), use it for the 422 rows. Database unique violations (`DataIntegrityViolationException`, already handled in the file) must be translated to `OPEN_PAYOUT_EXISTS` / `PAYMENT_REFERENCE_IN_USE` / `IBAN_ALREADY_IN_USE` by constraint name, never leak the SQL message.

### 6.4 Gateway and auth routes

`api-gateway/src/main/resources/application.yml` payment-service route (line 69): append
`,/api/partner/organizations/*/payouts,/api/partner/organizations/*/payouts/**,/api/partner/organizations/*/bank-account,/api/admin/payouts,/api/admin/payouts/**,/api/admin/bank-accounts,/api/admin/bank-accounts/**,/api/admin/organizations/*/payout-balance,/api/admin/organizations/*/payout-adjustments`.
The payment-service route is above the auth-service route (`/api/admin/**`, `/api/organizations/**`), keep that order. `/api/admin/organizations/*/...` is more specific than anything auth-service owns today for those two exact suffixes, but implementer checks `auth-service` controllers for an `/api/admin/organizations/{id}/...` clash. Gateway CORS allows `PUT` and `DELETE` already (`allowed-methods` list in the same file, implementer confirms `PUT` is present). Rate limit: reuse the existing filter if it already covers `/api/partner/**`, else no change (not needed in this slice). `/internal/**` is never routed. No new `waylo.service-auth.routes` entry in payment-service (no new internal endpoint). In auth-service add `payment-service` to the `/internal/organizations` callers (sec. 3.2).

## 7. Frontend

No fake data: every number comes from the endpoints above, omit elements when the backend has no data.

Partner `frontend/src/app/partner/earnings/page.tsx` (extend, keep date filter and rows):
- Remove the static "Odeme takvimi henuz tanimlanmadi" line.
- Balance cards per currency from `GET payouts/balance`: "Cekilebilir bakiye" (`payable`), "Beklemede (tahmini)" (`held`, labeled estimate), "Odeme surecinde" (`reserved`), "Odenen toplam" (`paidTotal`), "Bekleme suresi" amount only if `releasedNotYetPayable > 0`. `owed > 0` shows a warning.
- Button "Odeme talep et" per currency, enabled only when `canRequest`, otherwise shows text from `blockedReason` (Turkish map in `lib/partner.ts`). Opens a small form (amount, default = payable, "Tumunu cek") and sends `Idempotency-Key = crypto.randomUUID()` generated when the form opens (reused on retry of the same attempt). Hidden for non-owners (`NOT_OWNER`).
- Section "Banka hesabi": masked IBAN, holder, status pill (`PENDING_VERIFICATION` "Onay bekliyor", `VERIFIED` "Onayli", `REJECTED` "Reddedildi" with reason). Form for IBAN (client side format hint only, server validates) and holder name. Warning text that a change requires re-verification. Never pre-fills the IBAN.
- Section "Odeme gecmisi": table from `GET payouts` (date, amount, status pill, reference when PAID, failure or cancel reason, "Iptal" button for REQUESTED).
- Types and calls in `frontend/src/lib/partner.ts` (`PartnerPayoutBalance`, `PartnerPayout`, `PartnerBankAccount`, `partnerApi.payoutBalance|payouts|requestPayout|cancelPayout|bankAccount|saveBankAccount|deleteBankAccount`), update `PartnerEarnings` to `totalsByCurrency`, add `PAYOUT_STATUS_LABEL`. The api client must support `put` (check `frontend/src/lib/api.ts`, add only if missing) and a custom header for the idempotency key.
- Error messages via existing `partnerErrorMessage`, add Turkish text per code in sec. 6.3.

Admin new page `frontend/src/app/admin/payouts/page.tsx`:
- Queue table filtered by status tab (Talep, Onayli, Isleniyor, Gecmis), columns: organization (name via existing organizations admin API, fallback id), amount, requested date, masked IBAN, bank status.
- Row actions by status: Onayla, Isleme al, Odendi (modal: reference, optional date, shows "IBAN'i goster" which calls `bank-details` and displays the full IBAN until the modal closes, not stored in state beyond the modal), Basarisiz (reason), Iptal (reason). Disable buttons not valid for the status.
- Second tab "Banka hesaplari": verification queue with Onayla and Reddet.
- Org drawer or link to `GET /api/admin/organizations/{orgId}/payout-balance` with `reconciles` and the adjustment form (DEBIT or CREDIT, amount, reason, generated idempotency key).
- Nav entry `{ href: '/admin/payouts', label: 'Partner Odemeleri', icon: 'account_balance' }` in `frontend/src/app/admin/layout.tsx` list (lines 7-18), wrappers in `frontend/src/lib/admin.ts`. Leave `/admin/supplier-balance` untouched (flight prepaid balance, different concept) but its label is "Tedarikci Bakiyesi", no rename.
- Note: `frontend/src/lib/partner.ts`, `lib/admin.ts`, `admin/layout.tsx` currently have uncommitted changes from other agents (git status). The implementer rebases mentally and edits surgically.
- Frontend tests: extend `frontend/test` style (`bus.test.mjs` shows the node test pattern) with pure helper tests (blockedReason text map, money formatting of payout rows). `npx tsc --noEmit` and `npx eslint` must pass.

## 8. Notifications

Existing pattern (NOTIFICATION-CONTRACT.md): payment-service writes an outbox event on `payment-events`, notification-service `PaymentEventListener` (`@KafkaListener(topics = "payment-events")`) dispatches on event class, dedups per `eventId` through `processed_events`, acknowledges always.

Add to NOTIFICATION-CONTRACT.md sec. 1 table two rows (the implementer edits that file only for these rows):
| `PAYOUT_PAID` (payment-events) | payment-service P4 | "Odemeniz yapildi" | `recipientEmail` from the event (the requesting owner) | new |
| `PAYOUT_FAILED` (payment-events) | payment-service P5 | "Odemeniz yapilamadi" | `recipientEmail` | new |

Body PAID: "Odeme talebiniz tamamlandi. Tutar: X TL. Hesap: TR** **** ... **26. Banka referansi: REF." FAILED: "Odeme talebiniz islenemedi. Tutar: X TL. Neden: ... Bakiyeniz iade edildi, yeni talep olusturabilirsiniz." Money formatted with the existing `OrderEventListener.money` helper (package-private static, reuse by copying a tiny helper if it is not accessible). No full IBAN. No push (EMAIL row only).

Recipient: notification-service has no way to resolve a partner user's email (the `CrmServiceClient` resolves CUSTOMERS), so the event carries `recipientEmail` captured from the gateway `X-User-Email` header at P1 and stored in `partner_payouts.requested_by_email`. If it is null the listener logs and skips, still acknowledges and records the event as processed. `NotificationRequest(customerId, bookingId, type, recipient, subject, body)`: pass `requestedByUserId` as `customerId` and `payoutId` as `bookingId`. Implementer checks that `notifications.customer_id` and `booking_id` accept these (no FK to a customers table, see `notification-service/src/main/resources/db`) and only then reuses `notificationService.send`. If a constraint blocks it, report instead of widening the schema silently.

Listener change: `PaymentEventListener.handlePaymentEvent` adds two `instanceof` branches and two private handlers. It needs `NotificationService` injected: the class has ONE constructor (`PaymentEventListener(ProcessedEventRepository)`), so extend that single constructor, do not add a second one (CLAUDE.md rule 5, `scripts/check-bean-constructors.py`). The existing unit tests that construct it with mocks must be updated to the new constructor.

## 9. Acceptance tests

Write, do not run (coordinator runs the consolidated pass). Follow the existing styles: Mockito/JUnit unit tests like `PartnerEarningsServiceTest`, `PaymentFlowTest`, `SupplierBalanceServiceTest`; Testcontainers or DB tests for constraints like `LedgerImmutabilityTest`; live `scripts/test-payouts.py` modeled on `scripts/test-cancellation.py` (imports helpers from `scripts/test-payments.py`, own fixture, cleanup with `psql`, ledger trigger disabled only for fixture cleanup as `scripts/test-bus.py:120-124` does).

### 9.1 Unit and integration tests (payment-service/src/test)

`IbanValidatorTest`:
- valid grouped and ungrouped `TR33 0006 1005 1978 6457 8413 26` normalizes to 26 chars.
- invalid checksum (last digit changed), length 25, length 27, `DE89...` (non TR), lowercase `tr33...` accepted after normalize, letters in the digits part rejected.
- `IbanMaskTest`: `mask("1326")` gives `TR** **** **** **** **** **26`, full IBAN string never contained.
- `IbanCryptoTest`: round trip, two encryptions of the same IBAN differ (random IV), tampered ciphertext fails, wrong key fails, missing key fails startup in the prod profile.

`PayoutBalanceServiceTest` (repository stubs or DB test):
- released-only items count, HELD and REFUNDED excluded.
- `supplier_amount` already net of commission: commission 10% on 100.00 gives payable 90.00.
- holdback: item released 1 day ago with holdback 2 is in `releasedNotYetPayable`, not `payable`; after cutoff passes it is payable.
- currency isolation: TRY 100 and EUR 50 released give two balances, a TRY payout never touches EUR.
- payout reserve reduces payable, fail and cancel give it back, PAID keeps it reduced and `paidTotal` rises.
- refund-after-release: ADJUSTMENT_DEBIT reduces payable, DEBIT larger than payable gives `payable 0`, `owed > 0`, `canRequest false`, blockedReason NEGATIVE_BALANCE. ADJUSTMENT_CREDIT restores.
- reconciliation identity of sec. 5 holds across a mixed scenario; reconcile against `SUM(credit_amount)` of `SUPPLIER_PAYABLE` ledger rows for the same items.
- partner-funded discount item: payable equals `supplier_amount` (net of the discount), not gross.

`PayoutServiceTest`:
- happy path P1..P4 writes exactly: RESERVE(-A) at P1, two PAID legs at P4 (DR SUPPLIER_PAYABLE A, CR SUPPLIER_PAID A, debit sum equals credit sum), 4 `payout_events`, outbox `PAYOUT_PAID` with last4 only.
- P5 writes RESERVE_RELEASE(+A) and `PAYOUT_FAILED` outbox row; balance equals pre-request.
- P6 partner cancel from REQUESTED ok; from APPROVED partner gets 409 `INVALID_STATE_TRANSITION`, admin cancel from APPROVED ok.
- every illegal transition in the table (PAID to anything, FAILED to PAID, REQUESTED to PROCESSING, REQUESTED to PAID) is 409 with `from` and `to`.
- repeating a transition (approve twice) returns the same row, writes no second ledger row or event.
- minimum: `99.99` gives `PAYOUT_BELOW_MINIMUM`, `100.00` ok. Over payable by 0.01 gives `INSUFFICIENT_PAYABLE_BALANCE`. Unlisted currency gives `PAYOUT_CURRENCY_NOT_SUPPORTED`.
- no bank account: `BANK_ACCOUNT_REQUIRED`. PENDING_VERIFICATION: `BANK_ACCOUNT_NOT_VERIFIED`.
- account replaced after request: approve gives `BANK_ACCOUNT_CHANGED`, status stays REQUESTED, admin cancels and funds return.
- idempotency: same key same body returns same payout id with no second RESERVE; same key different amount gives `IDEMPOTENCY_KEY_REUSED`.
- `PAID` requires `reference`; reusing a reference on another payout gives `PAYMENT_REFERENCE_IN_USE`.
- non owner (`roles` without ORGANIZATION_OWNER, allowed true) gets 403 `PAYOUT_OWNER_ONLY` on request, cancel, PUT, DELETE and 200 on reads. Denied access decision gives 403 on reads. Auth `FeignException` gives 503. Inactive org gives 409 `ORGANIZATION_NOT_ACTIVE`.
- other organization's payout id on a partner path gives 404, not 403, and the body does not contain the id's organization.
- admin endpoints: non admin 403, anonymous 401 (extend `ControllerAuthTest` pattern).

`PayoutConcurrencyTest` (real PostgreSQL via Testcontainers, same infrastructure as the existing DB tests, N threads released with a `CountDownLatch`):
- 8 parallel `request(amount = full payable)` with 8 different idempotency keys: exactly 1 succeeds (201), the other 7 get `OPEN_PAYOUT_EXISTS` or `INSUFFICIENT_PAYABLE_BALANCE`, `SUM(payable_delta) + eligibleReleased` is exactly 0, one RESERVE row.
- 8 parallel requests with the SAME key and body: one row, every caller gets the same payout id.
- two parallel requests of half the balance each (open payout guard) still produce one open payout; after the first is cancelled, the second path is allowed.
- 4 parallel `markPaid` on one PROCESSING payout: one PAID leg pair, others get the same row (idempotent) or 409, never two leg pairs (unique `uq_payout_ledger_once`).
- request racing a concurrent `release` of an escrow item: no error, the new release only adds to the balance.
- parallel admin adjustment DEBIT versus request: the final `payable` is never negative from the request path.
- `fail` racing `markPaid` on the same payout: exactly one wins, the ledger is either RESERVE+RESERVE_RELEASE or RESERVE+PAID, never both (assert via the `uq_payout_ledger_once` and a status check).

`PayoutLedgerImmutabilityTest` (like `LedgerImmutabilityTest`): UPDATE, DELETE, TRUNCATE on `payout_ledger`, `payout_events`, `partner_bank_account_changes` all fail; CHECK `chk_payout_ledger_shape` rejects RESERVE with positive delta and PAID with nonzero delta; `uq_payout_open` rejects a second open payout row inserted directly.

`BankAccountServiceTest`:
- create stores ciphertext, last4 and hash; no field of any response DTO contains the IBAN (reflection over `PayoutDtos` records: no accessor returns the plain IBAN, serialized JSON of every DTO does not contain the middle digits `0006 1005 1978 6457 8413`).
- replace sets `PENDING_VERIFICATION`, bumps version, writes a `REPLACED` change with both last4 and hashes and no full IBAN.
- same IBAN for a second org gives `IBAN_ALREADY_IN_USE`. DELETE with an open payout gives `OPEN_PAYOUT_EXISTS`.
- `bank-details` reveal writes a `REVEALED` row, is 409 for REQUESTED and PAID payouts, returns `Cache-Control: no-store`, 403 for non admin.
- log capture test: no log event produced during create or replace contains the IBAN.

`PartnerEarningsServiceTest` (extend): two currencies produce two `totalsByCurrency` rows, single currency keeps `currency`.

`OutboxTest` (extend): `PayoutPaidEvent` serialises with the `payoutPaid` type token and consumer side deserialises into the notification class.

notification-service: `PaymentEventListenerPayoutTest`: PAID event sends one EMAIL notification to `recipientEmail` with masked IBAN and reference, a duplicate `eventId` sends nothing, null email skips but acks, body never contains more than last 2 digits of the IBAN, FAILED body contains the reason.

### 9.2 Live script `scripts/test-payouts.py` (through the gateway, own fixture, idempotent cleanup)

Fixture: ADMIN token (as `test-payments.py` obtains it), a partner organization approved ACTIVE with an OWNER user and a second `HOTEL_STAFF`-like member if the helpers allow, a second organization with its own owner (for cross-org), 10% partner commission, TEST provider, escrow rows created through the real flow (order, pay, redeem) so `released_at` is real. Escrow release time shifted by `psql` only for fixture rows, as `test-cancellation.py` does. Assertions (`ok(cond, label)` style, PASS/FAIL print, exit code 1 on FAIL):
1. balance: after one redeemed item gross 1000 at 10% commission, `payable == 900.00`, `held` of a second unredeemed item is shown and not in `payable`, and `GET earnings` agrees (`released`).
2. multi-currency: insert a second RELEASED row in EUR via `psql` fixture, balance lists TRY and EUR separately, a TRY payout leaves EUR unchanged, EUR request gives 422 `PAYOUT_CURRENCY_NOT_SUPPORTED`.
3. bank account: invalid IBAN 400 `INVALID_IBAN`, valid IBAN `PUT` returns `maskedIban` ending in the last 2 digits and `status PENDING_VERIFICATION`, response JSON string does not contain the full IBAN nor `0006 1005`, `GET` also masked, request before verification 422 `BANK_ACCOUNT_NOT_VERIFIED`, admin verify then ok, a non-admin verify call 403.
4. masking: grep the raw HTTP bodies of every partner and admin list/detail call in the script for the plain IBAN digits, none found. The admin `bank-details` call is the only one that returns it and only for APPROVED or PROCESSING. A psql query on `partner_bank_account_changes` and `outbox_event.payload` and `notifications.body` for the plain IBAN finds nothing.
5. lifecycle: request 900.00 gives 201 REQUESTED, replay with same `Idempotency-Key` gives 200 same id, same key with 800.00 gives 409, second request with a new key gives 409 `OPEN_PAYOUT_EXISTS`, `payable` reads 0.00 and `reserved 900.00`. approve, processing, paid with `reference` gives PAID, `paid` second call is idempotent, a different payout paid with the same reference gives 409 `PAYMENT_REFERENCE_IN_USE`. ledger rows via psql: RESERVE -900, PAID legs DR/CR 900, `SUM(debit)=SUM(credit)` for the PAID group, UPDATE on `payout_ledger` through psql fails.
6. failure path: second earnings, request, approve, processing, failed with reason gives `payable` restored and a new request is allowed.
7. double-payout race: with 900 payable, fire 10 parallel `POST payouts` with 10 distinct keys (threads), exactly one 201, nine 409/422, and `psql` shows one open payout and `SUM(payable_delta)` consistent. Then 6 parallel `paid` calls on one PROCESSING payout: one ledger pair.
8. refund-after-release: admin DEBIT adjustment of 200 against a 900 balance gives `payable 700.00`; a further DEBIT of 5000 (balance 700 - 5000) gives `payable 0.00`, `owed 4300.00`, `canRequest false` and request 422; CREDIT restores; adjustment replay with same key writes one row; non admin adjustment 403.
9. cross-org: owner of org B reading org A balance, payouts, bank-account gets 403; org B owner requesting `GET /payouts/{A's payout id}` under org B path gets 404; org B `POST` cancel of A's payout gives 404. Non-owner member of A: `GET balance` 200, `POST payouts` 403 `PAYOUT_OWNER_ONLY`. Anonymous 401. Customer role calling admin endpoints 403.
10. account change after request: request payout, replace IBAN, admin approve gives 409 `BANK_ACCOUNT_CHANGED`, admin cancels, balance restored, account is `PENDING_VERIFICATION` again.
11. notification: after paid, poll `GET /api/notifications` (admin) for a row with the payout id, body contains the reference and the masked IBAN, not the full IBAN (`test-notifications.py` shows the access pattern). Skip with a printed SKIP only if SMTP-less mode records no row (rows are SENT or SUPPRESSED per NOTIFICATION-CONTRACT.md, so a row exists).
12. holdback: not exercised live (config default 0), covered in unit tests.

Static checks required at the end by the coordinator rule: `:payment-service:compileJava :payment-service:compileTestJava`, `:notification-service:compileJava :notification-service:compileTestJava`, `python3 -m py_compile scripts/test-payouts.py`, `npx tsc --noEmit`, `npx eslint`, `python3 scripts/check-bean-constructors.py`, `python3 scripts/check-yaml-duplicates.py` (three yml files are edited).

## 10. Open decisions for the user (each has a default the implementation uses)

1. Payout schedule. Default: on demand, partner requests when the balance exceeds the minimum, admin pays manually (no automatic schedule). Alternative: weekly or monthly batch, the admin queue would be generated by a scheduler (not built).
2. Minimum, currency and holdback. Default: minimum `100.00 TRY`, only TRY payable (Turkish IBAN only), holdback `0` days after release (released at QR scan or no-show already means service was delivered). Alternatives: higher minimum, 2 to 7 day holdback for chargeback exposure, foreign IBAN or SWIFT and EUR payouts (needs a currency and bank model change).
3. Commission timing. Default: commission is fixed at capture and deducted from every payout automatically (the payable is already net). Alternative: invoice commission monthly and pay gross (would need a receivable ledger, not built). Also open: platform-funded discounts are already absorbed by the platform and do not reduce payable.
4. Merchant of record and legal. Default: the platform holds the funds and pays out as the contracting party, with manual bank transfers, as sandbox only. Whether the platform may hold and forward customer money (MoR, payment institution license, or use iyzico Pazaryeri / sub-merchant settlement where the provider pays out and this feature becomes reporting only), tax invoices (e-fatura / komisyon faturasi), KYC of the account holder and withholding tax are business and legal decisions this document does not make (PRODUCT-ARCHITECTURE.md sec. 5). Also open: admin sees the full IBAN through the audited reveal endpoint (default) versus a bank-portal workflow.
5. Who initiates and who approves. Default: the organization OWNER requests, any platform admin approves and marks paid (single admin allowed). Alternatives: admin-initiated payout runs, four-eyes (`require-different-admins=true`, already wired as a flag, default false), also letting HOTEL_MANAGER request.
6. Refund after release. Default: no automatic reversal (it cannot occur today), manual admin adjustment (sec. 3.5), negative balance carried and blocks requests. Decide whether the platform may ever refund a customer after the partner was paid, and whether it is recovered from future earnings (default here) or invoiced.
7. Notifications. Default: partner gets email on PAID and FAILED only. Optional: email to admins on new requests, email to the owner on bank account verified or rejected or changed.
8. Minimum identity of the account holder. Default: admin compares holder name with the organization legal name manually. Alternative: store the legal name in payment-service from auth and block mismatches automatically.

## 11. Files the implementation will touch

payment-service (all under `payment-service/src/main/`):
- `resources/db/migration/V9__partner_payouts.sql` (new)
- `resources/application.yml` (`waylo.payouts.*`)
- `java/com/waylo/paymentservice/domain/`: `PartnerPayout`, `PayoutStatus`, `PayoutLedgerEntry`, `PayoutEvent`, `PartnerBankAccount`, `BankAccountStatus`, `BankAccountChange`, `PayoutWallet` (new)
- `.../repository/`: `PartnerPayoutRepository`, `PayoutLedgerRepository`, `PayoutEventRepository`, `PartnerBankAccountRepository`, `BankAccountChangeRepository`, `PayoutWalletRepository` (new), `EscrowItemRepository` (add the three balance queries)
- `.../service/`: `PayoutBalanceService`, `PayoutService`, `BankAccountService`, `IbanValidator`, `IbanMask`, `IbanCrypto` (new, one constructor each), `PartnerEarningsService` (group by currency)
- `.../controller/`: `PartnerPayoutController`, `PartnerBankAccountController`, `AdminPayoutController` (new), `PartnerEarningsController` untouched except DTO
- `.../dto/`: `PayoutDtos` (new), `PartnerEarningsResponse` (totalsByCurrency)
- `.../client/AuthAccessClient.java` (active-ids method)
- `.../event/`: `PayoutPaidEvent`, `PayoutFailedEvent` (new), `PaymentEventPublisher` or direct `OutboxWriter` use, `.../config/KafkaProducerConfig.java` (type mappings)
- `.../exception/`: `ConflictException` (new) and `GlobalExceptionHandler.java` (map it, constraint name translation)
- tests (`payment-service/src/test/java/com/waylo/paymentservice/`): `service/IbanValidatorTest`, `service/IbanMaskTest`, `service/IbanCryptoTest`, `service/PayoutBalanceServiceTest`, `service/PayoutServiceTest`, `service/PayoutConcurrencyTest`, `service/PayoutLedgerImmutabilityTest`, `service/BankAccountServiceTest`, extended `PartnerEarningsServiceTest`, `controller/ControllerAuthTest`, `event/OutboxTest`

notification-service: `listener/PaymentEventListener.java` (single constructor extended), `event/PayoutPaidEvent.java`, `event/PayoutFailedEvent.java` (new), `config/KafkaConsumerConfig.java` (type mappings), test `PaymentEventListenerPayoutTest` and the existing listener test constructor updates.

Other services: `api-gateway/src/main/resources/application.yml` (payment-service route paths), `auth-service/src/main/resources/application.yml` (`/internal/organizations` callers add payment-service, and any config-service copy).

Frontend: `frontend/src/app/partner/earnings/page.tsx`, `frontend/src/app/admin/payouts/page.tsx` (new), `frontend/src/app/admin/layout.tsx` (nav), `frontend/src/lib/partner.ts`, `frontend/src/lib/admin.ts`, `frontend/src/lib/api.ts` (only if `put` or custom headers are missing), a pure helper test under `frontend/test/`.

Scripts and docs: `scripts/test-payouts.py` (new), `API-DOCS.md` (payout and bank account sections next to `:1185-1230`), `NOTIFICATION-CONTRACT.md` (two rows), `STATUS.md` (entry). `.env.example` and `docker-compose.yml` only to add the two IBAN key variables, surgically, nothing else deleted.

Not touched: `EscrowService`, `LedgerService`, `PaymentProvider` and its implementations, `SupplierBalanceController` and the flight prepaid tables, package-booking-service, existing migrations V1..V8.
