# Bus vertical contract (queue row 7f)

Source decisions: PRODUCT-DISCUSSION-NOTES.md "Kullanici kararlari 3" (otobus dikeyi), "Odeme modeli: emanet + QR", roadmap 4b. Same shape as CAR-RENTAL-CONTRACT.md. Times are `Europe/Istanbul`. Money `NUMERIC(12,2)`, `RoundingMode.HALF_EVEN`. All JSON wrapped in `ApiResponse`; errors use `ErrorResponse` with an `error` code. Phase A (bus-service) is implemented. Sections 1, 4 (package-booking part), 5 and the gateway/compose bits are phase B.

## 1. Partner model (auth-service, phase B)

- `organizations.business_type` gains `BUS` (existing values `HOTEL | CAR_RENTAL`). `legal_type` is not used by bus: only `COMPANY` is accepted for `BUS` (individuals cannot be bus partners).
- New permission `BUS_MANAGE` (buses, trips, fares, documents of a BUS org): `ORGANIZATION_OWNER` only. Denied on non-BUS orgs. `HOTEL_CREATE` and `FLEET_MANAGE` are denied on BUS orgs. `RESERVATION_READ` / `RESERVATION_REDEEM` keep the current rule (OWNER qualifies) so the boarding scan reuses the existing QR redeem flow.
- Internal `GET /internal/organizations/{id}` already returns `businessType`; bus-service reads `status` and `businessType`. Access check stays `GET /internal/access?userId&organizationId&permission=BUS_MANAGE`.
- Legal gate: bus-service property `waylo.bus.sales-enabled` (env `BUS_SALES_ENABLED`, default `false`). When false: `GET /api/bus-trips` returns an empty list, the seat map returns 404, and `POST /internal/bus-holds` returns 422 `BUS_SALES_DISABLED`. Partner management (buses, trips, documents, status) still works so companies can prepare. `/status` exposes `salesEnabled`.

## 2. Domain (bus-service, port 8096, db `bus_db`, Flyway V1)

- `terminals`: id, city, name, active. Platform data managed by ADMIN (a terminal is a shared place, not owned by a company). Unique (city, name).
- `buses`: id, organization_id, plate_number (unique, upper, no spaces), brand, model, year, `layout` (`TWO_PLUS_TWO` = 4 seats per row, `TWO_PLUS_ONE` = 3 per row), row_count 5..20, seat_count (derived), active. Seats are numbered row by row left to right, so no seat table is needed. Neighbour seats are the 2-seat blocks (1-2, 3-4, 5-6...). In `TWO_PLUS_ONE` the third seat of each row has no neighbour. Seat plan cannot change while the bus has PUBLISHED trips.
- `trips` (sefer): id, organization_id, bus_id, code, status `PUBLISHED | CANCELLED`, departure_at (first stop departure), currency. A trip is created already published (no draft).
- `trip_stops`: trip_id, seq 0..n-1, terminal_id, arrival_at, departure_at. Times strictly increase along the route, first departure in the future, a terminal appears once, at least 2 and at most 20 stops.
- `trip_fares`: trip_id, from_seq, to_seq, price (>0). Unique pair, from < to.
- `bookings` (one per order item): id, organization_id, customer_id, trip_id, from_seq, to_seq, currency, total_amount, status `HELD | CONFIRMED | CANCELLED | EXPIRED`, hold_expires_at, idempotency_key (unique), order_correlation_id.
- `tickets` (one per seat): booking_id, trip_id, seat_no, from_seq, to_seq, passenger_name, gender `FEMALE | MALE`, identity_number (optional), price, status (mirrors the booking), hold_expires_at.
- `documents`: same columns as car (`bus_id` replaces `vehicle_id`).

