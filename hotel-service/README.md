# Hotel Service

## Purpose

Owns the hotel catalog and physical rooms for the Waylo hotel-first marketplace. Each hotel belongs to an organization (tenant) via `organizationId`.

## API Endpoints

All endpoints return a success envelope:

```json
{"success":true,"data":{...},"message":"...","timestamp":"..."}
```

Error responses follow this envelope:

```json
{"status":404,"error":"NOT_FOUND","message":"...","path":"...","timestamp":"..."}
```

### HotelController (`/api/hotels`)

| #   | HTTP Method | Path                               | Query Params                           | Success Code |
| --- | ----------- | ---------------------------------- | -------------------------------------- | ------------ |
| 1   | POST        | `/api/hotels`                      | —                                      | 201          |
| 2   | GET         | `/api/hotels/{id}`                 | —                                      | 200          |
| 3   | GET         | `/api/hotels`                      | `organizationId` (optional UUID)       | 200          |
| 4   | GET         | `/api/hotels/search`               | `city` (optional), `country` (optional), `minStars` (optional) | 200 |
| 5   | PUT         | `/api/hotels/{id}`                 | —                                      | 200          |
| 6   | DELETE      | `/api/hotels/{id}`                 | —                                      | 204          |
| 7   | POST        | `/api/hotels/{id}/deactivate`      | —                                      | 200          |
| 8   | POST        | `/api/hotels/{id}/activate`        | —                                      | 200          |

**POST /api/hotels — Request body (HotelRequest):**

```json
{
  "name": "Grand Hotel",
  "city": "Istanbul",
  "country": "Turkey",
  "address": "Taksim Square, Beyoglu",
  "starRating": 4,
  "description": "A luxury hotel in the heart of Istanbul",
  "latitude": 41.0369,
  "longitude": 28.9857,
  "organizationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Fields: `name` (required, non-blank), `city` (required, non-blank), `country` (required, non-blank), `address` (required, non-blank), `starRating` (required, integer 1–5), `description` (optional string), `latitude` (optional decimal), `longitude` (optional decimal), `organizationId` (required, UUID).

### RoomController (`/api/hotels/{hotelId}/rooms`)

| #   | HTTP Method | Path                                       | Query Params                                  | Success Code |
| --- | ----------- | ------------------------------------------ | --------------------------------------------- | ------------ |
| 9   | POST        | `/api/hotels/{hotelId}/rooms`              | —                                             | 201          |
| 10  | GET         | `/api/hotels/{hotelId}/rooms/{roomId}`     | —                                             | 200          |
| 11  | GET         | `/api/hotels/{hotelId}/rooms`              | —                                             | 200          |
| 12  | GET         | `/api/hotels/{hotelId}/rooms/search`       | `roomType` (optional), `status` (optional), `maxPrice` (optional) | 200 |
| 13  | PUT         | `/api/hotels/{hotelId}/rooms/{roomId}`     | —                                             | 200          |
| 14  | DELETE      | `/api/hotels/{hotelId}/rooms/{roomId}`     | —                                             | 204          |
| 15  | POST        | `/api/hotels/{hotelId}/rooms/{roomId}/status` | `status` (required enum)                     | 200          |
| 16  | GET         | `/api/hotels/{hotelId}/rooms/available-count` | —                                          | 200          |

**POST /api/hotels/{hotelId}/rooms — Request body (RoomRequest):**

```json
{
  "roomNumber": "101",
  "roomType": "DOUBLE",
  "capacity": 2,
  "pricePerNight": 150.00,
  "floor": 1,
  "description": "Standard double room with city view",
  "status": "AVAILABLE"
}
```

Fields: `roomNumber` (required, max 20 chars), `roomType` (required, enum: `SINGLE`, `DOUBLE`, `SUITE`, `DELUXE`, `FAMILY`), `capacity` (required, int >= 1), `pricePerNight` (required, decimal >= 0), `floor` (optional int), `description` (optional string), `status` (optional enum: `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`).

### HealthController

| #   | HTTP Method | Path         | Query Params | Success Code |
| --- | ----------- | ------------ | ------------ | ------------ |
| 17  | GET         | `/api/health` | —           | 200          |

Returns:

```json
{"service": "hotel-service", "status": "UP"}
```

**Total endpoints: 17**

## Error Codes

| Error Code                    | HTTP Status | Triggered By                                  |
| ----------------------------- | ----------- | --------------------------------------------- |
| `NOT_FOUND`                   | 404         | `ResourceNotFoundException`                   |
| `CONFLICT`                    | 409         | `DuplicateResourceException`                  |
| `DATA_INTEGRITY_VIOLATION`    | 409         | `DataIntegrityViolationException`             |
| `CONCURRENT_MODIFICATION`     | 409         | `ObjectOptimisticLockingFailureException`     |
| `UNPROCESSABLE_ENTITY`        | 422         | `BusinessRuleException` (code from `getRuleCode()`) |
| `VALIDATION_ERROR`            | 400         | `MethodArgumentNotValidException`             |
| `BAD_REQUEST`                 | 400         | `IllegalArgumentException`                    |
| `TYPE_MISMATCH`               | 400         | `MethodArgumentTypeMismatchException`         |
| `MISSING_PARAMETER`           | 400         | `MissingServletRequestParameterException`     |
| `MALFORMED_REQUEST`           | 400         | `HttpMessageNotReadableException`             |
| `METHOD_NOT_ALLOWED`          | 405         | `HttpRequestMethodNotSupportedException`      |
| `UNAUTHORIZED`                 | 401         | No identity (missing/invalid token)                        |
| `FORBIDDEN`                    | 403         | AccessDeniedException (not authorized for this org/hotel)  |
| `AUTHORIZATION_UNAVAILABLE`    | 503         | auth-service /internal/access unavailable (fail closed)    |
| `INTERNAL_SERVER_ERROR`       | 500         | Unhandled `Exception`                         |

## Security Status

JWT verified at api-gateway; gateway forwards X-User-Id; all hotel and room mutations require an ALLOWED decision from auth-service /internal/access (HOTEL_CREATE for create using request organizationId, HOTEL_MANAGE / ROOM_MANAGE using the hotel's stored organizationId); errors 401 no identity, 403 denied, 503 auth-service unavailable (fail closed); legacy hotels without organizationId are read-only; GET endpoints are public; room access is scoped to its hotel.

- See `../ISSUES.md` for the full list of known security gaps.

## Environment Variables

| Variable                    | Default                          | Description                        |
| --------------------------- | -------------------------------- | ---------------------------------- |
| `DB_URL`                    | `jdbc:postgresql://localhost:5432/hotel_db` | JDBC connection URL             |
| `DB_USERNAME`               | `waylo`                          | Database username                  |
| `DB_PASSWORD`               | `waylo`                          | Database password                  |
| `KAFKA_BOOTSTRAP_SERVERS`   | `localhost:9092`                 | Kafka bootstrap servers            |
| `REDIS_HOST`                | `localhost`                      | Redis host                         |
| `REDIS_PORT`                | `6379`                           | Redis port                         |
| `EUREKA_URL`                | `http://localhost:8761/eureka/`  | Eureka service registry URL        |

