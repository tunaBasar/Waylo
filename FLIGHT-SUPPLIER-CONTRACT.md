# Flight supplier contract (queue row 7d)

Source decisions: PRODUCT-DISCUSSION-NOTES.md "Ucuslar" + "Odeme modeli". Single contract for all 7d workers. Money `NUMERIC(12,2)`, `RoundingMode.HALF_EVEN`, currency `TRY` for INTERNAL. Times are UTC instants in APIs, shown in `Europe/Istanbul`. JSON in the existing `ApiResponse` envelope, errors in `ErrorResponse` with an `error` code.

Flow: offer (short lived) -> order -> payment -> ticketing (PNR / e-ticket) -> change/refund.

## 1. Where the code lives

- `flight-service` (8081): unchanged catalogue (airports, flights, admin CRUD). Existing admin flight and seat endpoints keep working untouched.
- `flight-seat-inventory-service` (8082): hosts the supplier layer, because seats and prices live there. Package `com.waylo.flightseatinventoryservice.supplier`.
- `payment-service`: supplier balance + ledger (phase B).
- `package-booking-service`: FLIGHT order (phase B).

## 2. `FlightSupplier` port

```java
interface FlightSupplier {
  String code();                                   // INTERNAL, later DUFFEL
  List<FlightOffer> searchOffers(OfferSearch s);   // from, to, date, adults (INTERNAL: adults == 1)
  FlightOffer getOffer(String offerId);            // re-price; OFFER_EXPIRED / INVALID_OFFER / FARE_CHANGED / NO_SEAT_AVAILABLE
  FlightHold hold(HoldCommand c);                  // price check + hold, idempotent per idempotencyKey
  FlightHold getHold(UUID holdId);
  FlightHold issueTicket(UUID holdId);             // PNR + e-ticket number, idempotent
  FlightHold cancel(UUID holdId);                  // drop hold or void ticket, idempotent
  RefundQuote refundQuote(UUID holdId);            // customerRefundAmount, supplierRecoverableAmount, rule
}
```

`FlightOffer {offerId, supplier, flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime, cabin, currency, totalAmount (per passenger), availableSeats, expiresAt}`.
`FlightHold {holdId, supplier, flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime, cabin, seatNumber, passengerFirstName, passengerLastName, currency, totalAmount, supplierNetAmount, status, expiresAt, pnr, ticketNumber}`.
`supplierNetAmount` is what we owe the supplier at ticketing. INTERNAL: always `0.00`.

### 2.1 Internal implementation (`InternalFlightSupplier`)

- Search: flight-service `GET /api/flights/search?from&to&date` (Feign), keep `SCHEDULED` flights departing later than `now + sales-cutoff` (default 120 min, `waylo.flight.sales-cutoff-minutes`), then one offer per cabin (`seat_type`) with the minimum seat price among free seats. Free = not closed and (available or hold expired).
- Offer id: stateless, HMAC-SHA256 signed `base64url(flightNumber|cabin|price|currency|expiresEpoch).base64url(sig)`, secret `waylo.flight.offer-secret` (env `FLIGHT_OFFER_SECRET`, falls back to `INTERNAL_API_TOKEN`). The price is inside the signature so a client cannot alter it. **Offer TTL 10 min** (`waylo.flight.offer-ttl-minutes`): long enough to fill a passenger form, short enough that prices do not go stale.
- Hold: price must equal the offer price or 409 `FARE_CHANGED`. Seat chosen by number (`SELECT ... FOR UPDATE` on that seat) or, when `seatNumber` is omitted, cheapest free seat of the cabin (`FOR UPDATE SKIP LOCKED`). Runs in a read-write `@Transactional`. A partial unique index on `seat_holds(seat_id) WHERE status IN ('HELD','CONFIRMED')` backs it up. **Hold TTL 15 min** (`waylo.flight.hold-ttl-minutes`), same as car.
- Idempotency: same `idempotencyKey` returns the same hold, no second seat is taken.
- `issueTicket` (called by confirm): HELD and unexpired -> CONFIRMED, seat becomes permanently unavailable (`hold_expires_at = null`), PNR (6 chars, no I/O/0/1) and e-ticket number (`WL` + 11 digits) assigned. Expired or cancelled -> 409 `HOLD_EXPIRED`. Repeat call returns the same ticket.
- `cancel`: HELD or CONFIRMED -> CANCELLED, seat freed; repeat is a no-op.
- `refundQuote` (INTERNAL): customer gets the full amount back until the QR is scanned (scan is the payment trigger, so before it nothing was paid out); `supplierRecoverableAmount = 0.00`, rule `INTERNAL_FULL_REFUND_BEFORE_SCAN`.
- `SeatHoldExpiryScheduler` (every 60 s) marks expired HELD holds EXPIRED and frees their seats, and still releases legacy per-seat holds.
- Existing `POST /api/seat-inventory/seats/{flight}/reserve` (direct per-user hold) stays for backward compatibility. It must not be used for orders.
- Only one passenger per order for now (error 422 `PASSENGER_COUNT_UNSUPPORTED` when `adults != 1`). Multi passenger = several order items later, no port change.

