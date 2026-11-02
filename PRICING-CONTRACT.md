# Pricing-service commercial layer contract (queue row 7e)

Source decisions: PRODUCT-DISCUSSION-NOTES.md line ~128. "Fiyatin ikinci kopyasi tutulmaz" — the base price lives only in the supplier service (hotel `rate_plan`s, car `category_rates`, flight seat prices, bus fares). Pricing-service **never stores a copy of base prices**. This file is the single contract for all 7e workers. Times are `Europe/Istanbul`. Money `NUMERIC(12,2)`, `RoundingMode.HALF_EVEN`. All JSON wrapped in the existing `ApiResponse` envelope. Error bodies use the existing `ErrorResponse` with an `error` code.

## 0. Domain boundary & migration of `pricing_rules`

| Current | New |
|---------|-----|
| `pricing_rules` with `base_price` — stores base prices, conflicts with decision | **Removed**. See §0a. |
| `PricingType` enum (FLIGHT, HOTEL, CAR_RENTAL, PACKAGE_DISCOUNT) | Replaced by campaign tables + tax_rate table |
| Kafka consumer/producer infrastructure (kept for promo usage events) | Used for promo-usage committed/cancelled events |

**0a. Migration** (Flyway V2+ in pricing-service):
1. `ALTER TABLE pricing_rules RENAME TO pricing_rules__old`
2. Drop columns: `base_price`, `pricing_type`, `active`, `discount_percentage`
3. Retain: `id`, `name`, `currency`, `description`, `created_at`, `updated_at` (for audit — all rows become orphaned, no references expected)
4. New tables: `campaigns`, `promo_codes`, `tax_rates`
5. Workers must **not** insert into `pricing_rules`; all new inserts go to `campaigns`.

## 1. Tables (pricing-service, Flyway V2+)

**`campaigns`** (commercial rules with no base prices):

| Column | Type | Constraint |
|--------|------|------------|
| `id` | UUID | PK |
| `name` | VARCHAR(255) | NOT NULL |
| `vertical` | VARCHAR(20) | NOT NULL (OTEL, ARAÇ, UÇUŞ, OTOBÜS) |
| `scope_org_id` | UUID | NULL = platform-wide, set = partner-only |
| `discount_type` | VARCHAR(20) | NOT NULL (PERCENTAGE, FIXED) |
| `discount_value` | NUMERIC(10,4) | NOT NULL |
| `min_order_amount` | NUMERIC(12,2) | >= 0, default 0 |
| `start_at` | TIMESTAMP | NOT NULL |
| `end_at` | TIMESTAMP | NOT NULL (exclusive; campaign invalid at end_at) |
| `max_uses_global` | INTEGER | NULL = unlimited |
| `max_uses_per_user` | INTEGER | NULL = unlimited |
| `funded_by` | VARCHAR(20) | NOT NULL (PLATFORM, PARTNER) |
| `description` | TEXT | NULL |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT NOW() |
| `updated_at` | TIMESTAMP | NOT NULL DEFAULT NOW() |

Constraints: unique (name, vertical, start_at). No `active` column — validity is implicit from `start_at`/`end_at`.

**`promo_codes`** (optional text codes attached to campaigns):

| Column | Type | Constraint |
|--------|------|------------|
| `id` | UUID | PK |
| `campaign_id` | UUID | FK → campaigns(id) ON DELETE CASCADE, NOT NULL |
| `code` | VARCHAR(50) | NOT NULL, UNIQUE |
| `max_uses_total` | INTEGER | NULL = unlimited |
| `uses_count` | INTEGER | NOT NULL DEFAULT 0 |
| `valid_from` | TIMESTAMP | NOT NULL |
| `valid_until` | TIMESTAMP | NOT NULL |

Constraint: unique (campaign_id, code). `uses_count` only incremented via the `/internal/pricing/quote` idempotent reserve.

**`tax_rates`** (empty, user must configure):

| Column | Type | Constraint |
|--------|------|------------|
| `id` | UUID | PK |
| `vertical` | VARCHAR(20) | NOT NULL |
| `scope_org_id` | UUID | NULL = default for vertical |
| `tax_type` | VARCHAR(50) | NOT NULL (KDV, KONAKLAMA_VERGISI, etc.) |
| `rate` | NUMERIC(5,4) | NOT NULL (0.0000..1.0000) |
| `inclusive` | BOOLEAN | NOT NULL (true = already in gross, false = added on top) |
| `valid_from` | TIMESTAMP | NOT NULL |

## 2. Commission rates — ownership decision