### Pricing model: explicit fare per (from stop, to stop) pair
Chosen over a flat per-trip price and over per-km/per-leg sums. Reasons: intercity fares are not additive (Ankara-Istanbul is not the sum of Ankara-Bolu plus Bolu-Istanbul), companies publish a fare table, and it is trivial to validate and audit. The full-route fare (0 -> last) is required, intermediate pairs are optional: a pair without a fare is simply not sold (`SEGMENT_NOT_OFFERED`). Ticket price = fare of the booked pair, `total = fare * passengers`, computed only from the stored fare, never from the client.

### Seat inventory
A seat is taken for a segment when a CONFIRMED or unexpired HELD ticket of the same trip and seat overlaps it, with half-open segments `[from, to)`: a ticket 0->2 and one 2->4 on the same seat do not overlap (seat reused after the passenger leaves). All holds on a trip serialize on `SELECT ... FOR UPDATE` of the trip row; the idempotency key is re-checked after the lock is held.

### Gender and seat-neighbour rule
Turkish intercity practice: the company must not seat an unrelated passenger next to a passenger of the other gender in a 2-seat block (cinsiyet kurali, commonly applied by all major sellers, in practice not a statute). Rule enforced: if the neighbour seat holds an overlapping ticket of the other gender and the neighbour seat is not in the same booking, the hold is rejected with 422 `SEAT_GENDER_CONFLICT`. Passengers in the same booking may sit together regardless of gender (family, couples). This is why `gender` and `passenger_name` are mandatory per seat. See "Needs legal confirmation" for U-ETDS and identity data.

Max 5 seats per booking. Sales and holds close 30 minutes before the boarding stop departure (`waylo.bus.sales-close-minutes`).

## 3. Documents and sellability

Document types and levels:
- Org level (company): `TRANSPORT_AUTHORIZATION` (D1 yetki belgesi, "Otobusle sehirlerarasi tarifeli yolcu tasimaciligi", valid_until required), `TAX_CERTIFICATE` (valid_until optional).
- Bus level: `VEHICLE_REGISTRATION` (ruhsat, valid_until = muayene date, required), `VEHICLE_CARD` (tasit karti, required), `TRAFFIC_INSURANCE` (zorunlu trafik / Zorunlu Karayolu Tasimacilik Mali Sorumluluk Sigortasi, required), `SEAT_ACCIDENT_INSURANCE` (Karayolu Yolcu Tasimaciligi Zorunlu Koltuk Ferdi Kaza Sigortasi, required).

Research summary (web, not legal advice): scheduled intercity bus transport needs a transport authorization from the Ministry of Transport (D1 = scheduled intercity bus; D2 = unscheduled/irregular group transport, which does not cover timetabled ticket sales, so D2 holders are not accepted as bus companies here; B2 is international unscheduled). Every holder of these authorizations must carry the compulsory transport liability insurance and the compulsory seat personal accident insurance for each registered vehicle, and may not run the vehicle without them. Vehicles are registered on the company's tasit karti.

Files: PDF, JPEG, PNG by magic bytes, max 10 MB, stored under `waylo.bus.documents-dir` (volume `bus_documents` mounted at `/data/bus-documents` in phase B), never served without auth. Upload goes PENDING, ADMIN approves or rejects with reason. Newest APPROVED per type wins.

Sellable rule (computed at query time, expiry closes listings automatically): org is sellable if auth status ACTIVE, type BUS (auth lookup fails closed) and every org-level type has an APPROVED document with valid_until null or on/after the boarding date. A bus is sellable if active, its org sellable and every bus-level type APPROVED and valid on the boarding date. A trip is offered/held only if org and bus are sellable at its boarding date.

## 4. HTTP API

