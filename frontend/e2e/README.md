# End-to-end tests (Playwright)

Browser tests against the **running** docker stack (frontend `http://localhost:3000`, gateway `http://localhost:8080`).

```bash
cd frontend
npm run e2e                 # headless Chromium, ~1-2 min (plus ~40s fixture setup)
E2E_KEEP=1 npm run e2e      # keep and reuse the fixture between runs (fast local iteration)
```

`@playwright/test` is pinned to `1.61.0`, which uses the Chromium build `chromium-1228` cached in
`~/.cache/ms-playwright`. If it is missing run `npx playwright install chromium`.

## Fixture

`global-setup.ts` creates its own data through the public API (like `scripts/test-package-order.py`):
three users (customer, hotel partner, car partner), registered and marked `email_verified` via `psql` in `auth_db`;
an approved hotel org with a hotel, room type, rate plan, two nights of stock and rates plus a HOTEL commission rate;
an approved car org with two branches, one vehicle, rates, a one-way route both directions, approved documents
and a CAR commission rate. Ids go to `e2e/.state.json` (mode 0600, gitignored).
Admin credentials are read from `../.env` (`WAYLO_BOOTSTRAP_ADMIN_EMAIL/PASSWORD`) and never printed.

Pages are opened signed in by writing `waylo_token`, `waylo_refresh` and `waylo_user` into localStorage
before any page script runs (`signIn` in `helpers.ts`). Logins are cached per account because the gateway
limits `/api/auth/login` per IP; registration sleeps 13s per user for the same reason.

`global-teardown.ts` deletes only what the suite created (its orgs, their orders/payments/vouchers, hotel,
car data and uploaded document files, reviews of its hotel, its users). Demo data is never touched.

## Specs (run in order, one worker)

| Spec | Covers |
|---|---|
| `01-customer-hotel` | hotel search -> detail -> quote -> cart -> sandbox pay -> order CONFIRMED with QR -> cancellation preview |
| `02-customer-car` | one-way car search (one-way fee shown) -> cart -> pay -> order total = days x rate + fee |
| `03-partner-hotel` | room types, rate plans, calendar save persists, orders shows the spec 1 order, earnings net after commission, team lists owner, QR redeem of the spec 1 voucher |
| `04-admin` | approve a pending org, payments ledger for the spec 1 order, review moderation (hide), notifications list |
| `05-surface-guards` | customer redirected away from `/admin` and `/partner`, partner away from `/admin` |

Every page visit also runs `expectNoRawIcons`: no `material-symbols-outlined` element may render its ligature
name as visible words (icon font missing).

Failure screenshots and traces go to `frontend/test-results/` (gitignored).
