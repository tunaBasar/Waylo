# Hotel Service

## Purpose

Owns the hotel catalog, room types, rate plans, physical rooms, daily inventory, and daily rates. Each hotel belongs to an organization via `organizationId`.

## API Endpoints

All responses return `ApiResponse` wrapper or `ErrorResponse`:

### HotelController (`/api/hotels`)

| # | Method | Path | Params | Code |
|---|--------|------|--------|------|
| 1 | POST | `/api/hotels` | -- | 201 |
| 2 | GET | `/api/hotels/{id}` | -- | 200 |
| 3 | GET | `/api/hotels` | `organizationId` (optional) | 200 |
| 4 | GET | `/api/hotels/search` | `city`, `country`, `minStars` | 200 |
| 5 | PUT | `/api/hotels/{id}` | -- | 200 |
| 6 | DELETE | `/api/hotels/{id}` | -- | 204 |
| 7 | POST | `/api/hotels/{id}/deactivate` | -- | 200 |
| 8 | POST | `/api/hotels/{id}/activate` | -- | 200 |

### RoomTypeController (`/api/hotels/{hotelId}/room-types`)

| # | Method | Path | Code |
|---|--------|------|------|
| 9 | POST | `/api/hotels/{hotelId}/room-types` | 201 |
| 10 | GET | `/api/hotels/{hotelId}/room-types/{id}` | 200 |
| 11 | GET | `/api/hotels/{hotelId}/room-types` | 200 |
| 12 | PATCH | `/api/hotels/{hotelId}/room-types/{id}` | 200 |

POST fields: `code` (required, 2-30 uppercase alphanumeric/underscore, unique per hotel), `name` (required, max 120), `description` (optional, max 2000), `maxAdults` (1-20), `maxChildren` (0-10), `maxOccupancy` (1-30), `sizeSqm` (optional, 1-2000). Status ACTIVE/INACTIVE.

### RatePlanController (`/api/hotels/{hotelId}/rate-plans`)

| # | Method | Path | Code |
|---|--------|------|------|
| 13 | POST | `/api/hotels/{hotelId}/rate-plans` | 201 |
| 14 | GET | `/api/hotels/{hotelId}/rate-plans/{id}` | 200 |
| 15 | GET | `/api/hotels/{hotelId}/rate-plans` | 200 |
| 16 | PATCH | `/api/hotels/{hotelId}/rate-plans/{id}` | 200 |

POST fields: `code` (required, 2-30, unique per hotel), `name` (required, max 120), `description` (optional), `mealPlan` (enum: ROOM_ONLY/BREAKFAST/HALF_BOARD/FULL_BOARD/ALL_INCLUSIVE), `cancellationPolicy` (enum: FREE_CANCELLATION/NON_REFUNDABLE), `freeCancellationDaysBefore` (FREE_CANCELLATION requires 0-365; NON_REFUNDABLE requires NULL). V6 CHECK enforced.

### RoomController (`/api/hotels/{hotelId}/rooms`)

| # | Method | Path | Params | Code |
|---|--------|------|--------|------|
| 17 | POST | `/api/hotels/{hotelId}/rooms` | -- | 201 |
| 18 | GET | `/api/hotels/{hotelId}/rooms/{id}` | -- | 200 |
| 19 | GET | `/api/hotels/{hotelId}/rooms` | -- | 200 |
| 20 | GET | `/api/hotels/{hotelId}/rooms/search` | `roomType`, `status`, `maxPrice` | 200 |
| 21 | PUT | `/api/hotels/{hotelId}/rooms/{id}` | -- | 200 |
| 22 | DELETE | `/api/hotels/{hotelId}/rooms/{id}` | -- | 204 |
| 23 | POST | `/api/hotels/{hotelId}/rooms/{id}/status` | `status` | 200 |
| 24 | GET | `/api/hotels/{hotelId}/rooms/available-count` | -- | 200 |

### InventoryController (`/api/hotels/{hotelId}/inventory`)

| # | Method | Path | Params | Code |
|---|--------|------|--------|------|
| 25 | GET | `/api/hotels/{hotelId}/inventory` | `roomTypeId`, `from`, `to` | 200 |
| 26 | PUT | `/api/hotels/{hotelId}/inventory` | -- | 200 |

