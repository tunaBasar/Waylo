# Infra review: api-gateway, config-service, discovery-service, compose

Fixed in this pass: gateway `gateway` actuator endpoint removed, order-create and voucher-redeem rate limits, `%00` rejected, infra ports bound to 127.0.0.1. Everything below is open.

## Prioritized open items

1. **Eureka and config-service have no authentication** (medium, network-only).
   `discovery-service/src/main/resources/application.yml:1-21` and `config-service/src/main/resources/application.yml:1-20` have no security starter. Anyone on the docker network can register a fake instance (traffic hijack via `lb://`) or read `/application/default`. Now bound to localhost on the host, so only containers reach them.
   Fix: Spring Security basic auth on both, creds via `:?` env, update every `defaultZone` and config import. Open decision: do now or accept network isolation for MVP.
2. ~~Config-service health `show-details: always`~~ fixed by coordinator (`when-authorized`).
3. **Rate-limit client IP is the TCP peer** (medium for prod). `api-gateway/.../application.yml` `waylo.gateway.trust-forwarded-for: false`. Spoofed X-Forwarded-For is correctly ignored (verified live). Behind a real LB/CDN all users share one IP and one bucket. Decision: when a trusted proxy exists, set true only if the proxy overwrites XFF.
4. Real Gmail address as default for MAIL_USERNAME removed by coordinator (compose + notification application.yml now default empty; .env keeps the value). Still open: `notification-service application.yml` `from:` address is the same mailbox (user decision: keep or use a domain sender).
5. **0.0.0.0 bindings**: `docker-compose.yml:152` (gateway 8080) and frontend `:706` (3000). Intended per brief. Postgres/redis/pgadmin/config/eureka were changed to 127.0.0.1 in the file but take effect only when those containers are next recreated (not restarted now, per rules).
6. Rate limit fails open when Redis is down (`AuthRateLimitFilter.java` onErrorResume). Deliberate. Limits are per IP, fixed window, in-gateway only: new order/voucher limits (30/min/IP each) are tunable via `waylo.gateway.order-create-per-minute` and `voucher-redeem-per-minute`. Per-user limits are not implemented.
7. CORS: single origin `http://localhost:3000` with credentials (`api-gateway application.yml` globalcors), `allowed-headers: "*"`. Fine for dev, must be set per environment for prod (currently hardcoded, not env-driven).
8. Gateway path check rejects any `;`, `//`, `%2e`, `%2f`, `%5c`, `\`, `%00`. Note this also rejects legitimate encoded slashes in IDs. Acceptable.
9. Eureka self-preservation is disabled (`discovery-service application.yml:16`): good for dev, evicts instances on network blips in prod. Dashboard `/` is open (200) on localhost only.
10. `/api/admin/**` and `/api/organizations/**` catch-all routes go to auth-service; authorization is by downstream role checks, gateway only verifies token. `/api/admin/orders` etc. have specific routes first. Verified spoofed ADMIN header without token yields 401.

## Public routes (no token), all GET unless noted
- `/api/auth/**`: login/register/refresh etc. (rate limited).
- `/actuator/health`: liveness, details only when authorized.
- `/api/hotels`, `/api/flights`, `/api/airports`, `/api/seat-inventory`, `/api/flight-offers`, `/api/car-branches`, `/api/car-offers`, `/api/bus-terminals`, `/api/bus-sales-status`, `/api/bus-trips` and `/seats`: read-only catalog/search. Optional identity forwarded if a valid access token is present.
- OPTIONS: CORS preflight.
- Everything else needs a valid access token (`typ=access`, issuer `waylo-auth`). Refresh tokens rejected (verified live). `/api/actuator/*`, `/internal/**` return 404/401 from outside (no route; verified live). Note `/actuator/info` is public (200) and harmless.

## Checked and fine
- X-User-Id/Email/Role/X-Internal-Token stripped on every request (unit tests plus live).
- All services expose only `health,info`; `/actuator/env|heapdump|configprops` 404 via gateway.
- Every compose service calling /internal has `INTERNAL_API_TOKEN` with `:?`; healthchecks and memory limits present on all; saga orchestrator is under `profiles: ["legacy-saga"]`.
- Secrets in git: no private keys or real secrets found. Only `.env.example` (placeholders) and `${ENV}` references with no defaults. Not exhaustive for binary or history. `.env` is untracked. No rotation needed from this scan.