**Note:** When running via `docker-compose.yml`, the service container sets `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_HOST`, and `SPRING_KAFKA_BOOTSTRAP_SERVERS` as environment variables, which take precedence over the values above.

## Database Schema

Flyway-managed migrations under `src/main/resources/db/migration`:

### `V1__init.sql` — Initial schema

Creates three tables:

- **`hotels`** — Hotel catalog (id, name, city, country, address, star_rating, description, latitude, longitude, active, created_at, updated_at). Indexes on city, country, (city, country), star_rating, active.
- **`rooms`** — Physical rooms with FK to hotels (id, hotel_id, room_number, room_type, status, capacity, price_per_night, floor, description, created_at, updated_at). Unique constraint on (hotel_id, room_number). Indexes on hotel_id, (hotel_id, status), (hotel_id, room_type), (hotel_id, room_type, status), (hotel_id, price_per_night). CASCADE delete on hotel.
- **`processed_events`** — Kafka consumer idempotency table (event_id, consumer_group, processed_at). Composite primary key.

### `V2__hotel_organization_ownership.sql`

- Adds `organization_id UUID` column to `hotels` with an index.
- The column is **nullable** until existing rows are backfilled. This is documented in `../ISSUES.md` as a pending human decision (risk of data loss; a legacy quarantine organization is proposed).

## Running Locally

1. Start infrastructure:

   ```bash
   docker compose up -d postgres kafka redis config-service discovery-service
   ```

2. Run the hotel service:

   ```bash
   ./gradlew :hotel-service:bootRun
   ```

   (Run from the repo root `/home/basar/Desktop/projects/waylo`.)

The service starts on port **8083** and registers with Eureka at `http://localhost:8761`.

## Running Tests

```bash
./gradlew :hotel-service:test
```

Run from the repo root. JaCoCo HTML coverage report is generated at:

```
hotel-service/build/reports/jacoco/test/html/index.html
```

## Test Coverage

Last measured: 64.4% line before access guard (target: 60%); 135 tests (2026-10-15)
