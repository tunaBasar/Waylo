# Car rental v2 contract (queue row 7c)

Source decisions: PRODUCT-DISCUSSION-NOTES.md "Araç kiralama" + "Ödeme modeli". This file is the single contract for all 7c workers. Times are `Europe/Istanbul`. Money `NUMERIC(12,2)`, `RoundingMode.HALF_EVEN`. All JSON wrapped in the existing `ApiResponse` envelope. Error bodies use the existing `ErrorResponse` with an `error` code.

## 1. Partner types (auth-service)

`organizations` gains:
- `business_type` `HOTEL | CAR_RENTAL` (existing rows -> `HOTEL`), required on create.
- `legal_type` `COMPANY | INDIVIDUAL` (existing rows -> `COMPANY`), required on create.

`POST /api/organizations {name, businessType, legalType}`. `OrganizationResponse` and `/api/me/memberships` and admin org list expose both fields.

New permission `FLEET_MANAGE` (branches, vehicles, rates, documents of a CAR_RENTAL org): `ORGANIZATION_OWNER` only. `RESERVATION_READ` and `RESERVATION_REDEEM` keep their current rule (`ORGANIZATION_OWNER` of the org qualifies, hotel roles as today).
`FLEET_MANAGE` on a non-CAR_RENTAL org is denied. `HOTEL_CREATE` on a CAR_RENTAL org is denied.

Internal (X-Internal-Token): `GET /internal/organizations/{id}` -> `{id, name, status, businessType, legalType}`; 404 if missing.
Access check stays `GET /internal/access?userId&organizationId&permission` (hotelId omitted).

Individual partners are behind a legal gate: car-rental-service property `waylo.car.individual-partners-enabled` (env `CAR_INDIVIDUAL_PARTNERS_ENABLED`, default `false`). When false, offers of INDIVIDUAL orgs are never returned and holds for them return 422 `INDIVIDUAL_PARTNERS_DISABLED`. Partner management (branches, vehicles, documents) still works so they can prepare.

## 2. Car rental domain (car-rental-service, Flyway V5+)

Old model is replaced: platform-admin vehicle CRUD (`/api/vehicles/**`), `GET /api/vehicles/available`, direct `POST /api/rentals` are removed. Old `vehicles` rows have no owner: migration deletes them and the old rentals (dev seed data only; `scripts/seed-data.sh` must be updated to create a CAR_RENTAL partner instead).

Tables (all with `organization_id`, `created_at`, `updated_at`, optimistic `version` where edited):
- `branches`: id, organization_id, name, city, address, phone, open_time, close_time (same every day), active.
- `vehicles`: id, organization_id, home_branch_id, plate_number (unique, normalized upper/no spaces), brand, model, year, category, transmission, fuel_type, seat_count, active.
- `category_rates`: organization_id, branch_id, category, transmission, currency, daily_rate (>0), deposit_amount (>=0), km_limit_per_day (null = unlimited), extra_km_fee (null allowed), min_driver_age (18..99), min_license_years (0..50). Unique (branch_id, category, transmission).
- `one_way_routes`: organization_id, from_branch_id, to_branch_id, fee (>=0), currency. Unique pair. A different return branch is allowed only if a route row exists.
- `documents`: id, organization_id, vehicle_id (null for org-level), type, document_number, valid_until (date, null only where allowed), status `PENDING | APPROVED | REJECTED`, reject_reason, reviewed_by, reviewed_at, file_name (original, sanitized), content_type, size_bytes, storage_key (random UUID), uploaded_by.
- `rentals` (new shape): id, organization_id, customer_id, vehicle_id (assigned unit), pickup_branch_id, return_branch_id, category, transmission, pickup_at, return_at (timestamptz), chargeable_days, daily_rate, one_way_fee, total_amount, currency, deposit_amount, km_limit_per_day, driver_age, status `HELD | CONFIRMED | CANCELLED | EXPIRED`, hold_expires_at, idempotency_key (unique), order_correlation_id, version.

