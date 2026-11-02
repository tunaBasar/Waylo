# Check-in contract (queue row 11)

Binding for checkin-service, package-booking-service (internal read model), frontend and `scripts/test-checkin.py`. Check-in applies to Order FLIGHT items (FLIGHT-SUPPLIER-CONTRACT.md). The legacy PackageBooking check-in endpoints stay but answer `Deprecation: true`.

## 1. Window (internal supplier flights)

Online check-in is open from **departure - 24h** to **departure - 1h** (both edges inclusive), using the item's scheduled `departureAt`.

- Why: 24h is the common airline norm and leaves time to fix a wrong detail. It closes 1h before so desks/gates stay the authority near departure, and it sits inside the voucher window (departure - 3h .. +1h) only at its end, so the QR is not needed before check-in.
- **Open user decision (commercial choice):** the 24h / 1h numbers. They are constants in `OrderCheckinService`, one place to change.
- Before opening: 422 `CHECKIN_NOT_OPEN`. After closing: 422 `CHECKIN_CLOSED`.

## 2. Who

Only the order owner (`orders.customer_id == X-User-Id`), per FLIGHT order item. Others (including admin) get 403 `FORBIDDEN`. Non FLIGHT item: 422 `NOT_A_FLIGHT_ITEM`.

## 3. Result

- A `checkins` row keyed by `order_item_id` (unique): status `CHECKED_IN`, `seat_number` (seat confirmed from the item, no seat change here), `checked_in_at`, plus snapshot `order_id, pnr, passenger_name, flight_number, departure_at`.
- **The boarding credential stays the existing voucher QR** (order item voucher). No second code is issued. Check-in does not touch money or escrow, the boarding scan still releases escrow.
- Idempotent: a repeat returns the same row (200), `checked_in_at` unchanged, even after the window closed or after boarding.

## 4. Blocked states

| Condition | Response |
|---|---|
| order CANCELLED, item has cancel outcome (REFUND, NO_SHOW...), voucher VOID | 409 `ORDER_ITEM_CANCELLED` |
| voucher REDEEMED (already boarded) and no prior check-in | 409 `ALREADY_BOARDED`; an existing check-in is returned unchanged |
| order not CONFIRMED / no active voucher | 409 `ORDER_NOT_CONFIRMED` |
| supplier != INTERNAL (paid external supplier) | 409 `CHECKIN_AT_AIRLINE` (check-in is airline-side; adapter is disabled anyway) |
| unknown item | 404 |

Check order: owner, vertical, supplier, cancelled, existing check-in (idempotent), redeemed, confirmed, window.

## 5. API

- Customer: `POST /api/checkins/order-items/{orderItemId}` (`X-User-Id`), `GET /api/checkins/me`.
- Admin: `GET /api/admin/checkins?page&size` (`X-User-Role: ADMIN`), page `{items,page,size,total}`.
- Internal (package-booking, `X-Internal-Token`, never routed by the gateway): `GET /internal/orders/items/{orderItemId}` returns owner, order status, vertical, cancelOutcome, supplier, flight fields, passenger names, voucherStatus.
- Schema: checkin-service `V2__order_item_checkin.sql` adds `order_item_id` (unique), `order_id, pnr, passenger_name, flight_number, departure_at`; `booking_id`, `flight_id` become nullable.

## 6. Errors

422 `CHECKIN_NOT_OPEN`, `CHECKIN_CLOSED`, `NOT_A_FLIGHT_ITEM`; 409 `ORDER_ITEM_CANCELLED`, `ALREADY_BOARDED`, `ORDER_NOT_CONFIRMED`, `CHECKIN_AT_AIRLINE`; 403 `FORBIDDEN`; 503 `SERVICE_UNAVAILABLE` (package-booking down).