### 2.2 `PaidSupplierAdapter` (shape only)

`interface PaidSupplierAdapter extends FlightSupplier { BigDecimal supplierBalance(); String supplierOrderRef(UUID holdId); }`

- No implementation class, no bean, no network call, no fake data. Property `waylo.flight.paid-supplier.enabled=false`. A Duffel-like adapter later is one new `@Service` guarded by `@ConditionalOnProperty(waylo.flight.paid-supplier.enabled=true)` with credentials only in env.
- For a paid supplier: `hold` returns `supplierNetAmount` (what we pay) and `totalAmount` (what the customer pays); `issueTicket` is where we pay the supplier and it must fail with `SUPPLIER_BALANCE_INSUFFICIENT` before any ticket exists, or `FARE_CHANGED` (new total in the message and `newTotalAmount`) if the supplier price moved; `cancel` voids and `refundQuote` returns airline-rule amounts (customer refund vs supplier recoverable).
- Switching supplier = new adapter + config. HTTP API, order flow and frontend do not change; the order item stores `supplier` code and `supplierOrderRef`.

## 3. Money model

Customer pays us one total (escrow, as for hotel/car). Ledger is append-only.

- INTERNAL (admin) flights: payment trigger = **QR boarding scan**. Escrow held per item; scan -> commission (vertical `FLIGHT`, rate must exist else `COMMISSION_RATE_MISSING`) and payout to the seller. Cancel before scan -> full refund from escrow per policy. Ticketing (PNR) happens at `markPaid` (confirm) and costs the supplier nothing.
- External supplier flights: payment trigger = **ticket issuance**. At ticketing payment-service debits the supplier net fare from the supplier balance (ledger `SUPPLIER_PAYOUT`), and our margin (customer total - net) is recognised. If the balance is below the net fare: no ticket, order stops in `TICKETING_FAILED` with `SUPPLIER_BALANCE_INSUFFICIENT`, customer is refunded in full (or the order waits for a top-up if admin chooses). If the fare changed at ticketing: order goes `AWAITING_FARE_CONFIRMATION`; customer confirms the new total (pays the difference) or the order is cancelled and refunded. We never absorb the difference. Airline cancellation: supplier-recoverable amount is credited back to the supplier balance (`SUPPLIER_REFUND`) and the customer refund follows `refundQuote`.

### 3.1 Supplier balance (payment-service, phase B)

