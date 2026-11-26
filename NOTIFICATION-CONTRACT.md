# Notification contract (queue row 13)

notification-service consumes Kafka events and stores one `notifications` row per e-mail (`status` SENT in dev: the service marks rows SENT without real SMTP delivery for order mails, `retry` is admin only). Idempotent per `eventId` through `processed_events` (group `notification-service`). Texts are Turkish. A mail carries only what the customer needs: no phone, no address, no card data, **never a voucher code or QR payload** (bearer credentials). Order mails end with "QR kodunuz Siparişlerim sayfasında." and a line "Siparişinizi görmek için: {waylo.mail.app-base-url}/orders". The body is plain text, one item per line, HTML-escaped with newlines turned into `<br>` at delivery (`NotificationServiceImpl`), so passenger names cannot inject markup. Instants are shown in Europe/Istanbul as `dd.MM.yyyy HH:mm`, date-only values as `dd.MM.yyyy`, money in Turkish format (`5.280,00 TL`). The PUSH text is the first line of the body

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
| `supportCaseUpdated` kind `ANSWERED` (support-events) | crm-service outbox, partner or admin reply on an RM-36 case | "Destek talebiniz yanıtlandı": case subject + hint to open the Destek page + link `{waylo.mail.app-base-url}/support` (added 2026-11-26; the reply text is not mailed) | case customer (crm e-mail lookup) | added 2026-11-24 |
| `supportCaseUpdated` kind `CLOSED` (support-events) | crm-service outbox, admin close | "Destek talebiniz kapatıldı" | case customer | added 2026-11-24; customer messages and the customer's own close send nothing |
| `orderPaid` (order-events) | package-booking-service, order CONFIRMED | "Yeni rezervasyon": that organization's items only (description, dates, amount) + link `{app-base-url}/partner/orders`. One mail per supplier org per order. No customer e-mail/phone and no customer name (not in the event) | partner owners (auth contacts: ACTIVE ORGANIZATION_OWNER / HOTEL_OWNER, `GET /internal/organizations/{id}/contacts`) | added 2026-11-26; own processed_events group `notification-service-partner`; lookup failure = log, skip, ack |
| `orderCancelled` of a paid order (order-events) | package-booking-service cancel | "Rezervasyon iptal edildi": that organization's items only + refund outcome (sum of the items' per-item refund) + link `{app-base-url}/partner/orders` | partner owners (auth contacts) | added 2026-11-26; items carry `supplierOrganizationId` and `refundAmount` (additive) |
| `supportCaseUpdated` kind `CREATED` (support-events) | crm-service outbox, customer opens a case | "Yeni müşteri destek talebi": case subject, order no + link `{app-base-url}/partner/support` (no message text) | partner owners (auth contacts, event `supplierOrganizationId`) | added 2026-11-26; authorRole CUSTOMER; the customer gets no mail |
| `supportCaseUpdated` kind `CUSTOMER_MESSAGE` (support-events) | crm-service outbox, customer adds a message | "Destek talebinde yeni müşteri mesajı": case subject + link `{app-base-url}/partner/support` (no message text) | partner owners (auth contacts) | added 2026-11-26; skipped when the case has no supplier organization |
| `BOOKING_CREATED` / `BOOKING_CANCELLED` (booking-events) | old PackageBooking model | "Rezervasyonunuz alindi/iptal edildi" | booking customer | **LEGACY**, kept, harmless when no events arrive |
| Check-in reminder | - | not built | - | skipped (optional) |

Failure handling: a failed crm lookup logs and skips the mail but still acknowledges the event (no poison loop). An event is recorded in `processed_events` only after the notification row is written or deliberately skipped.
Auth e-mails (`auth-events`) are idempotent by `eventId` and are not sent when the event is older than 24h (`waylo.mail.auth-event-max-age`) or has no `occurredAt` (replay safety).
Partner e-mails (`notification-service-partner` group) are likewise not sent, but are marked processed, when the Kafka record timestamp (producer time, `occurredAt` of the typed event is not reliable) is older than 24h (`waylo.mail.partner-event-max-age`) or missing.

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
| `GET /api/notifications/status/{status}` (e.g. `FAILED`, `GAVE_UP`) | 200 | 403 | 401 |

## 3. Delivery status and retry policy (RM-30)

Statuses: `PENDING`, `SENT` (provider accepted), `FAILED` (transport error, will be retried), `SUPPRESSED` (reserved/test domain, no device, push disabled: never retried), `GAVE_UP` (terminal, retries exhausted). Columns: `retry_count`, `next_attempt_at`, `last_error` (max 300 chars, exception class only, no addresses or other PII).

- `DeliveryRetryJob` runs every 60 s (`waylo.notifications.retry.interval-ms`). It claims up to `waylo.notifications.retry.batch-size` (20) rows with `status = FAILED AND retry_count < max AND next_attempt_at <= now` using `SELECT ... FOR UPDATE SKIP LOCKED` inside the sending transaction, so two instances never send the same row.
- The retry uses the same send path as the first attempt. `SENT` and `SUPPRESSED` rows are never picked and a `SENT` mail is never resent.
- Max attempts `waylo.notifications.retry.max-attempts` (default 5). Backoff after failed attempt n (counted from the first send): 1 m, 5 m, 30 m, 2 h, 12 h. When `retry_count` reaches the max and the send fails again, the row becomes `GAVE_UP` and `next_attempt_at` is cleared.
- Manual `POST /api/notifications/{id}/retry` (admin only) sets `next_attempt_at` to now and retries at once. It also works for `GAVE_UP` (a new failure keeps `GAVE_UP`), and answers 422-style business error for `SENT`.
- Admin view: `GET /api/notifications/status/FAILED` and `/status/GAVE_UP`.
- Metric `waylo.notifications.delivery{channel,outcome=sent|failed|gave_up}`. Logs carry the notification id only.
