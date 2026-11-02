# CRM contract

## Access (crm-service `CrmAccessFilter`, identity from gateway headers)
- ADMIN: all `/api/customers/**`. Any other user: only `GET /api/customers/me` (everything else 403, no identity 401).
- notification-service reads `GET /api/customers/{id}` with `X-Internal-Token` (GET only).
- `passport_number` is stored but never returned by any endpoint. `/me` returns name, email and loyalty fields only.

## Events
- auth-events `USER_UPDATED` (name/email change, carries `eventId`): crm updates the customer, idempotent via `processed_events`. auth-service has no phone field and no email-change flow yet.
- order-events `ORDER_ITEM_REDEEMED` (package-booking, after a voucher redeem commits; fail-soft): `orderId, orderItemId, customerId, currency, amount` (item gross).

## Loyalty (mechanism only, OPEN USER DECISION)
- `waylo.crm.loyalty.points-per-try` (env `CRM_LOYALTY_POINTS_PER_TRY`, default 0 = disabled): points per 1 TRY of a redeemed item, floored. Only TRY items count. Idempotent per order item.
- `tier-silver-points` / `tier-gold-points` / `tier-platinum-points` (env `CRM_LOYALTY_TIER_*`, default 0 = tier disabled). Tiers only move up.
- While disabled, `/me` hides points/tier and the profile page hides the card.
- Owner must decide: the actual rate, thresholds, currency handling, whether cancellations/refunds revoke points.