Tables (Flyway, new V-number):
- `supplier_accounts`: `supplier` (PK, e.g. `DUFFEL`), `currency`, `balance NUMERIC(12,2) CHECK (balance >= 0)`, `version`, `updated_at`. Row is created by the first top-up. `INTERNAL` has no account.
- `supplier_ledger`: `id`, `supplier`, `entry_type` (`TOPUP`, `TICKET_PAYOUT`, `REFUND_CREDIT`, `ADJUSTMENT`), `amount` (signed, balance delta), `balance_after`, `order_id`, `order_item_id`, `idempotency_key` (unique), `created_by`, `created_at`. Append-only (no update/delete), balance only changes together with a ledger row in one transaction under `SELECT ... FOR UPDATE` on the account row.

Endpoints:
- Admin (`X-User-Role: ADMIN`): `POST /api/admin/supplier-accounts/{supplier}/topups {amount, currency, reference, idempotencyKey}` -> account; `GET /api/admin/supplier-accounts` (all accounts + balance); `GET /api/admin/supplier-accounts/{supplier}/ledger?page&size`.
- Internal (X-Internal-Token): `POST /internal/supplier-payouts {supplier, orderId, orderItemId, amount, currency, idempotencyKey}` -> 200 or 422 `SUPPLIER_BALANCE_INSUFFICIENT` (body has `balance`, `required`), 404 `SUPPLIER_ACCOUNT_NOT_FOUND`; `POST /internal/supplier-credits` for refunds (same shape).
- A low balance threshold (`waylo.supplier.low-balance-threshold`, unset = no alert) is a later addition, not part of this contract.

## 4. Error codes

| HTTP | code | meaning |
|---|---|---|
| 409 | `OFFER_EXPIRED` | offer older than TTL, search again |
| 409 | `FARE_CHANGED` | seat price differs from offer; message carries the new amount |
| 409 | `NO_SEAT_AVAILABLE` | cabin sold out |
| 409 | `SEAT_ALREADY_RESERVED` | chosen seat taken |
| 409 | `HOLD_EXPIRED` | confirm after TTL or after cancel |
| 422 | `INVALID_OFFER`, `FLIGHT_NOT_BOOKABLE`, `SEAT_CABIN_MISMATCH`, `PASSENGER_COUNT_UNSUPPORTED` | business rule |
| 404 | `NOT_FOUND` | flight, seat or hold missing |
| 401 | `UNAUTHORIZED` | `/internal/**` without valid `X-Internal-Token` (fails closed if no token configured) |
| 403 | `ADMIN_CANNOT_PURCHASE` | admin creating a flight order (package-booking) |
| 422 | `SUPPLIER_BALANCE_INSUFFICIENT` | supplier balance below net fare (payment-service) |
| 503 | `SERVICE_UNAVAILABLE` | flight-service down |

`INVALID_OFFER` means the offer id is unreadable or its signature is wrong.

## 5. HTTP API

Public (flight-seat-inventory-service, gateway route `/api/flight-offers/**`, no auth):
- `GET /api/flight-offers?from=IST&to=ESB&date=2026-11-04&adults=1` -> `FlightOffer[]`, sorted by departure then price.
- `GET /api/flight-offers/{offerId}` -> re-priced `FlightOffer` (409 `OFFER_EXPIRED` / `FARE_CHANGED`).
- Airports for the search form: existing `GET /api/airports`.

Internal (X-Internal-Token, never routed by the gateway), path `/internal/flight-holds`:
- `POST /internal/flight-holds {offerId, seatNumber?, passengerFirstName, passengerLastName, principalUserId, idempotencyKey, orderCorrelationId}` -> `FlightHold`. Names: letters, space, apostrophe, hyphen, max 80, must be as on the ID.
- `GET /internal/flight-holds/{id}`, `POST /internal/flight-holds/{id}/confirm` (issues ticket), `POST /internal/flight-holds/{id}/release` (idempotent), `GET /internal/flight-holds/{id}/refund-quote`.

Admin: existing `/api/flights`, `/api/airports`, `/api/seat-inventory/seats` stay. New: supplier accounts in section 3.1.

