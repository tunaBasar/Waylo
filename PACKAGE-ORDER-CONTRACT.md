# Package (cart) order contract (queue row 9)

Source: PRODUCT-DISCUSSION-NOTES.md "Ödeme modeli": the customer pays the whole package in ONE payment, escrow is per item, each item has its own QR. Builds on PRICING-CONTRACT.md and CANCELLATION-CONTRACT.md. Binding.

## 1. Model

A package order is an ordinary `Order` with N (1..10) `OrderItem`s of any vertical (HOTEL, CAR, FLIGHT, BUS), all in one currency. Nothing new is stored: every item carries its own supplier, hold ref, pricing snapshot, cancellation policy snapshot, voucher and escrow item. `order.quote_id` is null for package orders.

## 2. Create: `POST /api/orders/package`

```
{ items: [ { vertical: HOTEL|CAR|FLIGHT|BUS, ...fields of the single endpoint, flat } ], promoCode?, notes? }
HOTEL  quoteId
CAR    pickupBranchId, returnBranchId, category, transmission, pickupAt, returnAt, driverAge
FLIGHT offerId, seatNumber?, passengerFirstName, passengerLastName
BUS    tripId, fromSeq, toSeq, passengers[{seatNo,name,gender,identityNumber?}]
```
Customer only (ADMIN gets 403 `ADMIN_CANNOT_PURCHASE`), prices always come from the holds, never from the client. All item fields are validated before any hold is taken.

- Holds are taken item by item, in request order.
- If any hold (or the later pricing/currency/save step) fails, every hold already taken is released (compensation, best effort, supplier TTL reclaims the rest), the promo reservation is released, no order is stored, and the API returns the failing item: HTTP status of the cause (409/422/404/503), body `{status, error: <cause code>, message: "Item <i>: ...", itemIndex: <0-based>, ...}`. Failures not tied to one item (currency mismatch, promo invalid for every item) use the index of the item that triggered them, or 0.
- `order.hold_expires_at` = min of the holds' `expiresAt`.
- Response: the normal `OrderResponse` (201). Payment is the existing single flow: `POST /api/payments/orders/{orderId}/intent` is ONE intent for `order.total_amount` = sum of item nets. payment-service already plans one escrow item per order item, so it needed no multi-item change except section 4.

## 3. Promo code (decision: once per order, on the first item the code is valid for)

pricing-service binds a code to one vertical campaign and allows exactly one reservation per `orderId`. So "whole order" is impossible and "every item" would be rejected. Rule: the code is tried on the items in request order; `PROMO_CODE_INVALID` (wrong vertical or partner scope) moves on to the next item, the first item that accepts it carries the discount (and consumes the single reservation), all other items are priced without promo. Any other pricing error (EXPIRED, MAX_USES, MIN_ORDER, DISCOUNT_EXCEEDS_PARTNER_SHARE, 503 PRICING_UNAVAILABLE) fails the request with that item's index. Invalid for every item: 422 `PROMO_CODE_INVALID`. Commission stays on the gross price per item exactly as PRICING-CONTRACT.md (escrow snapshots per item), so a discount on one item never changes the others' partner payout.

## 4. Payment confirmed (`markPaid`) and confirm failures

`markPaid` confirms every hold (flight confirm issues the ticket). Single-item orders keep today's behaviour: a rejection fails the call and payment-service cancels the provider payment and rolls back.

For N > 1: if some confirm fails after the money was captured, that item alone is refunded and the rest is kept. Rule: the failed item gets `cancel_outcome=REFUND`, `refund_amount=net`, its hold is released best effort, it gets no voucher; `markPaid` returns its id in `refundItemIds`; payment-service then calls `EscrowService.refund` for it in the same transaction (a callback from package-booking would not see the uncommitted escrow row and would deadlock on the intent lock). The order is CONFIRMED, the intent becomes PARTIALLY_REFUNDED/etc. through the existing derivation. If ALL confirms fail the whole payment is rolled back as for a single item.
Justification: the customer paid per item with per-item escrow, the other suppliers did deliver their holds, and cancelling a whole trip because one segment expired would punish the customer and the healthy partners. The refund is the charged net, so it is exact (no partial amounts) and ledger-exact with the existing refund path. Any confirm error (rejection or supplier outage) is treated the same because holds expire and cannot be retried safely.

## 5. Vouchers, redemption, no-show

One voucher per confirmed item with its own window (`VoucherService.window`). Redeeming one item releases only that escrow item. No-show runs per item.

## 6. Cancellation

Per item from the stored snapshot, exactly CANCELLATION-CONTRACT.md. `GET /api/orders/{id}/cancellation-preview` returns one entry per item and the total refund; `POST /api/orders/{id}/cancel` first checks every item is still cancellable, then cancels all. Items already refunded at confirm time stay refunded (idempotent).

## 7. Admin list: `GET /api/admin/orders`

ADMIN only (`X-User-Role`), else 403. Params: `status`, `vertical` (any item), `from`, `to` (ISO dates on createdAt), `page` (0), `size` (20, max 100). Returns `{items:[{id, customerId, status, paymentState, currency, totalAmount, createdAt, items[{id, vertical, description, netAmount, promoCode, cancelOutcome, refundAmount}]}], page, size, total}`. `paymentState` is derived: PENDING/FAILED -> UNPAID; CONFIRMED/CANCELLED -> REFUNDED (all items refunded), PARTIALLY_REFUNDED, PAID (none), or UNPAID for a CANCELLED order that never had outcomes. No customer name: only `customerId` exists in this service.

## 8. Compatibility

`POST /api/orders/{hotel|car|flight|bus}` and every other order endpoint are unchanged. Legacy `/api/bookings` is deprecated (`Deprecation: true` header), no longer used by the frontend; the saga orchestrator and notification-service still call it (row 10).