Partner, base `/api/bus-partner/organizations/{orgId}` (own prefix so the gateway never collides with car) (`X-User-Id`, `BUS_MANAGE` on `{orgId}`, org must be BUS):
- `GET /api/bus-partner/organizations/{orgId}/terminals` (active terminals, for the trip form)
- `GET|POST .../buses`, `PUT .../buses/{id}`, `POST .../buses/{id}/deactivate|activate` (response has `sellable`, `missingDocuments`)
- `GET|POST .../trips` (body `{busId, code, currency, stops:[{terminalId, arrivalAt, departureAt}], fares:[{fromSeq,toSeq,price}]}`), `POST .../trips/{id}/cancel` (409-like 422 `TRIP_HAS_TICKETS` when paid tickets exist: refunds go through the platform)
- `GET .../documents`, `POST .../documents` multipart (`file`, `type`, `documentNumber`, `validUntil?`, `busId?`), `GET .../documents/{id}/file`
- `GET .../bookings` (CONFIRMED and CANCELLED, newest first, with passengers)
- `GET .../status` -> `{sellable, missingOrgDocuments, salesEnabled}`

Admin (`X-User-Role: ADMIN`): `GET /api/admin/bus-documents?status=`, `GET .../bus-documents/{id}/file`, `POST .../approve`, `POST .../reject {reason}`, `GET /api/admin/bus-vehicles` (read-only), `GET|POST /api/admin/bus-terminals`, `PUT /api/admin/bus-terminals/{id}`.

Public: `GET /api/bus-sales-status` -> `{enabled}` (legal gate for the UI), `GET /api/bus-terminals` (active, empty while the gate is closed), `GET /api/bus-trips?fromCity&toCity&date=YYYY-MM-DD` -> `TripOffer {tripId, organizationId, companyName, code, fromSeq, toSeq, fromTerminalId, toTerminalId, fromCity, toCity, departureAt, arrivalAt, price, currency, availableSeats, busBrand, busModel, layout}` for sellable trips whose route passes fromCity then toCity and has a fare for that pair, boarding on that date, sales open. `GET /api/bus-trips/{id}/seats?fromSeq&toSeq` -> `{layout, rowCount, seats:[{seatNo, available, occupantGender}]}` (occupantGender drives the UI neighbour hint).

Customer (`X-User-Id`): `GET /api/bus-bookings/me`, `GET /api/bus-bookings/{id}` (owner or ADMIN). No direct create or cancel: lifecycle comes from orders.

Internal (X-Internal-Token), `/internal/bus-holds`:
- `POST {tripId, fromSeq, toSeq, passengers:[{seatNo, name, gender, identityNumber?}], principalUserId, idempotencyKey, orderCorrelationId}` -> `HoldDto {holdId, organizationId, tripId, fromSeq, toSeq, fromTerminalId, toTerminalId, fromCity, toCity, fromTerminalName, toTerminalName, companyName, boardingAt, arrivalAt, seatNos, currency, totalAmount, status, expiresAt}`. Same idempotencyKey returns the same hold.
- `POST /{id}/confirm` (HELD and unexpired -> CONFIRMED, else 409 `HOLD_EXPIRED`), `POST /{id}/release` (HELD or CONFIRMED -> CANCELLED, idempotent). TTL `waylo.bus.hold-ttl-minutes` default 15, scheduled job marks expired HELD bookings EXPIRED.

## 5. Orders, vouchers, payment (package-booking-service, phase B)

- `POST /api/orders/bus {tripId, fromSeq, toSeq, passengers:[{seatNo,name,gender,identityNumber?}]}`: same pattern as `createCarOrder` (ADMIN -> 403 `ADMIN_CANNOT_PURCHASE`, `BusHoldClient` hold first, order PENDING, item vertical `BUS`, `supplierOrganizationId = hold.organizationId`, `fulfillmentRef = holdId`, `productRef = tripId`, amount from the hold only, release hold if the order save fails). Item stores `pickupAt = hold.boardingAt` and `returnAt = hold.arrivalAt` (existing nullable columns) and a description like `"Ankara - Istanbul, 02.02.2027 22:00, koltuk 5, 6"`.
- markPaid confirms BUS holds, cancel releases them (mirror CAR).
- Voucher / QR window for BUS: `boardingAt - 2h` to `boardingAt + 1h`. One QR per booking item (the company scans once per booking and sees the passenger list from `GET .../bookings`); escrow released at scan.
- Payment escrow vertical `BUS` (payment-service has it); missing commission rate -> existing 422 `COMMISSION_RATE_MISSING`.