Schema (flight-seat-inventory-service `V4__seat_holds_supplier.sql`): table `seat_holds` (id, idempotency_key unique, order_correlation_id, principal_user_id, seat_id FK, flight_number, seat_number, cabin, passenger names, currency, total_amount, departure/arrival time and airports, airline, status `HELD|CONFIRMED|CANCELLED|EXPIRED`, hold_expires_at, pnr, ticket_number, version, timestamps). The hold row snapshots flight data so a later flight edit does not rewrite history.

## 6. Orders, vouchers (package-booking-service, phase B)

- `POST /api/orders/flight {offerId, seatNumber?, passengerFirstName, passengerLastName}`: same pattern as `createHotelOrder` / car. ADMIN -> 403 `ADMIN_CANNOT_PURCHASE`. Order is not created without a successful hold. Hold failure maps the codes in section 4 through. Amount comes from the hold only. Release the hold if the order save fails. Idempotency key = `order-{correlationId}-flight`.
- Order item (vertical `FLIGHT`): `supplierOrganizationId` = the platform seller organization configured for flights, `fulfillmentRef = holdId`, `productRef = flightNumber`, new nullable columns `passengerFirstName`, `passengerLastName`, `flightNumber`, `departureAt`, `seatNumber`, `pnr`, `ticketNumber`, `supplier`, `supplierOrderRef`. Description: `"TK100 IST-ESB, 04.11.2026 09:30, koltuk 12A"`.
- markPaid confirms the hold (ticket issued, PNR and e-ticket number copied to the item). cancel releases it.
- Voucher / QR window for FLIGHT: **`departureAt - 3h` to `departureAt + 1h`**. Check-in desks and gates open about 3 h before an international departure and close 45-60 min before; the scan marks "service rendered" at boarding/check-in, so the window covers the full check-in to gate period plus 1 h grace for delayed flights (delays move the real time later, the departure time on the item is the scheduled one). A no-show after `departureAt + 1h` falls to the policy auto decision.
- Scanning is done by an operator of the seller organization (platform seller for internal flights) through the existing QR scan screen.

## 7. Frontend (phase B)

- Customer `/flights`: search (from/to airports, date, 1 passenger) -> offer cards (flight, times, cabin, price, "kalan koltuk" from `availableSeats`) -> `/flights/offer/[offerId]` passenger form (first/last name "kimlikteki gibi", optional seat picker from `GET /api/seat-inventory/seats/{flight}/available`) -> `POST /api/orders/flight` -> `/orders` -> pay. Show offer expiry countdown from `expiresAt`; on `OFFER_EXPIRED` or `FARE_CHANGED` show the message and return to the search. No fake data.
- Admin `/admin/supplier-balance`: accounts and balances, top-up form (amount, reference), ledger table. Shows "tedarikci bakiyesi yok" when no account exists (no invented numbers). The admin flight/airport/seat pages stay as they are.
- Customer `/orders` shows PNR and e-ticket number from the order item once present.

## 8. As built (phase B notes)

- Seller organization: package-booking `waylo.flight.seller-organization-id` (env `FLIGHT_SELLER_ORGANIZATION_ID`). Empty = `POST /api/orders/flight` answers 422 `FLIGHT_SELLER_NOT_CONFIGURED`. The org's owner scans boarding QRs. A FLIGHT commission rate for that org (or the FLIGHT default) must exist.
- Order item response gains `flight {flightNumber, departureAt, seatNumber, passengerFirstName, passengerLastName, pnr, ticketNumber, supplier}` (null for non-flight items). PNR and ticket appear after payment (markPaid confirms the hold).
- Gateway: `/api/flight-offers/**` (GET public) to the seat service, `/api/admin/supplier-accounts/**` to payment-service. `/internal/**` is never routed.
- Frontend: `/flights` (search over `/api/flight-offers`), `/flights/offer?id=` (countdown, passenger form, optional seat), `/admin/supplier-balance`.
- The external-supplier ticketing payout (`/internal/supplier-payouts`) exists and is tested, but nothing calls it until a paid adapter exists.