Enums: category `ECONOMY, COMPACT, MIDSIZE, FULLSIZE, SUV, MINIVAN, VAN, LUXURY`; transmission `MANUAL, AUTOMATIC`; fuel `PETROL, DIESEL, HYBRID, ELECTRIC, LPG`.

Document types:
- Org level, COMPANY: `RENTAL_AUTHORIZATION` (yetki belgesi, valid_until required), `TAX_CERTIFICATE` (valid_until optional).
- Org level, INDIVIDUAL: `IDENTITY_DOCUMENT` (valid_until required).
- Vehicle level, all: `VEHICLE_REGISTRATION` (ruhsat, valid_until = muayene date, required), `RENTAL_INSURANCE` (kasko, required), `TRAFFIC_INSURANCE` (zorunlu trafik, required).
Files: PDF, JPEG, PNG only, checked by magic bytes, max 10 MB, stored on volume `car_documents` mounted at `/data/car-documents`, never served without auth.

Sellable rule (computed at query time, so expiry closes listings automatically): an org is sellable if status ACTIVE (auth internal lookup, fail closed) and every required org-level type has an APPROVED document with valid_until null-or->= return date. A vehicle is sellable if active, its org is sellable, and every vehicle-level type has an APPROVED document with valid_until >= return date. Newest APPROVED document per type wins.

Pricing: `chargeable_days = ceil(minutes(return_at - pickup_at) / 1440)`, min 1, max 30. `total = chargeable_days * daily_rate + one_way_fee`. Currency of route fee must equal rate currency (validated on write). Deposit, km limit, extra km fee are informational (collected or checked by the partner at pickup) and shown on the offer and voucher.

Time rules: pickup_at >= now + 2h; return_at > pickup_at; minutes 00 or 30 only; pickup time within pickup branch open/close, return time within return branch open/close.

Unit availability (inside the hold, with `SELECT ... FOR UPDATE` on candidate vehicles): a vehicle is free for [pickup_at, return_at] if no HELD (unexpired) or CONFIRMED rental of it overlaps, and its location at pickup_at is the pickup branch (location = return_branch of its latest rental ending <= pickup_at, else home_branch), and its next rental starting >= return_at (if any) picks up at the requested return branch. Pick the first free sellable unit ordered by id.

## 3. HTTP API (car-rental-service, routed by api-gateway)

Partner (`X-User-Id`, access check `FLEET_MANAGE` on `{orgId}`, org must be CAR_RENTAL):
- `GET|POST /api/partner/organizations/{orgId}/branches`, `PUT /api/partner/organizations/{orgId}/branches/{id}`
- `GET|POST /api/partner/organizations/{orgId}/vehicles`, `PUT .../vehicles/{id}`, `POST .../vehicles/{id}/deactivate|activate`. Vehicle response includes `sellable` and `missingDocuments` (list of types).
- `GET|PUT /api/partner/organizations/{orgId}/rates` (PUT upserts a list of category_rates rows)
- `GET|PUT /api/partner/organizations/{orgId}/one-way-routes` (PUT replaces the full set)
- `GET /api/partner/organizations/{orgId}/documents`, `POST .../documents` multipart (`file`, `type`, `documentNumber`, `validUntil?`, `vehicleId?`), `GET .../documents/{id}/file`
- `GET /api/partner/organizations/{orgId}/rentals` (CONFIRMED and CANCELLED, newest first)
- `GET /api/partner/organizations/{orgId}/status` -> `{sellable, missingOrgDocuments, individualGateOpen}`

Admin (`X-User-Role: ADMIN`):
- `GET /api/admin/car-documents?status=PENDING`, `GET /api/admin/car-documents/{id}/file`, `POST /api/admin/car-documents/{id}/approve`, `POST /api/admin/car-documents/{id}/reject {reason}`
- `GET /api/admin/car-vehicles` (read-only, all orgs)

