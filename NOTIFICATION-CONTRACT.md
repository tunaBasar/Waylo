# Notification contract (queue row 13)

notification-service consumes Kafka events and stores one `notifications` row per e-mail (`status` SENT in dev: the service marks rows SENT without real SMTP delivery for order mails, `retry` is admin only). Idempotent per `eventId` through `processed_events` (group `notification-service`). Texts are Turkish. A mail carries only what the customer needs: no phone, no address, no card data, **never a voucher code or QR payload** (bearer credentials). Order mails end with "QR kodunuz Siparişlerim sayfasında."

## 1. Event -> template -> recipient

| Event (topic) | Producer | Template / subject | Recipient | State |
|---|---|---|---|---|
| `ORDER_PAID` (order-events) | package-booking `markPaid` (new) | "Siparişiniz onaylandı": items (description, dates), per item amount, total, QR hint | order customer (e-mail via crm-service) | new |
| `ORDER_CANCELLED` with `paid=true` (order-events) | package-booking `cancel` | "Siparişiniz iptal edildi": `refundAmount` (sum of per item REFUND amounts) or "iade yapılmayacaktır" | order customer | new, amount taken from package-booking only (it already holds the per item decision from payment-service, one source) |
| `ORDER_CANCELLED` with `paid=false` | package-booking | none (nothing was paid) | - | decided |
| No-show release (voucher expired) | package-booking job | none: the customer did not use the service, escrow goes to the partner, a mail adds no value | - | decided: no message |
| `ORDER_CREATED`, `ORDER_ITEM_REDEEMED` | package-booking | none (crm handles loyalty) | - | ignored |
| `FLIGHT_CANCELLED` (flight-events) | flight-service | "Ucusunuz iptal edildi" | customers of CONFIRMED flight items | existing (row 10) |
| `EMAIL_VERIFICATION`, `PASSWORD_RESET`, welcome (auth-events) | auth-service | `email-verification.html`, `password-reset.html`, `welcome.html` | the user | existing |
| `PAYMENT_COMPLETED` / `PAYMENT_REFUNDED` (payment-events) | payment-service | log only, no mail: the order mail covers it | - | existing, unchanged |
| `BOOKING_CREATED` / `BOOKING_CANCELLED` (booking-events) | old PackageBooking model | "Rezervasyonunuz alindi/iptal edildi" | booking customer | **LEGACY**, kept, harmless when no events arrive |
| Check-in reminder | - | not built | - | skipped (optional) |

Failure handling: a failed crm lookup logs and skips the mail but still acknowledges the event (no poison loop). An event is recorded in `processed_events` only after the notification row is written or deliberately skipped.

## 2. Access rules `/api/notifications/**` (gateway sets `X-User-Id`, `X-User-Role`; route already exists)

| Endpoint | ADMIN | Customer | Anonymous |
|---|---|---|---|
| `POST /api/notifications` | 201 | 403 | 401 |
| `GET /api/notifications` (all) | 200 | 403 | 401 |
| `GET /api/notifications/me` | own | own | 401 |
| `GET /api/notifications/customer/{id}` | any | only own id, else 403 | 401 |
| `GET /api/notifications/booking/{orderId}` | all rows | only own rows | 401 |
| `GET /api/notifications/{id}` | any | own, foreign id gives 404 | 401 |
| `POST /api/notifications/{id}/retry` | 200 | 403 | 401 |
