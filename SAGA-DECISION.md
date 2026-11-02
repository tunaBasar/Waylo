# Saga orchestrator decision

**Decision: retire `package-booking-saga-orchestrator` from the runtime path.**

Why: the Order flow in package-booking-service already does synchronous orchestration
(holds taken in-request, compensation on failure, one payment, escrow + QR per item).
The saga only flips step statuses on the legacy PackageBooking model and reserves nothing,
so keeping it running adds a second, misleading source of truth. A reconciliation watcher
would be new code, new endpoints and new tests for a risk the hold expiry already covers.

What changed:
- The module still compiles and its tests still run (`./gradlew :package-booking-saga-orchestrator:test`).
- `docker-compose.yml`: service has `profiles: ["legacy-saga"]`, so `docker compose up` and `scripts/dev-up.sh` never start it. Nothing `depends_on` it.
- Re-enable: `docker compose --profile legacy-saga up -d --no-deps package-booking-saga-orchestrator` (or `./boot.sh start`, which still lists it as phase 3).
- Nothing calls it: frontend and scripts have no `/api/sagas` use. The gateway route `/api/sagas/**` is kept (returns 503 while the service is down).
- Legacy `/api/bookings` code, tables and `Deprecation: true` header are kept.

Leftover `/api/bookings` callers: only the saga's `PackageBookingClient` (retired) and tests/docs
(`scripts/test-package-order.py` asserts the Deprecation header, gateway security tests use the path as a sample).
notification-service now uses `GET /internal/orders/flight-items?flightNumber=&departureDate=`.