## 6. Error codes

422: `BUS_SALES_DISABLED`, `PARTNER_NOT_SELLABLE`, `TRIP_NOT_AVAILABLE`, `SALES_CLOSED`, `INVALID_SEGMENT`, `SEGMENT_NOT_OFFERED`, `INVALID_SEAT`, `DUPLICATE_SEAT`, `SEAT_GENDER_CONFLICT`, `INVALID_STOPS`, `INVALID_FARES`, `INVALID_TERMINAL`, `BUS_INACTIVE`, `BUS_HAS_TRIPS`, `TRIP_HAS_TICKETS`, `INVALID_DOCUMENT`, `DOCUMENT_TYPE_NOT_ALLOWED`. 409: `SEAT_TAKEN`, `HOLD_EXPIRED`, `DOCUMENT_ALREADY_REVIEWED`, `CONCURRENT_MODIFICATION`. 503: `AUTH_UNAVAILABLE`. 413/415/400 for files as in car.

## 7. Frontend (phase B or later)

- Partner create-organization: businessType "Otobus firmasi" (legalType fixed to Sirket).
- BUS partner nav: Otobusler, Seferler (stops + fare table), Belgeler, Biletler, QR Okut, status banner (missing documents, "Satis henuz acik degil" while `salesEnabled` is false).
- Admin: Belgeler page shared pattern (bus-documents), Duraklar (terminals) CRUD, read-only buses.
- Customer `/otobus`: city to city + date search, offer cards, seat map (2+2 / 2+1) with gender choice and neighbour hint, passenger form, "Satin al" -> `POST /api/orders/bus`. Hidden entirely while sales are disabled.

## 8. Needs legal confirmation

1. Authorization type: is D1 the exact required document for a company selling scheduled intercity tickets, and are D2/other types ever acceptable? Document naming (yetki belgesi, tasit karti) and whether the platform must display the authorization number to buyers (as car rental does).
2. Zorunlu koltuk ferdi kaza sigortasi and tasimacilik mali sorumluluk sigortasi: confirmed mandatory in sources, but the precise validity checks the platform must do (policy per vehicle, per period) need a lawyer's reading.
3. U-ETDS: scheduled operators must notify the passenger list to the Ministry system before departure. Who sends it (company or platform), and whether we must hand over TC kimlik no. We store `identity_number` as optional only. If required, it becomes mandatory and needs KVKK consent text and retention rules.
4. Gender seating rule: widely practiced, but the legal basis and permitted exceptions (same booking, children) are not confirmed. Current rule is an assumption.
5. Intermediary status: whether selling tickets on behalf of companies needs a seyahat acentasi (TURSAB) or similar license, and consumer-law items (mesafeli satis, cayma hakki exemptions for dated transport, refund and change policy, cancellation by company). Same gate as the `BUS_SALES_ENABLED` flag.
6. Refund policy when the company cancels a trip with paid tickets (currently blocked with `TRIP_HAS_TICKETS`, to be done through the platform; policy undecided).
7. Fiyat/komisyon tax treatment (KDV on bus tickets) is the open decision already listed in the notes.

## 9. Phase B change list (shared paths)

auth-service (BUS type + BUS_MANAGE), docker-compose (bus-service, `bus_db`, volume `bus_documents`, `BUS_SALES_ENABLED`), db init script, api-gateway routes (`/api/bus-partner/**`, `/api/bus-trips/**`, `/api/bus-terminals`, `/api/bus-bookings/**`, `/api/admin/bus-**`), package-booking-service (BusHoldClient, `/api/orders/bus`, markPaid/cancel, voucher window), payment-service (none expected), seed + `scripts/test-bus.py`, boot.sh/dev-up entries, frontend.