Public:
- `GET /api/car-branches?city=` -> active branches of sellable orgs `{id, organizationId, name, city, address, openTime, closeTime}`
- `GET /api/car-offers?pickupBranchId&returnBranchId&pickupAt&returnAt&driverAge` -> one offer per (category, transmission) that has a rate and at least one free sellable unit: `{pickupBranchId, returnBranchId, organizationId, category, transmission, exampleModel ("Fiat Egea veya benzeri", from the lowest-id free unit), seatCount, fuelType, chargeableDays, dailyRate, oneWayFee, totalAmount, currency, depositAmount, kmLimitPerDay, extraKmFee, minDriverAge, minLicenseYears, authorizationDocumentNumber (COMPANY only, regulation: show yetki belgesi)}`. Offers with `driverAge < minDriverAge` are excluded.

Customer (`X-User-Id`): `GET /api/rentals/me`, `GET /api/rentals/{id}` (owner or ADMIN). No direct create or cancel: lifecycle comes from orders.

Internal (X-Internal-Token, path `/internal/car-holds`):
- `POST /internal/car-holds {pickupBranchId, returnBranchId, category, transmission, pickupAt, returnAt, driverAge, principalUserId, idempotencyKey, orderCorrelationId}` -> `HoldDto`. Same idempotencyKey returns the same hold. Errors: 409 `NO_VEHICLE_AVAILABLE`, 422 `ROUTE_NOT_OFFERED`, `DRIVER_TOO_YOUNG`, `OUTSIDE_BRANCH_HOURS`, `INVALID_TIME`, `NO_RATE`, `INDIVIDUAL_PARTNERS_DISABLED`, `PARTNER_NOT_SELLABLE`.
- `POST /internal/car-holds/{id}/confirm` (HELD and unexpired -> CONFIRMED, else 409 `HOLD_EXPIRED`), `POST /internal/car-holds/{id}/release` (HELD or CONFIRMED -> CANCELLED, idempotent).
- `HoldDto {holdId, organizationId, pickupBranchId, returnBranchId, category, transmission, exampleModel, pickupAt, returnAt, chargeableDays, currency, totalAmount, depositAmount, kmLimitPerDay, status, expiresAt}`
- Hold TTL `waylo.car.hold-ttl-minutes` default 15; a scheduled job marks expired HELD rentals EXPIRED.

## 4. Orders, vouchers, payment (package-booking-service)

- `POST /api/orders/car {pickupBranchId, returnBranchId, category, transmission, pickupAt, returnAt, driverAge}`: same pattern as `createHotelOrder` (ADMIN -> 403 `ADMIN_CANNOT_PURCHASE`, hold first, order PENDING, item vertical `CAR`, `supplierOrganizationId = hold.organizationId`, `fulfillmentRef = holdId`, `productRef = pickupBranchId`, amount from hold only, release hold if order save fails). Order item stores `pickupAt`, `returnAt` (new nullable columns) and a Turkish description like `"Fiat Egea veya benzeri (ECONOMY, Manuel), 3 gun"`.
- markPaid confirms CAR holds, cancel releases them (mirror HOTEL branches).
- Voucher window for CAR: `pickupAt - 2h` to `returnAt`.
- Payment escrow uses vertical `CAR` (commission rate must exist, else the existing 422 `COMMISSION_RATE_MISSING`).

## 5. Frontend

- Partner create-organization form asks businessType (Otel / Arac kiralama) and legalType (Sirket / Sahis).
- Partner layout: HOTEL orgs keep the current hotel nav. CAR_RENTAL orgs get: Subeler, Filo, Fiyatlar, Tek yon, Belgeler, Kiralamalar, QR Okut, plus a banner from `/status` listing what is missing before listings go live (and the legal gate message for INDIVIDUAL when closed).
- Admin: "Belgeler" page (pending documents, open file, approve, reject with reason); vehicles page becomes read-only `/api/admin/car-vehicles`.
- Customer `/vehicles`: search (city -> pickup branch, return branch, pickup and return date+time in 30 min steps, driver age), offer cards with "X veya benzeri", total, deposit, km limit, yetki belgesi no, "Kirala" -> `POST /api/orders/car` -> `/orders`. `/rentals` lists `GET /api/rentals/me` with branch names and times. No fake data anywhere.