**Commission stays in `payment-service`** (not moved). Justification:
- `commission_rates`, `EscrowItem` (commission_rate/commission_amount/supplier_amount snapshots), `CommissionService.split()`, and `PaymentIntentService.plan()` are tightly coupled to the payment ledger. Moving commission to pricing-service would create two sources of truth: pricing-service for customer-facing pricing and payment-service for ledger reconciliation.
- The existing pattern — `EscrowItem` stores a **snapshot** of `commission_rate`, `commission_amount`, `supplier_amount` taken at capture time — is correct and idempotent. It does not need pricing-service to recompute it.
- Pricing-service computes the **customer price** (gross after discount, net with taxes). Payment-service computes the **ledger split** (commission on the gross amount the customer pays) using its own stored commission rate snapshot. These are different things: one is customer-facing, one is internal accounting.

**Effect of `funded_by` on commission base (binding):** commission is ALWAYS computed on `gross_amount` (the supplier's own price), never on the discounted amount, so a platform campaign never changes what the partner earns.
- `PLATFORM` discount: customer pays `net = gross - discount`. Commission = `gross * rate`. Partner receives `gross - gross*rate` (exactly as without the campaign). Platform revenue = `gross*rate - discount` (may be negative; allowed, it is the platform's marketing cost).
- `PARTNER` discount: customer pays `net = gross - discount`. Commission = `gross * rate` (partner agreed to fund the discount, platform commission unchanged). Partner receives `net - gross*rate`. Must be >= 0, else the quote fails with 422 `DISCOUNT_EXCEEDS_PARTNER_SHARE`.
- payment-service therefore needs per item: `grossAmount`, `discountAmount`, `discountFundedBy` (not only the charged amount). Escrow snapshots `commission_base = gross_amount`.

## 3. Internal API — `POST /internal/pricing/quote` (X-Internal-Token)

```
Request:  { vertical, supplierOrganizationId, grossAmount, currency,
             customerId, promoCode (nullable), orderId (nullable for confirm) }
```

- If `orderId` is null → **preview**: compute discount/tax, **no** promo usage reservation.
- If `orderId` is non-null → **confirm**: compute, reserve promo usage atomically with order creation (one-shot increment of `promo_codes.uses_count`, idempotent on same `orderId`).

```
Response: { netAmount, discountLines: [{ description, amount, fundedBy }],
             taxLines: [{ taxType, amount, inclusive }],
             promoReservationId (nullable), orderId (echo) }
```

Idempotency: if called again with the same `orderId` (before the order is confirmed in package-booking), return the previously computed quote. On order cancellation, the caller must invoke `POST /internal/pricing/reserve/{reservationId}/release`.

**Error codes:** 422 `PROMO_CODE_INVALID`, `PROMO_CODE_EXPIRED`, `PROMO_CODE_MAX_USES_REACHED`, `MIN_ORDER_NOT_MET`, `DISCOUNT_EXCEEDS_PARTNER_SHARE`. No promo code -> always 200 with zero discount (never an error).

## 4. Admin API

- `GET /api/admin/campaigns` — list all (incl. expired). `GET /api/admin/campaigns/{id}`.
- `POST /api/admin/campaigns` — create campaign (ADMIN only, `X-User-Role: ADMIN`).
- `PUT /api/admin/campaigns/{id}` — update (only `name`, `description`, `end_at`; `discount_type`, `discount_value`, `vertical`, `scope_org_id`, `start_at`, `funded_by` are immutable after creation).
- `DELETE /api/admin/campaigns/{id}` — soft-delete (`end_at` set to NOW()).
- `GET /api/admin/promo-codes?campaignId=` — list codes for a campaign.
- `POST /api/admin/promo-codes` — create code(s) for a campaign (bulk accept array).
- `GET /api/admin/tax-rates` — list all configured tax rates. `POST /api/admin/tax-rates` — create rate.
- `GET /api/admin/commission-rates` — delegated to payment-service (unchanged).

## 5. Customer API

- `POST /api/pricing/preview {vertical, supplierOrganizationId, grossAmount, currency, promoCode}` (X-User-Id required, public route through api-gateway) -> same shape as the internal quote in preview mode (no reservation). Used by the checkout UI to show discount lines. It is advisory only: the binding computation happens server-side at order creation (package-booking calls the internal quote with the hold amount, never a client-supplied amount).
- Order create endpoints (`POST /api/orders/hotel|car|flight|bus`) accept an optional `promoCode`.

## 6. Order item fields (package-booking-service)

New columns on `order_items`:

| Column | Type | Description |
|--------|------|-------------|
| `gross_amount` | NUMERIC(12,2) | Supplier hold amount (before any discount) |
| `discount_amount` | NUMERIC(12,2) | Total discount applied (always >= 0; 0 if none) |
| `discount_funded_by` | VARCHAR(20) | PLATFORM or PARTNER (null if discount_amount = 0) |
| `net_amount` | NUMERIC(12,2) | gross_amount - discount_amount (what customer pays) |
| `tax_amount` | NUMERIC(12,2) | Informational tax portion INCLUDED in net (null when no tax rate is configured). Never added on top. |

`subtotal` = `net_amount * quantity`. Order `total_amount` = sum of item subtotals. Supplier prices are consumer prices with taxes included (that is how partners enter them today); tax_amount is only a breakdown for invoices/receipts.

At order creation:
1. `createHotelOrder` / `createCarOrder` / flight / bus -> hold returns `totalAmount` (supplier consumer price).
2. Call `POST /internal/pricing/quote` with `grossAmount = hold.totalAmount`, `orderId = new order.id`, `promoCode` from the request.
3. Store `gross_amount = hold.totalAmount`, `discount_amount`, `net_amount`, `discount_funded_by`, `tax_amount` from the quote response.
4. Set `unit_amount = net_amount`.
5. If pricing-service is unreachable: when no promo code was given, proceed with discount 0 (gross = net, tax null) so sales never depend on pricing-service; when a promo code was given, fail with 503 `PRICING_UNAVAILABLE` (do not silently drop the customer's code).
6. On order cancel/expiry, release the promo reservation (idempotent).

**Never recompute prices later.** All monetary fields on the order item are written once at creation and immutable.

## 7. Ledger effects

When payment-service captures via `PaymentIntentService.plan()`:
- Customer charge = `net_amount`.
- Commission = `gross_amount * resolvedCommissionRate` (snapshot).

**Platform-funded discount:** ledger lines per item: CUSTOMER_PAYMENT `net`; PLATFORM_COMMISSION `gross*rate`; PLATFORM_DISCOUNT_COST `discount` (debit platform); SUPPLIER_PAYABLE `gross - gross*rate`. Balanced: `net + discount = gross*rate + (gross - gross*rate)`.

**Partner-funded discount:** CUSTOMER_PAYMENT `net`; PLATFORM_COMMISSION `gross*rate`; SUPPLIER_PAYABLE `net - gross*rate`. Balanced: `net = gross*rate + (net - gross*rate)`.

Refunds reverse the same lines proportionally; the promo usage is released on full refund.

## 8. Frontend pages

- **Admin** (`/admin/campaigns`): table of campaigns (name, vertical, discount, scope, funded_by, dates, active status). Create/edit forms. Promo code management sub-section. Tax rates configuration.
- **Checkout**: promo code input on the existing order flows (hotel offer, car offer, flight/bus checkout). On entry -> `POST /api/pricing/preview` -> show discount line. Price shown as "vergiler dahil"; tax breakdown only when a rate is configured.
- **Customer orders**: show item breakdown (gross, discount, net, tax) on the order detail page.

## 9. Open user decisions

| Decision | Recommendation | Rationale |
|----------|---------------|-----------|
| **Vergi oranlari (KDV, konaklama vergisi)** | Supplier prices are tax-inclusive consumer prices. Missing tax rate does NOT block sales; the receipt simply shows no tax breakdown (`tax_amount` null) and the admin dashboard shows a warning "Vergi oranlari tanimli degil". Rates are entered by the user via `/api/admin/tax-rates`; the system never ships default numbers. | Blocking all sales on an unconfigured rate would stop the product for a reporting field; the customer price does not change with the rate because it is inclusive. Invoice/e-arsiv requirements are a separate decision (needs accountant). |
| **Varsayilan kampanya fonlamasi** | Each campaign must state `funded_by` explicitly at creation (no default). | Affects partner payout (section 2). |
| **Partner-funded campaigns consent** | A PARTNER-funded campaign scoped to an org requires that org's owner to accept it (partner panel) before it activates. Until implemented, admin can only create PLATFORM-funded campaigns. | A partner must never lose payout without consent. |

## 10. Error codes (complete)

| Code | HTTP | Where |
|------|------|-------|
| `NO_CAMPAIGN_MATCHING` | (unused) | kept out: no matching campaign is simply zero discount |
| `PROMO_CODE_EXPIRED` | 422 | /internal/pricing/quote — code past valid_until |
| `PROMO_CODE_MAX_USES_REACHED` | 422 | /internal/pricing/quote — code or global limit hit |
| `MIN_ORDER_NOT_MET` | 422 | /internal/pricing/quote — grossAmount < campaign.min_order_amount |
| `DISCOUNT_EXCEEDS_PARTNER_SHARE` | 422 | /internal/pricing/quote — partner-funded discount larger than partner payout |
| `PRICING_UNAVAILABLE` | 503 | package-booking order create — promo code given but pricing-service unreachable |
| `PROMO_CODE_INVALID` | 422 | unknown code, wrong vertical or wrong partner scope |
| `PROMO_ALREADY_RESERVED` | 409 | /internal/pricing/quote — same orderId already has a reservation |
| `RESERVATION_NOT_FOUND` | 404 | /internal/pricing/reserve/{id}/release |
| `COMMISSION_RATE_MISSING` | 422 | payment-service (unchanged) |
