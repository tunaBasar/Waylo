# Inventory automation contract (2026-11-27)

Status: agreed by the architect for day 4. Answers the user question of 2026-11-24 ("do hotels, flights and buses
really get updated by hand?"): rules and timetables open future dates automatically, so demo and real data never run
out. Manual edits always win over generated data.

## 1. Common rules

- **Time zone:** all rule dates and clock times are Europe/Istanbul. "today" = `LocalDate.now(clock.withZone(Europe/Istanbul))`.
- **Weekdays (JSON):** an array of `java.time.DayOfWeek` names, e.g. `["FRIDAY","SUNDAY"]`; at least one, no duplicates.
  - In the database: a `SMALLINT` mask, bit `(dayOfWeek.getValue() - 1)`, so MONDAY = 1 and SUNDAY = 64, with `CHECK (weekdays BETWEEN 1 AND 127)`.
- **Formats:** clock times are `"HH:mm"` (`LocalTime`); dates are ISO `yyyy-MM-dd`.
- **Generators:**
  - They are idempotent: running one twice changes nothing the second time.
  - They never change or delete data they did not create. Hotel rows with `source = MANUAL` are never touched. Existing bus trips and flights are never changed.
  - They never create past departures: a bus departure earlier than now + 60 min is skipped, and so is a flight departure earlier than now + 3 h.
  - Each hotel or schedule runs in its own transaction. A failure is logged with the hotel or schedule id, and the run continues with the next one.
- **When generators run:**
  - Every day, on a cron in zone Europe/Istanbul. Properties: `waylo.automation.enabled` (default `true`) and `waylo.automation.cron`. Defaults: hotel `0 10 3 * * *`, bus `0 20 3 * * *`, flight `0 30 3 * * *`.
  - Immediately after a rule or schedule is created, updated or activated, for that rule or schedule only.
  - On the "apply now" endpoint.
  - Generation never runs inside the transaction that saves the rule: the controller (or a non-transactional facade) calls save first, then apply.
  - Scheduler threads have no TenantContext, so they run in SERVICE mode (RLS).
- **Result:** every run returns or logs a summary record `{created, updated, skippedExisting, skippedManual, skippedPast}`. Fields that do not apply to a vertical are 0.
- **Errors:** the existing `ApiResponse` envelope and the service's `BusinessRuleException` codes, with English messages. The web maps each code to Turkish.
- **Performance:** one hotel with 4 room types, 2 rate plans, max occupancy 4 and 365 days must apply in under 5 s. Batch the work or use set-based SQL; no round trip per night and occupancy.

## 2. Hotel: rolling inventory and rate rules (hotel-service, migration V14)

### 2.1 Tables

**`hotel_inventory_rules`**
- `id UUID PK`, `hotel_id UUID NOT NULL`, `room_type_id UUID NOT NULL`
- `default_allotment INT NOT NULL CHECK (0..10000)`
- `horizon_days INT NOT NULL DEFAULT 365 CHECK (30..730)`
- `active BOOLEAN NOT NULL DEFAULT TRUE`, `version BIGINT NOT NULL DEFAULT 0`
- `created_at` / `updated_at TIMESTAMP NOT NULL DEFAULT NOW()`
- `UNIQUE (hotel_id, room_type_id)`; `FOREIGN KEY (hotel_id, room_type_id) REFERENCES room_types (hotel_id, id) ON DELETE CASCADE`

**`hotel_rate_rules`**
- `id UUID PK`, `hotel_id`, `room_type_id`, `rate_plan_id`
- `name VARCHAR(80) NOT NULL`
- `valid_from DATE NULL`, `valid_to DATE NULL`
  - both null = the default rule; both set = a season
  - `CHECK ((valid_from IS NULL) = (valid_to IS NULL))` and `CHECK (valid_to >= valid_from)`
- `currency CHAR(3) NOT NULL`
- `base_occupancy INT NOT NULL CHECK (1..30)`
- `base_amount NUMERIC(12,2) NOT NULL CHECK (> 0)`
- `weekend_base_amount NUMERIC(12,2) NULL CHECK (> 0)`
- `extra_person_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (>= 0)`
- `reduced_person_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (>= 0)`
- `active`, `version`, `created_at`, `updated_at`
- FKs `(hotel_id, room_type_id)` to `room_types (hotel_id, id)` and `(hotel_id, rate_plan_id)` to `rate_plans (hotel_id, id)`, both `ON DELETE CASCADE`
- index on `(hotel_id)`

**Changes to existing tables**
- `daily_inventory` and `daily_rates`: `ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','RULE'))`. Existing rows stay MANUAL.

**RLS and grants**
- Both new tables use the V9 child-table pattern: ENABLE and FORCE RLS, plus read, insert, update and delete policies through `app_hotel_in_scope(hotel_id)`.
- `GRANT SELECT, INSERT, UPDATE, DELETE ... TO waylo_app`, as in V11.

### 2.2 Price derivation (pure class `RateRuleMath`)

For one night `d` of a room type with max occupancy `M` and one rate plan:

1. **Pick the rule.** Consider the active rules of this (room type, rate plan) that cover `d`.
   - A season (`valid_from <= d <= valid_to`) beats the default rule.
   - Among seasons, the latest `valid_from` wins, then the latest `updated_at`.
   - If no rule covers `d`, no rate is generated for that night.
2. **Base amount.** `base` = `weekend_base_amount` when `d` is a FRIDAY or SATURDAY night and the field is set; otherwise `base_amount`.
3. **Amount per occupancy.** For each occupancy `o` in `1..M`, with base occupancy `b`:
   - `o > b`: `base + (o - b) * extra_person_amount`
   - `o < b`: `base - (b - o) * reduced_person_amount`
   - `o = b`: `base`
   - Round to scale 2, HALF_UP. If the result is `<= 0`, that occupancy is not generated and counts as skipped.
4. **Validation on save:**

   | Rule | Error code |
   |---|---|
   | `base_occupancy <= max_occupancy` of the room type | `OCCUPANCY_RULE` |
   | Currency equals the hotel currency (same check as `RateServiceImpl`) | `CURRENCY_MISMATCH` |
   | Room type and rate plan exist, belong to this hotel and are ACTIVE | existing not-found / business codes |
   | `valid_to >= valid_from` | `INVALID_DATE_RANGE` |
   | At most one active default rule per (room type, rate plan), and active seasons of the same pair never overlap | `RULE_OVERLAP` |

### 2.3 Generator (`HotelRuleGenerator`)

**Window per room type:** `[today, today + horizon_days)`. `horizon_days` comes from the room type's inventory rule, or is 365 when the room type has none.

**Inventory**, only when the room type has an active inventory rule:
- A night without a row: INSERT `allotment = default_allotment`, `source = RULE`, using `ON CONFLICT (hotel_id, room_type_id, stay_date) DO NOTHING`.
- A RULE row whose allotment differs from the rule: set `allotment = max(default_allotment, out_of_service + held + confirmed)`.
  - Lock these rows first with PESSIMISTIC_WRITE, in the same order as the hold path (`room_type_id`, `stay_date`), so the job cannot deadlock with holds.
- MANUAL rows: never touched.

**Rates**, for every night and occupancy with a derived amount:
- No row: INSERT with `source = RULE`, using `ON CONFLICT (hotel_id, room_type_id, rate_plan_id, stay_date, occupancy) DO NOTHING`.
- A RULE row with a different amount: UPDATE the amount and bump the version.
- MANUAL rows: untouched.
- A RULE row that no rule covers any more: left as it is, never deleted. Deleting a price would silently remove sellable nights.

**Manual edits:** the existing `PUT /inventory` and `PUT /rates` set `source = MANUAL` on both insert and update.

**Reset:** sets the rows of a date range back to RULE, then regenerates that range.
- Inventory: `allotment = max(rule, out_of_service + held + confirmed)` and `stop_sell = false`. `out_of_service` keeps its value.
- Rates: recomputed. A night that no rule covers keeps its amount and becomes RULE.

### 2.4 API

Every endpoint uses the existing guard `HotelAccessGuard.requireRoomManage(userId, hotelId)` (partner managers and admin). The gateway already routes `/api/hotels/**`.

| Method and path | Body | Response |
|---|---|---|
| `GET /api/hotels/{hotelId}/automation` | none | `{inventoryRules: [...], rateRules: [...], coverage: [{roomTypeId, inventoryUntil, ratesUntil}]}`. The two dates are the last night with a row (any source), or null. |
| `PUT /api/hotels/{hotelId}/automation/inventory-rules/{roomTypeId}` | `{defaultAllotment, horizonDays, active, expectedVersion?}` | `{rule, summary}` (upsert) |
| `POST /api/hotels/{hotelId}/automation/rate-rules` | `{roomTypeId, ratePlanId, name, validFrom?, validTo?, currency, baseOccupancy, baseAmount, weekendBaseAmount?, extraPersonAmount, reducedPersonAmount, active}` | `201 {rule, summary}` |
| `PUT /api/hotels/{hotelId}/automation/rate-rules/{ruleId}` | same body plus `expectedVersion?` | `{rule, summary}` |
| `DELETE /api/hotels/{hotelId}/automation/rate-rules/{ruleId}` | none | `204`. Generated rows stay. |
| `POST /api/hotels/{hotelId}/automation/apply` | none | `summary` |
| `POST /api/hotels/{hotelId}/automation/reset` | `{roomTypeId?, from, to}` | `summary`. Requires `from >= today`, `to >= from`, and a range of at most 730 days. |

- `DailyInventoryResponse` and `DailyRateResponse` gain the field `source` (`"MANUAL"` or `"RULE"`).
- Errors: version conflict (409, like the existing optimistic-lock handling), `OCCUPANCY_RULE`, `CURRENCY_MISMATCH`, `RULE_OVERLAP`, `INVALID_DATE_RANGE`.

### 2.5 Web (partner)

**New page `/partner/automation`, "Otomatik takvim"**
- Manage-only, like `/partner/calendar` (add it to `MANAGE_ONLY_HREFS`). Nav item right after "Takvim", icon `autorenew`.
- **One card per room type:**
  - "Stok kurali": daily room count; "Kac gun ileri acik" (90, 180, 365 or 730); active toggle.
  - Coverage line, e.g. "Stok 26.11.2027 tarihine kadar acik".
- **Rate rules per room type x rate plan:**
  - List columns: name; date range ("Her zaman" or "01.06.2027 - 31.08.2027"); base ("2 kisi: 4.000,00 TL"); weekend price.
  - Add/edit form with Turkish money input (existing `parseTrAmount` / `formatTrAmountInput`).
  - Live derived-price line from the pure helper `deriveOccupancyPrices`, which uses exactly the formula of 2.2, e.g. "1 kisi 3.500,00 TL · 2 kisi 4.000,00 TL · 3 kisi 4.800,00 TL".
  - A warning names every occupancy that would not be priced.
- **"Simdi uygula"** shows a toast, e.g. "120 gece stok, 1.440 fiyat olusturuldu; 12 elle degistirilmis gun korundu".
- **"Elle degisiklikleri kurala dondur"** takes a date range and asks for confirmation first.
- Every error code above has a Turkish text.

**`/partner/calendar`**
- Cells with `source = MANUAL` show a small "Elle" marker.
- One legend line: "Elle girilen gunler otomatik kurallarla degismez."

## 3. Bus: weekly timetables (bus-service, migration V4)

### 3.1 Tables

**`trip_schedules`**
- `id UUID PK`, `organization_id UUID NOT NULL`, `bus_id UUID NOT NULL REFERENCES buses (id)`
- `code VARCHAR(40) NOT NULL`, `currency VARCHAR(3) NOT NULL`
- `weekdays SMALLINT NOT NULL CHECK (1..127)`
- `departure_time TIME NOT NULL` (Europe/Istanbul)
- `valid_from DATE NOT NULL`, `valid_to DATE NULL`
- `horizon_days INT NOT NULL DEFAULT 30 CHECK (1..120)`
- `active BOOLEAN NOT NULL DEFAULT TRUE`, `version BIGINT NOT NULL DEFAULT 0`
- `created_at`, `updated_at`
- `UNIQUE (organization_id, code)`

**`trip_schedule_stops`**
- `id`, `schedule_id REFERENCES trip_schedules ON DELETE CASCADE`, `seq INT NOT NULL CHECK (>= 0)`
- `terminal_id REFERENCES terminals`
- `arrival_offset_minutes INT NOT NULL CHECK (>= 0)`
- `departure_offset_minutes INT NOT NULL`, with `departure_offset_minutes >= arrival_offset_minutes`
- `UNIQUE (schedule_id, seq)`
- Stop 0 has offsets 0/0. Offsets strictly increase along the route: `arrival(i) > departure(i-1)`. The last offset is at most 4320 (3 days).

**`trip_schedule_fares`**
- Same columns and checks as `trip_fares`, but keyed by `schedule_id`.

**Change to `trips`:** `ADD COLUMN schedule_id UUID NULL REFERENCES trip_schedules (id) ON DELETE SET NULL`.

**RLS and grants:** as in V2.
- Schedules are scoped by `organization_id`.
- Stops and fares are scoped through a security-definer function `app_schedule_in_scope(schedule_id)`, modelled on `app_trip_in_scope`.

### 3.2 Generator

For every active schedule, for each date in `[max(today, valid_from), min(today + horizon_days - 1, valid_to)]` whose weekday is in the mask:
- The departure is the date at `departure_time` in Europe/Istanbul.
- **Skip** it when:
  - the departure is earlier than now + 60 min
  - a trip with (`organization_id`, `code`, `departure_at`) already exists, in any status. This includes CANCELLED, so a cancelled departure is never recreated.
  - the bus is inactive (also log it)
- **Otherwise create it** through the same code path as `createTrip`:
  - `TripRules.validateStops` / `validateFares`
  - active terminals
  - status PUBLISHED
  - `schedule_id` set

### 3.3 API

All endpoints are under `/api/bus-partner/organizations/{organizationId}`, with the guard `AuthGateway.requireBusManager`.

| Method and path | Body | Response |
|---|---|---|
| `GET /trip-schedules` | none | list; each schedule carries `nextDepartures` (the next 5 generated `departureAt`) and `generatedCount` |
| `POST /trip-schedules` | `{busId, code, currency, weekdays, departureTime, validFrom, validTo?, horizonDays, stops: [{terminalId, arrivalOffsetMinutes, departureOffsetMinutes}], fares: [{fromSeq, toSeq, price}]}` | `201 {schedule, summary}` |
| `PUT /trip-schedules/{id}` | same body plus `expectedVersion?` | `{schedule, summary}`. Only future generation changes; existing trips stay as they are. |
| `POST /trip-schedules/{id}/activate` | none | `{schedule, summary}` |
| `POST /trip-schedules/{id}/deactivate` | none | `{schedule}` |
| `POST /trip-schedules/apply` | none | `summary` for the organization |

- Errors:
  - new: `SCHEDULE_CODE_TAKEN`; `INVALID_SCHEDULE` (empty weekdays, offsets not increasing, horizon out of range, `valid_to < valid_from`)
  - existing: `INVALID_STOPS`, `INVALID_FARES`, `INVALID_TERMINAL`, `BUS_INACTIVE`
- `TripResponse` gains `scheduleId` (nullable).

### 3.4 Web (partner)

On `/partner/bus/trips`, add a section "Haftalik sefer planlari" above the trip list.

**List**
- Columns: code; days ("Pzt Cum Paz"); time (22:30); route; next departure ("Sonraki: 28.11 22:30").
- An Aktif/Pasif toggle.

**Form**
- Fields: bus; code; 7 day checkboxes; departure time.
- Stops: each stop has an arrival and a departure clock time, with a "+1 gun" marker. The pure helper `scheduleOffsets` (with tests) turns them into offsets.
- Prices: use the existing `validateFares`.
- Validity range; "Kac gun ileri" (14, 30, 60 or 90).

**Trips:** a trip created by a schedule shows a small tag, e.g. "Plan: KPD-101".

## 4. Flight: recurring schedules (flight-service V3 and flight-seat-inventory-service)

### 4.1 Flight numbers

- A flight's identity stays `flight_number`. It is UNIQUE and used by seat inventory, package-booking and check-in.
- A scheduled departure gets `flight_number` = schedule code + `yy` + day of year (3 digits). Example: code WD1 on 2026-11-27 gives WD126331.
- The code pattern is `^[A-Z]{2}[0-9]{1,3}$`, so the number fits `VARCHAR(10)`.

### 4.2 Tables

**`flight_schedules`**
- `id`, `code VARCHAR(5) UNIQUE`, `airline VARCHAR(100)`
- `departure_airport_id`, `arrival_airport_id`: FKs to `airports`
- `weekdays`, `departure_time TIME`
- `duration_minutes INT CHECK (20..1200)`
- `valid_from`, `valid_to`
- `horizon_days DEFAULT 30 CHECK (1..120)`
- `active`, `version`, timestamps

**`flight_schedule_cabins`**
- `id`, `schedule_id` (FK, `ON DELETE CASCADE`)
- `seat_type VARCHAR(20)`, checked IN (ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST)
- `price NUMERIC(10,2) CHECK (> 0)`
- `from_row`, `to_row`: 1..999, `from_row <= to_row`
- `letters VARCHAR(11)`: distinct letters A-K
- `UNIQUE (schedule_id, seat_type)`
- The row ranges of one schedule must not overlap (`INVALID_CABINS`).

**Change to `flights`:** `ADD COLUMN schedule_id UUID NULL REFERENCES flight_schedules (id) ON DELETE SET NULL`.

### 4.3 Generator

- Dates are picked as in 3.2.
- Departure = the date at `departure_time` in Europe/Istanbul; arrival = departure + duration.
- Skip departures earlier than now + 3 h, and flight numbers that already exist.
- Otherwise create the flight through the same path as `POST /api/flights`, so `FlightCreatedEvent` is published, with `schedule_id` set.

### 4.4 Seats

- `FlightCreatedEvent` gains `cabins: [{seatType, price, fromRow, toRow, letters}]`. The field is null for flights created by hand.
- flight-seat-inventory consumes `flightCreated`:
  - add the type mapping and a local event class
  - when `cabins` is not empty, create the missing seats; an existing seat number is skipped, never an error
  - idempotent by `eventId`, through the existing IdempotencyService
- Check every consumer of `flight-events`: the new field must not break any of them.

### 4.5 API

Writes are admin-only through the existing `AdminWriteFilter`, because the paths start with `/api/flights`. Reads are public.

| Method and path | Body | Response |
|---|---|---|
| `GET /api/flights/schedules` | none | list; each schedule carries `nextFlights` (the next 5 flight numbers with departure) |
| `POST /api/flights/schedules` | `{code, airline, departureAirportCode, arrivalAirportCode, weekdays, departureTime, durationMinutes, validFrom, validTo?, horizonDays, cabins: [...]}` | `201 {schedule, summary}` |
| `PUT /api/flights/schedules/{id}` | same body plus `expectedVersion?` | `{schedule, summary}` |
| `POST /api/flights/schedules/{id}/activate` | none | `{schedule, summary}` |
| `POST /api/flights/schedules/{id}/deactivate` | none | `{schedule}` |
| `POST /api/flights/schedules/apply` | none | `summary` |

- Errors: `SCHEDULE_CODE_TAKEN`, `INVALID_SCHEDULE`, `INVALID_CABINS`, `SAME_AIRPORT`, and the existing unknown-airport error.

### 4.6 Web (admin)

New page `/admin/flight-schedules`, "Ucus Planlari". Nav item right after "Ucuslar", icon `event_repeat`.
- List with an Aktif/Pasif toggle, the next flight numbers, and a "Simdi uygula" button.
- Form fields:
  - code, airline
  - departure and arrival airport, as selects filled from `/api/airports`
  - days, departure time, duration in minutes
  - validity range, "Kac gun ileri"
  - cabins, with default rows Business 1-2 AC and Economy 3-7 ABCD, prices in TL

## 5. Demo data (after sections 2-4 are merged)

**`scripts/seed-demo.py` switches to rules:**
- Hotels:
  - an inventory rule per room type: 5 rooms, 365 days
  - a default rate rule per room type: base occupancy 2 at today's 2-person price, extra and reduced amounts taken from today's prices
  - then `reset` from today to today + 365 for the seed organization's own hotels, then `apply`
- Flights: one flight schedule per route (code `WD<route>`, every day, today's times and prices, 30 days), then `apply`. It no longer creates flights one by one.

**`scripts/seed-bus-partner.py`** creates one weekly schedule.

## 6. Out of scope (recorded on purpose)

- Channel manager / PMS API.
- Duffel or Amadeus as flight supplier: needs the user's sandbox account.
- Car rental: rates are not dated, so nothing runs out.
- Changing flight identity to (number, date).
