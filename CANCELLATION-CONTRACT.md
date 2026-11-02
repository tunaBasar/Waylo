# Cancellation contract (queue row 8)

Source decisions: PRODUCT-DISCUSSION-NOTES.md "Ödeme modeli: emanet + QR" (cancel before scan: refund from escrow per policy, never claw back from the seller; no-show: automatic decision per policy after the window; every money movement is an append-only ledger entry). Money `NUMERIC(12,2)`, `RoundingMode.HALF_EVEN`. Times `Europe/Istanbul`.

## 1. Policy snapshot (package-booking-service, Flyway V8)

`order_items` gains `cancellation_policy` (`FREE_CANCELLATION | FREE_UNTIL | NON_REFUNDABLE`, default `FREE_CANCELLATION`), `cancel_free_hours_before` (int, required iff `FREE_UNTIL`), `cancel_outcome` (`REFUND | NO_REFUND | NO_SHOW`), `refund_amount`. The policy is written once at order creation and never re-read from the supplier. Old rows keep `FREE_CANCELLATION` (= today's full refund).

Source per vertical:
- HOTEL: the rate plan `CancellationPolicy` (`FREE_CANCELLATION | NON_REFUNDABLE`), exposed as `cancellationPolicy` on the hotel-service hold response (read from the quote snapshot). `freeCancellationDaysBefore` of the rate plan is NOT evaluated yet (open decision 2).
- CAR, BUS, FLIGHT: no supplier exposes cancellation terms today, so the platform configuration applies: `waylo.cancellation.{car,bus,flight}.policy` (env `WAYLO_CANCELLATION_{CAR,BUS,FLIGHT}_POLICY`) and `...free-hours-before` (env `WAYLO_CANCELLATION_{CAR,BUS,FLIGHT}_FREE_HOURS_BEFORE`). Unset means `FREE_CANCELLATION`. No commercial numbers are shipped. If a supplier hold response later carries terms, they take precedence over configuration.

`FREE_UNTIL`: full refund while `now <= serviceStart - hours`, else no refund. `serviceStart`: hotel check-in 00:00, car/bus `pickupAt`, flight `departureAt`.

## 2. Outcomes (payment-service)

Escrow state machine stays `HELD -> RELEASED | REFUNDED`. `escrow_items.settlement_reason` (V6) records `REDEMPTION | TICKETING | CANCEL_NO_REFUND | NO_SHOW | CANCEL_REFUND`.
- Full refund: `HELD -> REFUNDED`, ledger `DR CUSTOMER_FUNDS_HELD / CR CUSTOMER_REFUND` for the charged net amount (promo included, the customer gets back what was paid).
- No refund: `HELD -> RELEASED`, normal release ledger (supplier share to `SUPPLIER_PAYABLE`, commission to `PLATFORM_COMMISSION`, platform-funded discount to `PLATFORM_DISCOUNT_COST`). Commission and supplier amounts are the snapshots taken at capture.
- Partial refunds do not exist. Discounted items: refund is all or nothing of the charged net, so ledger reversal is exact (proportional share is 100% or 0%).
- Promo: usage is released (pricing-service release) only when every item was fully refunded. A no-refund item consumes the promo.
- Seller money is never clawed back. A redeemed (`RELEASED` by scan/ticketing) item cannot be cancelled: 422 `ESCROW_ALREADY_RELEASED`.
- Idempotent: repeating cancel returns the recorded outcome and writes no ledger entries.

Internal endpoints (`X-Internal-Token`): `POST /internal/escrow-items/{orderItemId}/cancellation-preview|cancel` body `{policy, freeHoursBefore, serviceStartAt}`, `POST .../no-show`. Result: `{outcome REFUND|NO_REFUND, cancellable, refundAmount, supplierAmount, commissionAmount, chargedAmount, currency, status}`.

## 3. Customer API (package-booking-service)

- `GET /api/orders/{id}/cancellation-preview` (owner only) -> `{orderId, cancellable, blockedReason, currency, totalRefund, items[{orderItemId, description, policy, freeHoursBefore, outcome, chargedAmount, refundAmount}]}`. Read only. `blockedReason`: `ALREADY_CANCELLED | TERMINAL_STATE | ALREADY_USED`. A PENDING order previews 0 refund (nothing was paid).
- `POST /api/orders/{id}/cancel` (existing) now decides per item from the stored snapshot. It first checks every item is still cancellable, then cancels all. A second cancel returns 422 `ALREADY_CANCELLED`. The preview and the actual refund use the same evaluator, so they match unless the clock crosses a `FREE_UNTIL` deadline between the two calls.

## 4. No-show

Scheduled job in package-booking-service (`waylo.cancellation.no-show-interval-ms`, default 60000): vouchers still `ACTIVE` with `valid_until < now` call payment-service `no-show`, then the voucher becomes `NO_SHOW` and the item `cancel_outcome=NO_SHOW`. Escrow is released to the partner (minus commission) for BOTH `NON_REFUNDABLE` and `FREE_CANCELLATION`: the service time passed and the customer did not cancel in time. Failure leaves the voucher `ACTIVE`, the next run retries. Repeats are no-ops.

## 5. Frontend

`/orders` shows the policy text per item and a cancel button on CONFIRMED and PENDING orders. The button loads the preview and shows "X TL iade edilecek" (or "İade yapılmayacak"), then asks for confirmation before `POST cancel`.

## Open user decisions

1. CAR, BUS and FLIGHT have no configured cancellation policy, so they default to `FREE_CANCELLATION`. Provide per-vertical policy and hours (and whether suppliers should supply them).
2. Hotel `freeCancellationDaysBefore` exists on rate plans but is not evaluated: `FREE_CANCELLATION` means free until the service time. Decide whether the days-before cutoff should apply.
3. Partial refunds (e.g. keep first night or a cancellation fee) are out of scope.
4. A refund of an already released flight ticket with the external supplier is not handled.