GET params: `from`/`to` default 30 UTC days; max 366 inclusive. Rates: stayDate window UTC today-1 through today+730 inclusive for PUT. GET uses from/to, defaults today through today+29, max 366 inclusive.

PUT body: `reason` (optional, <=500 chars), `items` (batch 1-400). Each item: `roomTypeId`, `stayDate` (UTC, today-1 through today+730 inclusive), `allotment` (required for new; 0-10000), `outOfService` (optional, <=allotment), `stopSell` (optional boolean), `expectedVersion` (optional for concurrency). `available` = `allotment - outOfService - held - confirmed` (held/confirmed read-only).

### RateController (`/api/hotels/{hotelId}/rates`)

| # | Method | Path | Params | Code |
|---|--------|------|--------|------|
| 27 | GET | `/api/hotels/{hotelId}/rates` | `roomTypeId`, `ratePlanId`, `from`, `to` | 200 |
| 28 | PUT | `/api/hotels/{hotelId}/rates` | -- | 200 |

PUT body: `items` (batch 1-400). Each item: `roomTypeId`, `ratePlanId`, `stayDate`, `occupancy` (1-30), `amount` (required, >=0.01, up to 10 integer digits and 2 fractional digits), `currency` (ISO 4217, single per hotel, 422 on mismatch), `expectedVersion` (optional). No DELETE endpoint.

## Error Codes

| Code | HTTP | Trigger |
|------|------|---------|
| NOT_FOUND | 404 | Resource not found |
| CONFLICT | 409 | Duplicate/resource conflict |
| DATA_INTEGRITY_VIOLATION | 409 | DB constraint violation |
| CONCURRENT_MODIFICATION | 409 | Version mismatch on PUT/PATCH |
| UNPROCESSABLE_ENTITY | 422 | Business rule violation |
| VALIDATION_ERROR | 400 | Request body validation |
| UNAUTHORIZED | 401 | No identity |
| FORBIDDEN | 403 | Access denied |
| AUTHORIZATION_UNAVAILABLE | 503 | auth-service unavailable |

## Security

JWT verified at api-gateway; forwards X-User-Id. All hotel/room/inventory/rate mutations require auth-service `/internal/access` ALLOW. 401 no identity, 403 denied, 503 fail closed.

RoomType and RatePlan endpoints accept `includeInactive` query param (default false); default list omits INACTIVE entries. Catalog mutation access requires the ROOM_MANAGE permission. Default active catalog GET is public. For both list and detail, includeInactive=true requires ROOM_MANAGE. All inventory and rate GET/PUT endpoints require ROOM_MANAGE.

## Domain Models

- **RoomType**: id, hotelId, code (unique), name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm, status (ACTIVE/INACTIVE), version, timestamps
- **RatePlan**: id, hotelId, code (unique), name, description, mealPlan, cancellationPolicy, freeCancellationDaysBefore (nullable), status (ACTIVE/INACTIVE), version, timestamps
- **DailyInventory**: id, hotelId, roomTypeId, stayDate, allotment, outOfService, held, confirmed, available (computed), stopSell, version, updatedAt. Append-only audit log.
- **DailyRate**: id, hotelId, roomTypeId, ratePlanId, stayDate, occupancy, amount (BigDecimal), currency, version, updatedAt. Single currency per hotel.

## Database Schema

V1: hotels, rooms, processed_events
V2: nullable organization_id on hotels
V3: room_types table + rate_plans table
V4: daily_inventory and inventory_adjustments tables. No catalog table rename or legacy enum migration.
V5: daily_rates (with single-currency enforcement)
V6: CHECK constraint on rate_plans (cancellation policy requires specific days field)

## Running Locally

```bash
docker compose up -d postgres kafka redis config-service discovery-service
./gradlew :hotel-service:bootRun
```

Service on port 8083, registers with Eureka at localhost:8761.

## Tests

```bash
./gradlew :hotel-service:test :api-gateway:test
```

Hotel: 311 tests, 0 fail. Gateway: 34 tests, 33 executed (1 skip). JaCoCo LINE 83.8%, BRANCH 82.0%.
Live integration: `scripts/test-hotel-calendar.py` -- 106 assertions, 0 fail.
