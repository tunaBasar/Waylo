# Waylo API Documentation

> Microservices-based flight + hotel + car rental package booking platform.
>
> **Base URL (via Gateway):** `http://localhost:8080`
>
> **Stack:** Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3, PostgreSQL 16, Kafka, Redis, Eureka

---

## Table of Contents

1. [Common Patterns](#common-patterns)
2. [Flight Service](#1-flight-service)
3. [Flight Seat Inventory Service](#2-flight-seat-inventory-service)
4. [Hotel Service](#3-hotel-service)
5. [Car Rental Service](#4-car-rental-service)
6. [Package Booking Service](#5-package-booking-service)
7. [Pricing Service](#6-pricing-service)
8. [Payment Service](#7-payment-service)
9. [CRM Service](#8-crm-service)
10. [Check-in Service](#9-check-in-service)
11. [Notification Service](#10-notification-service)
12. [Saga Orchestrator (retired)](#11-saga-orchestrator)
13. [Traffic Simulator](#12-traffic-simulator)
14. [Auth Service and Account](#13-auth-service-and-account)
15. [Bus Service](#14-bus-service)
16. [Service-to-service (internal) endpoints](#15-service-to-service-internal-endpoints)
17. [Added 2026-11-24: hotel publication, stay check-out, payouts, Kafka DLT, hotel photos, order expiry/reconciliation, notification retries](#16-added-2026-11-24)
18. [Enums Reference](#enums-reference)
19. [Kafka Events](#kafka-events)
20. [Architecture](#architecture)

---

## Common Patterns

### Success Response

```json
{
  "success": true,
  "data": { ... },
  "message": "Optional message",
  "timestamp": "2026-10-11T12:00:00Z"
}
```

### Error Response

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Hotel not found with id: ...",
  "path": "/api/hotels/...",
  "timestamp": "2026-10-11T12:00:00Z"
}
```

### Validation Error (400)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/flights",
  "timestamp": "2026-10-11T12:00:00Z"
}
```

### ID Format

All entity IDs are UUID v4: `"550e8400-e29b-41d4-a716-446655440000"`

### Timestamps

ISO 8601 UTC format: `"2026-10-11T12:00:00Z"`

---

## 1. Flight Service

**Port:** 8081 | **DB:** flight_db

### Airports

#### `POST /api/airports` - Create Airport

```json
// Request
{
  "iataCode": "IST",           // required, exactly 3 chars
  "name": "Istanbul Airport",   // required
  "city": "Istanbul",           // required
  "country": "Turkey",          // required
  "timezone": "Europe/Istanbul",// required
  "latitude": 41.275278,        // optional
  "longitude": 28.751944        // optional
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid",
    "iataCode": "IST",
    "name": "Istanbul Airport",
    "city": "Istanbul",
    "country": "Turkey",
    "timezone": "Europe/Istanbul",
    "latitude": 41.275278,
    "longitude": 28.751944,
    "createdAt": "2026-10-11T12:00:00Z",
    "updatedAt": "2026-10-11T12:00:00Z"
  },
  "message": "Airport created"
}
```

#### `GET /api/airports/{id}` - Get Airport by ID
#### `GET /api/airports/iata/{iataCode}` - Get Airport by IATA Code
#### `GET /api/airports` - List All Airports
#### `PUT /api/airports/{id}` - Update Airport
#### `DELETE /api/airports/{id}` - Delete Airport (204 No Content)

### Flights

#### `POST /api/flights` - Create Flight

```json
// Request
{
  "flightNumber": "TK1001",           // required, max 10 chars
  "departureAirportCode": "IST",      // required, IATA code
  "arrivalAirportCode": "SAW",        // required, IATA code
  "departureTime": "2026-12-01T10:00:00Z", // required
  "arrivalTime": "2026-12-01T12:30:00Z",   // required
  "airline": "Turkish Airlines",       // required
  "status": "SCHEDULED"               // optional, default SCHEDULED
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid",
    "flightNumber": "TK1001",
    "departureAirportCode": "IST",
    "departureAirportName": "Istanbul Airport",
    "arrivalAirportCode": "SAW",
    "arrivalAirportName": "Sabiha Gokcen Airport",
    "departureTime": "2026-12-01T10:00:00Z",
    "arrivalTime": "2026-12-01T12:30:00Z",
    "airline": "Turkish Airlines",
    "status": "SCHEDULED",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/flights/{id}` - Get Flight by ID
#### `GET /api/flights/number/{flightNumber}` - Get Flight by Number
#### `GET /api/flights` - List All Flights

#### `GET /api/flights/search` - Search Flights

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | String | Yes | Departure airport IATA code |
| `to` | String | Yes | Arrival airport IATA code |
| `departureFrom` | Instant | Yes | Start of departure window |
| `departureTo` | Instant | Yes | End of departure window |

#### `PUT /api/flights/{id}` - Update Flight
#### `DELETE /api/flights/{id}` - Delete Flight (204)
#### `POST /api/flights/{id}/cancel` - Cancel Flight

---

## 2. Flight Seat Inventory Service

**Port:** 8082 | **DB:** flight_seat_inventory_db

#### `POST /api/seat-inventory/seats` - Create Seat

```json
// Request
{
  "flightNumber": "TK1001",  // required
  "seatNumber": "12A",       // required, max 5 chars
  "seatType": "ECONOMY",     // required
  "price": 1500.00           // required
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid",
    "flightNumber": "TK1001",
    "seatNumber": "12A",
    "seatType": "ECONOMY",
    "price": 1500.00,
    "available": true,
    "reservationId": null,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/seat-inventory/seats/{flightNumber}` - List All Seats for Flight
#### `GET /api/seat-inventory/seats/{flightNumber}/available` - List Available Seats
#### `GET /api/seat-inventory/seats/{flightNumber}/{seatNumber}` - Get Specific Seat
#### `GET /api/seat-inventory/seats/{flightNumber}/count` - Available Seat Count

#### `POST /api/seat-inventory/seats/{flightNumber}/reserve` - Reserve Seat

```json
// Request
{ "seatNumber": "12A" }

// Response
{
  "success": true,
  "data": {
    "flightNumber": "TK1001",
    "seatNumber": "12A",
    "reservationId": "generated-uuid",
    "price": 1500.00,
    "reservedAt": "2026-10-11T12:00:00Z"
  },
  "message": "Seat reserved"
}
```

#### `DELETE /api/seat-inventory/seats/{flightNumber}/reserve` - Cancel Reservation

```json
// Request
{ "seatNumber": "12A" }
// Response: 204 No Content
```

### Added endpoints (flight seat inventory, flight offers)

Gateway route: `/api/seat-inventory/**`, `/api/flight-offers`, `/api/flight-offers/**`. `SeatAccessFilter`: reads are open, reserve/cancel need a user, every other write needs ADMIN.

#### `POST /api/seat-inventory/seats/bulk` - Create seats in bulk (ADMIN)

| Field | Type | Validation |
|-------|------|-----------|
| `flightNumber` | String | required |
| `seatType` | String | required |
| `price` | BigDecimal | required, >= 0.01, max 8 integer and 2 fraction digits |
| `fromRow` | Integer | required, 1..999 |
| `toRow` | Integer | required, 1..999 |
| `letters` | String | required, distinct characters between A and K |

Response `data`: `{flightNumber, created}`. Errors: 400 validation, 403 not ADMIN, 409 on existing seats.

#### `DELETE /api/seat-inventory/seats/{flightNumber}/{seatNumber}` - Delete seat (ADMIN)

204 No Content. 403 for non-ADMIN, 404 if the seat does not exist, 409 `SEAT_HELD` if the seat has an active hold.

#### `GET /api/flight-offers?from=&to=&date=&adults=` - Search flight offers (public)

`from`, `to` (airport codes) and `date` (ISO date) are required, `adults` defaults to 1. Response `data`: list of
`{offerId, supplier, flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime, cabin, currency,
totalAmount, availableSeats, expiresAt}`. Offers are short-lived; use `offerId` in `POST /api/orders/flight`.

#### `GET /api/flight-offers/{offerId}` - Re-price an offer (public)

Response `data`: one offer (same fields). Errors: 404 for an unknown offer; the exact failure for an expired offer was not verified in source.


---

## 3. Hotel Service

**Port:** 8083 | **DB:** hotel_db

### Hotels

#### `POST /api/hotels` - Create Hotel

```json
// Request
{
  "name": "Waylo Grand Hotel",     // required
  "city": "Istanbul",               // required
  "country": "Turkey",              // required
  "address": "Beyoglu, Istiklal Cd.",// required
  "starRating": 5,                  // required, 1-5
  "description": "Luxury hotel",    // optional
  "latitude": 41.034,               // optional
  "longitude": 28.977               // optional
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Waylo Grand Hotel",
    "city": "Istanbul",
    "country": "Turkey",
    "address": "Beyoglu, Istiklal Cd.",
    "starRating": 5,
    "description": "Luxury hotel",
    "latitude": 41.034,
    "longitude": 28.977,
    "active": true,
    "roomCount": 0,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/hotels/{id}` - Get Hotel
#### `GET /api/hotels` - List All Hotels

#### `GET /api/hotels/search` - Search Hotels

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `city` | String | No | Filter by city |
| `country` | String | No | Filter by country |
| `minStars` | Integer | No | Minimum star rating |

#### `PUT /api/hotels/{id}` - Update Hotel
#### `DELETE /api/hotels/{id}` - Delete Hotel (204)
#### `POST /api/hotels/{id}/deactivate` - Deactivate Hotel
#### `POST /api/hotels/{id}/activate` - Activate Hotel

### Rooms

#### `POST /api/hotels/{hotelId}/rooms` - Create Room

```json
// Request
{
  "roomNumber": "101",        // required
  "roomType": "DOUBLE",       // required: SINGLE, DOUBLE, SUITE, DELUXE, FAMILY
  "capacity": 2,              // required, min 1
  "pricePerNight": 450.00,    // required, min 0
  "floor": 1,                 // optional
  "description": "Sea view",  // optional
  "status": "AVAILABLE"       // optional: AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid",
    "hotelId": "hotel-uuid",
    "hotelName": "Waylo Grand Hotel",
    "roomNumber": "101",
    "roomType": "DOUBLE",
    "status": "AVAILABLE",
    "capacity": 2,
    "pricePerNight": 450.00,
    "floor": 1,
    "description": "Sea view",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/hotels/{hotelId}/rooms/{roomId}` - Get Room
#### `GET /api/hotels/{hotelId}/rooms` - List Rooms for Hotel

#### `GET /api/hotels/{hotelId}/rooms/search` - Search Rooms

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `roomType` | RoomType | No | SINGLE, DOUBLE, SUITE, DELUXE, FAMILY |
| `status` | RoomStatus | No | AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE |
| `maxPrice` | BigDecimal | No | Maximum price per night |

#### `PUT /api/hotels/{hotelId}/rooms/{roomId}` - Update Room
#### `DELETE /api/hotels/{hotelId}/rooms/{roomId}` - Delete Room (204)
#### `POST /api/hotels/{hotelId}/rooms/{roomId}/status?status=RESERVED` - Update Room Status
#### `GET /api/hotels/{hotelId}/rooms/available-count` - Available Room Count

### Added endpoints (hotel availability, rate plans, room types, inventory, rates, reviews)

Partner endpoints require `ROOM_MANAGE` on the hotel's organization (`HotelAccessGuard.requireRoomManage`, checked through auth-service). Without
`X-User-Id` they return 401, without permission 403, and a hotel with no owning organization is locked (403). Responses use `ApiResponse<T>`
(`{success, data, message, timestamp}`). Enums: `MealPlan` = `ROOM_ONLY | BREAKFAST | HALF_BOARD | FULL_BOARD | ALL_INCLUSIVE`,
`CancellationPolicy` = `FREE_CANCELLATION | NON_REFUNDABLE`, `CatalogStatus` = `ACTIVE | INACTIVE`, `ReviewStatus` = `PUBLISHED | HIDDEN`.

#### `GET /api/hotels/{hotelId}/availability` - Priced availability (public)

Query: `checkIn` (ISO date, required), `checkOut` (ISO date, required), `rooms` (default 1), `adults` (default 1), `childrenAges` (repeatable int, optional).
Response `data`: `{hotelId, checkIn, checkOut, nights, rooms, adults, childrenAges, occupancy, offers: [{roomTypeId, roomTypeCode, roomTypeName,
maxAdults, maxChildren, maxOccupancy, ratePlanId, ratePlanCode, ratePlanName, mealPlan, cancellationPolicy, freeCancellationDaysBefore, nights,
rooms, occupancy, currency, totalAmount, nightly: [{stayDate, amount, currency}]}]}`.

#### `POST /api/hotels/{hotelId}/room-types` - Create room type (partner + ROOM_MANAGE)

| Field | Type | Validation |
|-------|------|-----------|
| `code` | String | required, `^[A-Z0-9_]{2,30}$` |
| `name` | String | required, max 120 |
| `description` | String | optional, max 2000 |
| `maxAdults` | Integer | required, 1..20 |
| `maxChildren` | Integer | required, 0..10 |
| `maxOccupancy` | Integer | required, 1..30 |
| `sizeSqm` | Integer | optional, 1..2000 |

Response `RoomTypeResponse`: `{id, hotelId, code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm, status, version, createdAt, updatedAt}`.

#### `GET /api/hotels/{hotelId}/room-types` - List room types (partner + ROOM_MANAGE)
#### `GET /api/hotels/{hotelId}/room-types/{roomTypeId}` - Get room type (partner + ROOM_MANAGE)

Query `includeInactive` (default false).

#### `PATCH /api/hotels/{hotelId}/room-types/{roomTypeId}` - Update room type (partner + ROOM_MANAGE)

All fields optional: `name` (not blank, max 120), `description` (max 2000), `maxAdults` (1..20), `maxChildren` (0..10), `maxOccupancy` (1..30),
`sizeSqm` (1..2000), `status` (`CatalogStatus`), `expectedVersion` (Long, optimistic lock, stale -> 409).

#### `POST /api/hotels/{hotelId}/rate-plans` - Create rate plan (partner + ROOM_MANAGE)

| Field | Type | Validation |
|-------|------|-----------|
| `code` | String | required, `^[A-Z0-9_]{2,30}$` |
| `name` | String | required, max 120 |
| `description` | String | optional, max 2000 |
| `mealPlan` | MealPlan | required |
| `cancellationPolicy` | CancellationPolicy | required |
| `freeCancellationDaysBefore` | Integer | optional, 0..365 |

Response `RatePlanResponse`: `{id, hotelId, code, name, description, mealPlan, cancellationPolicy, freeCancellationDaysBefore, status, version, createdAt, updatedAt}`.

#### `GET /api/hotels/{hotelId}/rate-plans` - List rate plans (partner + ROOM_MANAGE)
#### `GET /api/hotels/{hotelId}/rate-plans/{ratePlanId}` - Get rate plan (partner + ROOM_MANAGE)

Query `includeInactive` (default false).

#### `PATCH /api/hotels/{hotelId}/rate-plans/{ratePlanId}` - Update rate plan (partner + ROOM_MANAGE)

All optional: `name`, `description`, `mealPlan`, `cancellationPolicy`, `freeCancellationDaysBefore` (0..365), `status`, `expectedVersion`.

#### `GET /api/hotels/{hotelId}/inventory?from=&to=&roomTypeId=` - Daily inventory (partner + ROOM_MANAGE)

`from`, `to` ISO dates required, `roomTypeId` optional. Response: list of `{id, hotelId, roomTypeId, stayDate, allotment, outOfService, held,
confirmed, available, stopSell, version, updatedAt}`.

#### `PUT /api/hotels/{hotelId}/inventory` - Update daily inventory (partner + ROOM_MANAGE)

Body: `reason` (optional, max 500), `items` (required, 1..400) of `{roomTypeId (required), stayDate (required), allotment (0..10000), outOfService (>=0),
stopSell (Boolean), expectedVersion (Long)}`. Response: updated list of daily inventory rows (fields as above).

#### `GET /api/hotels/{hotelId}/rates?from=&to=&roomTypeId=&ratePlanId=` - Daily rates (partner + ROOM_MANAGE)

`from`, `to` required ISO dates, the two ids optional. Response: list of `{id, hotelId, roomTypeId, ratePlanId, stayDate, occupancy, amount, currency, version, updatedAt}`.

#### `PUT /api/hotels/{hotelId}/rates` - Update daily rates (partner + ROOM_MANAGE)

Body `items` (required, 1..500) of `{roomTypeId (required), ratePlanId (required), stayDate (required), occupancy (required, 1..30),
amount (required, >= 0.01, 10 integer and 2 fraction digits), currency (required, 3 uppercase letters), expectedVersion (Long)}`. Response: updated rate rows.

**Occupancy pricing rule (availability and quotes).** A search for N guests is priced by the exact rate row for occupancy N when one exists.
Otherwise the row with the smallest occupancy M > N on the same (roomType, ratePlan, night) is used, provided M <= the room type's `maxOccupancy`
(the room price applies to fewer guests). There is no downward fallback: a price for 2 never covers 3. Availability and quote share this rule, so
the quote's nightly amounts and total equal the offer's; the quote's `occupancy` stays the requested one. The partner calendar writes only the
`maxOccupancy` price, which therefore covers every occupancy from 1 up to max. No response fields were added.

#### `GET /api/hotels/{hotelId}/rates/currency` - Hotel currency (partner + ROOM_MANAGE)

Response `data`: `{currency}`.

#### `GET /api/hotels/{id}/reviews?page=&size=` - Published reviews (public)

`page` default 0, `size` default 10. Response `data`: `{items: [{id, hotelId, score, text, status, partnerResponse, respondedAt, createdAt,
orderItemId (null), moderationReason (null), moderatedAt (null)}], page, size, total, averageScore, reviewCount}`. Private fields are hidden in the public view.

#### `GET /api/me/reviewable-hotel-items` - My items I can still review (customer)

Response: list of `{orderItemId, hotelId, hotelName, description, checkIn, checkOut}`.

#### `POST /api/me/hotel-reviews` - Write a review (customer)

Body: `orderItemId` (required UUID), `score` (int, 1..5), `text` (optional, max 2000). Platform ADMIN gets 403. Eligibility is checked against
package-booking-service (`review-eligibility`); response `ReviewResponse` with the owner-visible fields.

#### `GET /api/me/hotel-reviews` - My reviews (customer)

Response: list of `ReviewResponse` (`{id, hotelId, score, text, status, partnerResponse, respondedAt, createdAt, orderItemId, moderationReason, moderatedAt}`).

#### `GET /api/partner/organizations/{orgId}/hotel-reviews?page=&size=` - Organization reviews (partner + HOTEL_READ, or ADMIN)

`page` default 0, `size` default 20. Response: the same `Page` shape as the public list. 403 without access.

#### `POST /api/partner/organizations/{orgId}/hotel-reviews/{id}/response` - Respond to a review (partner + HOTEL_MANAGE on the hotel)

Body `{text}` (required, max 2000). Response `ReviewResponse`.

#### `GET /api/admin/hotel-reviews?status=&page=&size=` - All reviews (ADMIN)

`status` optional (`PUBLISHED | HIDDEN`), `page` default 0, `size` default 20. Response: `Page` of `ReviewResponse`.

#### `POST /api/admin/hotel-reviews/{id}/moderate` - Moderate a review (ADMIN)

Body: `status` (required, `PUBLISHED | HIDDEN`), `reason` (required, max 500). Response `ReviewResponse`.

#### `GET /api/quotes/{id}` - Get a hotel quote (customer)

`X-User-Id` required. Response `QuoteResponse`: `{quoteId, hotelId, roomTypeId, roomTypeCode, roomTypeName, ratePlanId, ratePlanCode, ratePlanName,
mealPlan, cancellationPolicy, freeCancellationDaysBefore, checkIn, checkOut, nights, rooms, adults, childrenAges, occupancy, currency, totalAmount,
nightly, createdAt, expiresAt, expired}`. Quotes are created with `POST /api/quotes` (customer, ADMIN gets 403 `ADMIN_CANNOT_PURCHASE`).


---

## 4. Car Rental Service

**Port:** 8084 | **DB:** car_rental_db

> All responses are wrapped in `ApiResponse<T>`: `{"success": true, "data": {...}, "timestamp": "..."}`. Errors follow the
> `ErrorResponse` format shown in Common Patterns.

### Authentication

| Group | Auth | Description |
|-------|------|-------------|
| Public | None | Branch listings, search offers |
| Customer | `X-User-Id` (JWT) | View own rentals |
| Partner | `X-User-Id` + `FLEET_MANAGE` | Manage own vehicles, branches, rates, and documents |
| Admin | `X-User-Id` (ADMIN role) | Approve/reject photos, view all vehicles |
| Internal | `X-Service-Token` | Car hold management (package-booking-service) |

Individual partners (LegalType=INDIVIDUAL) are **rejected by default** when creating a branch. Set the env var
`CAR_INDIVIDUAL_PARTNERS_ENABLED=true` to allow them.

### Public: Branches and Offers

#### `GET /api/car-branches?city=` - List Sellable Branches (Public)

No auth. Returns only active branches (and only branches from partners whose organization has at least one
sellable vehicle — `sellable=true`). Returns public fields only (no `active`, `createdAt`, `updatedAt`).

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `city` | String | No | Filter by city (case-insensitive) |

Response: `List<PublicBranchResponse>` — `{id, organizationId, name, city, address, openTime, closeTime}`.

#### `GET /api/car-offers` - Get Vehicle Offers (Public)

No auth. Returns all active offers matching the search criteria. An "offer" represents an available vehicle
matching the requested category, transmission, and dates at the given branches.

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `pickupBranchId` | UUID | Yes | Pickup branch |
| `returnBranchId` | UUID | Yes | Return branch |
| `pickupAt` | LocalDateTime | Yes | Pickup time (Istanbul local) |
| `returnAt` | LocalDateTime | Yes | Return time (Istanbul local) |
| `driverAge` | int | No | Driver age (default 18) |

Response: `List<OfferResponse>` — `{pickupBranchId, returnBranchId, organizationId, category, transmission,
exampleModel, seatCount, fuelType, chargeableDays, dailyRate, oneWayFee, totalAmount, currency, depositAmount,
kmLimitPerDay, extraKmFee, minDriverAge, minLicenseYears, authorizationDocumentNumber}`.

### Customer: Rentals

#### `GET /api/rentals/me` - List My Rentals (Customer)

`X-User-Id` required. Returns the authenticated user's rentals, newest first.

Response: `List<RentalResponse>` — `{id, organizationId, customerId, vehicleId, pickupBranchId, returnBranchId,
category, transmission, pickupAt, returnAt, chargeableDays, dailyRate, oneWayFee, totalAmount, currency,
depositAmount, kmLimitPerDay, driverAge, status, holdExpiresAt, createdAt, updatedAt}`.

#### `GET /api/rentals/{id}` - Get Rental (Customer / Admin)

`X-User-Id` required. Customers see their own rentals only.

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `X-User-Role` | String | No | Pass `ADMIN` to view any rental |

Admins (with `X-User-Role=ADMIN` header) can view any rental by ID.

### Partner: Fleet Management (FLEET_MANAGE)

All partner endpoints are at `/api/partner/organizations/{orgId}`. The caller's `X-User-Id` must hold
`FLEET_MANAGE` on the organization. Branches and vehicles belong to an organization.

#### Branches

##### `POST /api/partner/organizations/{orgId}/branches` - Create Branch (Partner)

Creates a new branch for the organization. 201 Created.

```json
// Request
{
  "name": "Istanbul Airport Branch",
  "city": "Istanbul",
  "address": "IST Terminal 1",
  "phone": "+902120000000",
  "openTime": "08:00",
  "closeTime": "22:00",
  "active": true
}
```

##### `GET /api/partner/organizations/{orgId}/branches` - List Branches (Partner)

Returns all branches for the organization, newest first.

Response: `List<BranchResponse>` — `{id, organizationId, name, city, address, phone, openTime, closeTime, active, createdAt, updatedAt}`.

##### `PUT /api/partner/organizations/{orgId}/branches/{id}` - Update Branch (Partner)

Updates an existing branch (same body as create).

##### `DELETE /api/partner/organizations/{orgId}/branches/{id}` - Delete Branch (Partner)

Deletes a branch. The endpoint is not yet implemented.

#### Vehicles

##### `POST /api/partner/organizations/{orgId}/vehicles` - Create Vehicle (Partner)

Creates a new vehicle for the organization. 201 Created.

```json
// Request
{
  "homeBranchId": "uuid",
  "plateNumber": "34 ABC 123",
  "brand": "Toyota",
  "model": "Corolla",
  "year": 2024,
  "category": "COMPACT",
  "transmission": "AUTOMATIC",
  "fuelType": "PETROL",
  "seatCount": 5,
  "active": true
}
```

##### `GET /api/partner/organizations/{orgId}/vehicles` - List Vehicles (Partner)

Returns all vehicles for the organization, newest first.

Response: `List<VehicleResponse>` — `{id, organizationId, homeBranchId, plateNumber, brand, model, year, category,
transmission, fuelType, seatCount, active, sellable, missingDocuments, createdAt, updatedAt}`.

`sellable` indicates whether the vehicle can be offered in public searches. `missingDocuments` lists
required document types the vehicle still needs.

##### `PUT /api/partner/organizations/{orgId}/vehicles/{id}` - Update Vehicle (Partner)

Updates an existing vehicle (same body as create).

##### `DELETE /api/partner/organizations/{orgId}/vehicles/{id}` - Delete Vehicle (Partner)

Deletes a vehicle. The endpoint is not yet implemented.

##### `POST /api/partner/organizations/{orgId}/vehicles/{id}/deactivate` - Deactivate Vehicle (Partner)

Sets `active=false` on the vehicle. It will no longer appear in public offers.

##### `POST /api/partner/organizations/{orgId}/vehicles/{id}/activate` - Activate Vehicle (Partner)

Sets `active=true` on the vehicle. It will appear in public offers if all required documents are present.

#### Rates

##### `GET /api/partner/organizations/{orgId}/rates` - List Rates (Partner)

Returns all rates for the organization's branches.

Response: `List<RateResponse>` — `{id, organizationId, branchId, category, transmission, currency, dailyRate,
depositAmount, kmLimitPerDay, extraKmFee, minDriverAge, minLicenseYears}`.

##### `PUT /api/partner/organizations/{orgId}/rates` - Upsert Rates (Partner)

Replaces all rates for the organization. 200 OK with the updated list.

```json
// Request
{
  "items": [
    {
      "branchId": "uuid",
      "category": "COMPACT",
      "transmission": "AUTOMATIC",
      "currency": "TRY",
      "dailyRate": 350.00,
      "depositAmount": 1000.00,
      "kmLimitPerDay": 200,
      "extraKmFee": 5.00,
      "minDriverAge": 21,
      "minLicenseYears": 2
    }
  ]
}
```

#### One-Way Routes

##### `GET /api/partner/organizations/{orgId}/one-way-routes` - List Routes (Partner)

Returns all one-way route surcharges.

Response: `List<RouteResponse>` — `{id, organizationId, fromBranchId, toBranchId, fee, currency}`.

##### `PUT /api/partner/organizations/{orgId}/one-way-routes` - Replace Routes (Partner)

Replaces all one-way routes for the organization.

```json
// Request
{
  "routes": [
    {
      "fromBranchId": "uuid",
      "toBranchId": "uuid",
      "fee": 150.00,
      "currency": "TRY"
    }
  ]
}
```

#### Documents

##### `GET /api/partner/organizations/{orgId}/documents` - List Documents (Partner)

Returns all documents (org-level and vehicle-level) for the organization.

Response: `List<DocumentResponse>` — `{id, organizationId, vehicleId, type, documentNumber, validUntil,
status, rejectReason, reviewedBy, reviewedAt, fileName, contentType, sizeBytes, uploadedBy, createdAt, updatedAt}`.

`status` is `PENDING`, `APPROVED`, or `REJECTED`.

##### `POST /api/partner/organizations/{orgId}/documents` - Upload Document (Partner)

Uploads a document. 201 Created.

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | MultipartFile | Yes | Document file |
| `type` | DocumentType | Yes | RENTAL_AUTHORIZATION, TAX_CERTIFICATE, IDENTITY_DOCUMENT, VEHICLE_REGISTRATION, RENTAL_INSURANCE, TRAFFIC_INSURANCE |
| `documentNumber` | String | Yes | Document number |
| `validUntil` | LocalDate | No | Expiry date (null = no expiry) |
| `vehicleId` | UUID | No | If null, document is org-level; otherwise vehicle-level |

##### `GET /api/partner/organizations/{orgId}/documents/{id}/file` - Download Document (Partner)

Returns the raw file with `Content-Disposition: attachment`.

#### Partner Rentals

##### `GET /api/partner/organizations/{orgId}/rentals` - List Partner Rentals (Partner)

Returns all rentals for the organization's branches, newest first.

#### Partner Status

##### `GET /api/partner/organizations/{orgId}/status` - Partner Status (Partner)

Returns the partner's readiness status.

Response: `{sellable: boolean, missingOrgDocuments: [DocumentType...], individualGateOpen: boolean}`.

### Admin: Documents and Vehicles

#### Car Documents

##### `GET /api/admin/car-documents?status=` - List Documents (Admin)

Lists all documents across all organizations. Filters by `status` (PENDING, APPROVED, REJECTED) if provided.

Response: `List<AdminDocumentResponse>` — `{id, organizationId, vehicleId, type, documentNumber, validUntil,
status, rejectReason, reviewedBy, reviewedAt, fileName, contentType, sizeBytes, uploadedBy, createdAt, updatedAt,
organizationName, legalType, vehicleLabel}`.

##### `GET /api/admin/car-documents/{id}/file` - Download Document (Admin)

Returns the raw file.

##### `POST /api/admin/car-documents/{id}/approve` - Approve Document (Admin)

Approves a document. Changes `PENDING` → `APPROVED`. Requires `X-User-Id` (admin).

##### `POST /api/admin/car-documents/{id}/reject` - Reject Document (Admin)

Rejects a document. Requires `X-User-Id` (admin) and body: `{"reason": "..."}`.

#### Admin Vehicles

##### `GET /api/admin/car-vehicles` - List All Vehicles (Admin)

Returns all vehicles across all organizations with enrichment.

Response: `List<AdminVehicleResponse>` — `{id, organizationId, homeBranchId, plateNumber, brand, model, year,
category, transmission, fuelType, seatCount, active, organizationName, homeBranchName, sellable, missingDocuments}`.

### Internal: Car Holds

Called by package-booking-service directly (not through gateway). Requires a signed `X-Service-Token`.

#### `POST /internal/car-holds` - Create Car Hold (Internal)

Reserves an available vehicle matching the search criteria. Returns 409 if a hold already exists for the
same criteria (idempotent replay).

```json
// Request
{
  "pickupBranchId": "uuid",
  "returnBranchId": "uuid",
  "category": "COMPACT",
  "transmission": "AUTOMATIC",
  "pickupAt": "2026-12-01T10:00:00",
  "returnAt": "2026-12-05T10:00:00",
  "driverAge": 30,
  "principalUserId": "uuid",
  "idempotencyKey": "co-...",
  "orderCorrelationId": "uuid"
}
```

Response: `{holdId, organizationId, pickupBranchId, returnBranchId, category, transmission, exampleModel,
pickupAt, returnAt, chargeableDays, currency, totalAmount, depositAmount, kmLimitPerDay, status, expiresAt}`.

The hold TTL is configured via `car.holdTtlMinutes` (default 15 minutes).

#### `POST /internal/car-holds/{id}/confirm` - Confirm Hold (Internal)

Converts the hold into a confirmed rental. Returns 422 if expired or already released.

#### `POST /internal/car-holds/{id}/release` - Release Hold (Internal)

Cancels the hold and frees the reserved vehicle.

### Customer Car Orders (via Package Booking Service)

#### `POST /api/orders/car` - Create Car Order (Customer)

`X-User-Id` required. Calls car-rental-service to place a hold, then creates an order with the held price.

```json
// Request
{
  "pickupBranchId": "uuid",
  "returnBranchId": "uuid",
  "category": "COMPACT",
  "transmission": "AUTOMATIC",
  "pickupAt": "2026-12-01T10:00:00",
  "returnAt": "2026-12-05T10:00:00",
  "driverAge": 30
}
```

Response: `{success, data: {id, customerId, status, currency, totalAmount, notes, version, holdExpiresAt,
items:[{id, orderId, vertical: "CAR", supplierOrganizationId, productRef: pickupBranchId, fulfillmentRef: holdId,
description: "ExampleModel (TurkishCategory, TurkishTransmission), X gun", unitAmount, quantity, currency,
pickupAt, returnAt}], createdAt, updatedAt}}`

Errors: 401 no identity, 403 ADMIN_CANNOT_PURCHASE, 409 HOLD_CONFLICT (car-rental-service returned 409, e.g. NO_VEHICLE_AVAILABLE for the slot), 422 BUSINESS_RULE_VIOLATION (car-rental-service 422: ROUTE_NOT_OFFERED, DRIVER_TOO_YOUNG, OUTSIDE_BRANCH_HOURS, INVALID_TIME, NO_RATE, INDIVIDUAL_PARTNERS_DISABLED, PARTNER_NOT_SELLABLE). Same request from the same user within the hold TTL returns the same hold (idempotent).
503 if car-rental-service is unavailable.

#### `GET /api/orders/{id}` - Get own order (403 if not owner)
#### `GET /api/orders` - List caller's own orders
#### `POST /api/orders/{id}/cancel` - Cancel own order

Cancelling releases the car hold via the payment escrow refund flow.

---

## 5. Package Booking Service

**Port:** 8085 | **DB:** package_booking_db

> The `/api/bookings` endpoints below are legacy and internal-only (see "Legacy bookings"). Customers use
> `/api/orders/hotel`, `/api/orders/car`, `/api/orders/flight`, `/api/orders/bus` and `/api/orders/package`. Legacy `/api/bookings` responses carry a `Deprecation: true` header.

#### `POST /api/bookings` - Create Booking

```json
// Request
{
  "customerId": "customer-uuid",              // required
  "flightId": "flight-uuid",                  // optional
  "hotelReservationId": "hotel-res-uuid",     // optional
  "vehicleReservationId": "vehicle-res-uuid", // optional
  "notes": "Honeymoon package"                // optional
}

// Response: 201 Created - status defaults to PENDING
{
  "success": true,
  "data": {
    "id": "uuid",
    "customerId": "customer-uuid",
    "flightId": "flight-uuid",
    "hotelReservationId": "hotel-res-uuid",
    "vehicleReservationId": "vehicle-res-uuid",
    "status": "PENDING",
    "totalPrice": null,
    "notes": "Honeymoon package",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/bookings/{id}` - Get Booking
#### `GET /api/bookings` - List All
#### `GET /api/bookings/customer/{customerId}` - By Customer

#### `GET /api/bookings/search` - Search Bookings

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | No | Customer filter |
| `status` | BookingStatus | No | PENDING, CONFIRMED, CANCELLED, FAILED, PARTIALLY_CONFIRMED |

#### `PUT /api/bookings/{id}` - Update Booking
#### `DELETE /api/bookings/{id}` - Delete Booking (204)
#### `POST /api/bookings/{id}/status?status=CONFIRMED` - Update Status
#### `POST /api/bookings/{id}/cancel` - Cancel Booking

### Orders (customer)

`X-User-Id` is set by api-gateway from a verified JWT. There is no generic `POST /api/orders` any more
(removed: it trusted client prices). Prices come from the server-side hotel hold.

#### `POST /api/orders/hotel` - Create hotel order (201)

Body: `{ "quoteId": "uuid", "notes": "optional", "guestFirstName": "optional", "guestLastName": "optional" }`. Price/currency come from the hotel-service hold, client price
fields are ignored. Repeating the same quote returns the same order. Errors: 401 no identity,
403 `ADMIN_CANNOT_PURCHASE` (platform ADMIN), 404/409/422 passed through from hotel-service (hold rejected, inventory
unavailable), 503 `HOTEL_SERVICE_UNAVAILABLE`.
Optional lead guest `guestFirstName`/`guestLastName` (trimmed, 1-80 letters incl. Turkish, spaces, hyphen, apostrophe; else 400 `GUEST_NAME_INVALID`; null when absent) is stored in the item's passenger name columns. Package `items[]` of vertical HOTEL accept the same two fields.
Hotel order items carry `adults`, `childrenCount` (from the hold) and `guestFirstName`/`guestLastName`; partner order items carry `adults`, `childrenCount`, `passengerFirstName/LastName`; `RedeemResponse` adds `guestFirstName, guestLastName, adults, childrenCount`; the internal hotel hold response adds `adults` and `childrenAges` (list).
Response: `{success, data: {id, customerId, status, currency, quoteId, totalAmount, notes, version, holdExpiresAt,
items:[{id, orderId, vertical, supplierOrganizationId, productRef, fulfillmentRef, description, unitAmount, quantity,
subtotal, currency, createdAt}], createdAt, updatedAt}}`.

#### `POST /api/orders/car` - Create car order (201)

Body: `{ "pickupBranchId": "uuid", "returnBranchId": "uuid", "category": "COMPACT", "transmission": "AUTOMATIC",
"pickupAt": "2026-12-01T10:00:00", "returnAt": "2026-12-05T10:00:00", "driverAge": 30 }`. category and transmission
are optional (any match). Calls car-rental-service to place a hold, then creates an order with the held price.
Errors: 401 no identity, 403 `ADMIN_CANNOT_PURCHASE`, 422 if no vehicle available, 503 `CAR_RENTAL_SERVICE_UNAVAILABLE`.
Idempotent on the same (userId, pickupBranchId, returnBranchId, pickupAt, returnAt) key.
Response: same shape as hotel order with `vertical: "CAR"`, `productRef` = pickupBranchId, `fulfillmentRef` = holdId,
`description` = "ModelName (CategoryLabel, TransmissionLabel), X gun", and `pickupAt`/`returnAt` set.

#### `GET /api/orders/{id}` - Get own order (403 if not owner)
#### `GET /api/orders` - List caller's own orders
#### `POST /api/orders/{id}/cancel?expectedVersion=0` - Cancel own order

`expectedVersion` optional, stale -> 409 `CONCURRENT_MODIFICATION`. Already `CANCELLED`/`FAILED` -> 422.
Cancelling releases the hotel hold (and car hold).

### Legacy bookings: internal only

`/api/bookings/**` is no longer a customer API and is **deprecated**: every response carries a `Deprecation: true` header (`LegacyBookingAccessFilter`; use `/api/orders`). Allowed: calls carrying a valid `X-Internal-Token` (saga,
notification-service) and read-only GET/HEAD by a gateway-verified ADMIN. Everything else gets 403.

### Partner order items

#### `GET /api/partner/organizations/{organizationId}/order-items`

Query: `status` (PENDING|CONFIRMED|CANCELLED|FAILED, optional), `from`, `to` (ISO dates, UTC, inclusive, on order
creation time), `page` (default 0), `size` (default 20, max 100). Routed by the gateway to package-booking-service
for this exact path shape only. Only items whose `supplierOrganizationId` equals the path organization are returned,
newest first.
Authorization: caller `X-User-Id` must hold `RESERVATION_READ` on the organization (auth-service `GET /internal/access`;
granted to ORGANIZATION_OWNER, HOTEL_OWNER, HOTEL_MANAGER, HOTEL_STAFF). Status codes: 200, 401 no identity,
403 not a member / no permission, 503 `AUTHORIZATION_UNAVAILABLE` (auth-service unreachable, fails closed).
```json
{ "success": true, "data": { "items": [ { "orderItemId": "uuid", "orderId": "uuid", "orderStatus": "CONFIRMED",
  "orderCreatedAt": "...", "description": "...", "amount": 200.00, "currency": "USD", "fulfillmentRef": "uuid" } ],
  "page": 0, "size": 20, "total": 1 } }
```
No guest name, email, phone, payment data or other items are returned (no CRM lookup is done).

### Added endpoints (orders, vouchers, admin orders, bookings)

#### `POST /api/orders/flight` - Create flight order (customer, 201)

Platform ADMIN gets 403 `ADMIN_CANNOT_PURCHASE`. Body (`FlightOrderCreateRequest`): `offerId` (required, max 600, from `GET /api/flight-offers`),
`seatNumber` (optional, max 5), `passengerFirstName` (required, max 80, letters, space, apostrophe, hyphen), `passengerLastName` (same rules),
`promoCode` (optional, max 50). Response `data`: `OrderResponse` (same as the other order creators).

#### `POST /api/orders/bus` - Create bus order (customer, 201)

ADMIN gets 403. Body (`BusOrderCreateRequest`): `tripId` (required UUID), `fromSeq` (int, >=0), `toSeq` (int, >=1), `passengers` (required, 1..5 of
`{seatNo (int >=1), name (required, 2..150), gender (required, FEMALE|MALE), identityNumber (optional, max 20)}`), `promoCode` (optional, max 50).

#### `POST /api/orders/package` - Create multi-item order (customer, 201)

ADMIN gets 403. Body (`PackageOrderCreateRequest`): `items` (required, 1..10), `promoCode` (optional, max 50), `notes` (optional, max 1000).
Each item: `vertical` (required, `HOTEL | FLIGHT | CAR | BUS`) plus the fields of that vertical's single-item request:
HOTEL `quoteId`; CAR `pickupBranchId, returnBranchId, category, transmission, pickupAt, returnAt, driverAge`;
FLIGHT `offerId, seatNumber, passengerFirstName, passengerLastName`; BUS `tripId, fromSeq, toSeq, passengers`.
Only the fields that belong to the item's vertical are read. Which of them are mandatory per vertical is enforced in the service, not by annotations, so
the failure mode of a missing field was not documented precisely.

`OrderResponse`: `{id, customerId, status, currency, quoteId, totalAmount, notes, version, items: [OrderItemResponse], createdAt, updatedAt, holdExpiresAt}`.
`OrderItemResponse` includes `{id, orderId, vertical, supplierOrganizationId, productRef, fulfillmentRef, description, unitAmount, quantity, subtotal,
currency, createdAt, pickupAt, returnAt, flight: {flightNumber, departureAt, seatNumber, passengerFirstName, passengerLastName, pnr, ticketNumber, supplier},
grossAmount, discountAmount, discountFundedBy, netAmount, taxAmount, promoCode, cancellationPolicy, cancelFreeHoursBefore, freeCancellationUntil (Instant: FREE_UNTIL = serviceStart - hours, FREE_CANCELLATION = serviceStart, null for NON_REFUNDABLE or unknown start), cancelOutcome, refundAmount}`. Hotel hold response also carries `freeCancellationDaysBefore` (nullable).

#### `GET /api/orders/{id}/cancellation-preview` - Preview cancellation (customer)

Owner only. Response `data`: `{orderId, cancellable, blockedReason, currency, totalRefund, items: [{orderItemId, description, policy, freeHoursBefore,
outcome, chargedAmount, refundAmount}]}`.

#### `GET /api/orders/{id}/vouchers` - Order vouchers (customer)

Owner only. Response: list of `{voucherId, orderItemId, description, payload, status, validFrom, validUntil, redeemedAt}`.

#### `POST /api/partner/organizations/{organizationId}/vouchers/redeem` - Redeem a voucher (partner + RESERVATION_REDEEM)

Body `{payload}` (required, max 200, the QR payload). Permission is checked through auth-service (fails closed with 503 `AUTHORIZATION_UNAVAILABLE`).
Response `data`: `{voucherId, orderItemId, description, amountReleased, currency, redeemedAt}`. Redeeming releases the escrow of that item.

#### `GET /api/partner/organizations/{organizationId}/order-items` - fields added

Items also carry `pickupAt, returnAt, vertical, checkIn, checkOut, passengerFirstName, passengerLastName, voucherStatus, cancelOutcome, refundAmount`
(`PartnerOrderItemResponse`). Permission `RESERVATION_READ`.

#### `GET /api/admin/orders?status=&vertical=&from=&to=&page=&size=` - All orders (ADMIN)

`status` (`OrderStatus`), `vertical` (`HOTEL | FLIGHT | CAR | BUS`), `from`/`to` (ISO dates) are optional, `page` default 0, `size` default 20.
Response `data`: `{items: [{id, customerId, status, paymentState, currency, totalAmount, createdAt, items: [{id, vertical, description, netAmount,
promoCode, cancelOutcome, refundAmount}]}], page, size, total}`. 403 `Administrator role required` otherwise.

#### `GET /api/bookings/flight/{flightId}` - Legacy bookings by flight

Legacy and internal-only like the rest of `/api/bookings/**` (see "Legacy bookings" below). Returns `List<PackageBookingResponse>`
`{id, customerId, flightId, hotelReservationId, vehicleReservationId, status, totalPrice, notes, createdAt, updatedAt}`.


---

## 6. Pricing Service

**Port:** 8086 | **DB:** pricing_db

#### `POST /api/pricing-rules` - Create Pricing Rule

```json
// Request
{
  "name": "Economy Flight Base",           // required
  "pricingType": "FLIGHT",                 // required: FLIGHT, HOTEL, CAR_RENTAL, PACKAGE_DISCOUNT
  "basePrice": 1200.00,                    // required, min 0
  "discountPercentage": 10.0,              // optional
  "currency": "TRY",                       // required, exactly 3 chars
  "description": "Standard economy fare"   // optional
}

// Response: 201 Created - active defaults to true
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Economy Flight Base",
    "pricingType": "FLIGHT",
    "basePrice": 1200.00,
    "discountPercentage": 10.0,
    "currency": "TRY",
    "active": true,
    "description": "Standard economy fare",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/pricing-rules/{id}` - Get Rule
#### `GET /api/pricing-rules` - List All
#### `GET /api/pricing-rules/type/{pricingType}` - By Type
#### `GET /api/pricing-rules/type/{pricingType}/active` - Active Rules by Type
#### `PUT /api/pricing-rules/{id}` - Update Rule
#### `DELETE /api/pricing-rules/{id}` - Delete Rule (204)
#### `POST /api/pricing-rules/{id}/deactivate` - Deactivate
#### `POST /api/pricing-rules/{id}/activate` - Activate

### Added endpoints (pricing preview and admin)

Admin routes live under `/api/admin/*`. `AdminRoleFilter` enforces `X-User-Role=ADMIN` on POST, PUT, PATCH and DELETE (403 otherwise).
GET requests are not role-checked by this filter. Responses here are **not** wrapped in the `{success,data}` envelope: they return the DTO or list directly.

#### `POST /api/pricing/preview` - Price preview (customer)

Body (`PreviewRequest`, no validation annotations): `vertical` (String), `supplierOrganizationId` (UUID), `grossAmount` (BigDecimal), `currency` (String),
`promoCode` (String, optional). Response: `{netAmount, discountLines: [{description, amount, fundedBy}], taxAmount}`.

#### Campaigns

#### `POST /api/admin/campaigns` - Create campaign (ADMIN)

Body (`CampaignRequest`): `name` (required), `vertical` (required), `scopeOrgId` (UUID, optional), `discountType` (required, `PERCENTAGE | FIXED`),
`discountValue` (required), `minOrderAmount` (optional, defaults to 0), `startAt` (required), `endAt` (required), `maxUsesGlobal`, `maxUsesPerUser` (optional ints),
`fundedBy` (required, `PLATFORM | PARTNER`), `description` (optional). Admin may only create `PLATFORM`-funded campaigns: `PARTNER` returns a business-rule error
`PARTNER_FUNDING_REQUIRES_CONSENT`. Response: `{id, name, vertical, scopeOrgId, discountType, discountValue, minOrderAmount, startAt, endAt, maxUsesGlobal,
maxUsesPerUser, fundedBy, description, createdAt, updatedAt}`.

#### `GET /api/admin/campaigns` - List campaigns (ADMIN)
#### `GET /api/admin/campaigns/{id}` - Get campaign (ADMIN)
#### `PATCH /api/admin/campaigns/{id}` - Update campaign (ADMIN)

PATCH body (`CampaignUpdateRequest`): `name`, `description`, `endAt`, all optional. Response: campaign.

#### `DELETE /api/admin/campaigns/{id}` - Delete campaign (ADMIN)

204 No Content.

#### Promo codes and tax rates

#### `POST /api/admin/promo-codes` - Create promo codes (ADMIN)

Body (`PromoCodeRequest`): `campaignId` (required UUID), `codes` (required list of `{code (required), maxUsesTotal (optional), validFrom (required), validUntil (required)}`).
Response: list of `{id, campaignId, code, maxUsesTotal, usesCount, validFrom, validUntil}`.

#### `GET /api/admin/campaigns/{campaignId}/promo-codes` - List codes of a campaign (ADMIN)
#### `DELETE /api/admin/promo-codes/{id}` - Delete promo code (ADMIN, 204)

#### `POST /api/admin/tax-rates` - Create tax rate (ADMIN)

Body (`TaxRateRequest`): `vertical` (required), `scopeOrgId` (optional), `taxType` (required), `rate` (required, 0.0..1.0), `inclusive` (required Boolean),
`validFrom` (required). Response: `{id, vertical, scopeOrgId, taxType, rate, inclusive, validFrom}`.

#### `GET /api/admin/tax-rates` - List tax rates (ADMIN)
#### `GET /api/admin/tax-rates/{id}` - Get tax rate (ADMIN)
#### `DELETE /api/admin/tax-rates/{id}` - Delete tax rate (ADMIN, 204)


---

## 7. Payment Service

**Port:** 8087 | **DB:** payment_db

> **Note:** Currently uses mock payment (auto-completes with MOCK transaction ref).

#### `POST /api/payments` - Create Payment

```json
// Request
{
  "bookingId": "booking-uuid",    // required
  "customerId": "customer-uuid",  // required
  "amount": 4500.00,              // required, min 0
  "currency": "TRY",              // required
  "paymentMethod": "CREDIT_CARD"  // optional: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, MOCK
}

// Response: 201 Created - auto-completed with MOCK ref
{
  "success": true,
  "data": {
    "id": "uuid",
    "bookingId": "booking-uuid",
    "customerId": "customer-uuid",
    "amount": 4500.00,
    "currency": "TRY",
    "paymentMethod": "CREDIT_CARD",
    "status": "COMPLETED",
    "transactionRef": "MOCK-a1b2c3d4",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/payments/{id}` - Get Payment
#### `GET /api/payments` - List All
#### `GET /api/payments/booking/{bookingId}` - By Booking
#### `GET /api/payments/customer/{customerId}` - By Customer
#### `POST /api/payments/{id}/refund` - Refund Payment

### Added endpoints (payment intents, partner earnings, admin finance)

Payment-service responses use `ApiResponse<T>`. `X-User-Id` is required (401 `Authentication required`), admin endpoints return 403 `Administrator role required`.

#### `POST /api/payments/orders/{orderId}/intent` - Create payment intent (customer, 201)

No body. `X-User-Id` and `X-User-Role` are passed to the service, which decides ownership. Response `IntentResponse`: `{id, orderId, amount, currency, status, provider, testMode,
failureCode, createdAt, updatedAt}` (`testMode` is true when the provider is `TEST`).

#### `POST /api/payments/intents/{id}/confirm` - Confirm payment intent (customer)

Body `{paymentToken}` (required, non-blank). Response: `IntentResponse`.

#### `GET /api/payments/orders/{orderId}` - Payment state of an order (customer)

Response `data`: `{intent: IntentResponse, items: [{orderItemId, vertical, amount, currency, status, releaseTrigger, heldAt, releasedAt, refundedAt}]}`.

#### `GET /api/partner/organizations/{organizationId}/earnings?from=&to=` - Partner earnings (partner + RESERVATION_READ)

`from`, `to` optional ISO dates. Response `data`: `{currency, totals: {held, released, refunded}, items: [{orderItemId, vertical, gross, commission, partnerNet,
escrowState, heldAt, releasedAt}]}`.

#### `GET /api/admin/commission-rates` - List commission rates (ADMIN)
#### `POST /api/admin/commission-rates` - Create commission rate (ADMIN)

Body: `vertical` (required, `HOTEL | CAR | FLIGHT | BUS`), `supplierOrganizationId` (UUID, optional, supplier-specific override), `rate` (required, 0..1,
max 1 integer and 4 fraction digits), `validFrom` (optional instant). Response: `{id, vertical, supplierOrganizationId, rate, validFrom, createdBy, createdAt}`.

#### `GET /api/admin/ledger?orderId=` - Order ledger (ADMIN)

`orderId` required. Response `data`: `{orderId, balance, entries: [{entryId, groupId, occurredAt, orderItemId, account, debitAmount, creditAmount, currency,
correlationId, description}]}`.

#### `GET /api/admin/supplier-accounts` - Supplier prepaid accounts (ADMIN)

Response: list of `{supplier, currency, balance, updatedAt}`.

#### `POST /api/admin/supplier-accounts/{supplier}/topups` - Top up a supplier account (ADMIN, 201)

Body: `amount` (required, 0.01..10000000.00), `currency` (required, 3 uppercase letters), `reference` (required, max 200), `idempotencyKey` (required, max 150).
Response: the account (`{supplier, currency, balance, updatedAt}`), message `Top-up recorded`.

#### `GET /api/admin/supplier-accounts/{supplier}/ledger?page=&size=` - Supplier ledger (ADMIN)

`page` default 0, `size` default 20. Response `data`: `{items: [{id, entryType, amount, balanceAfter, currency, orderId, orderItemId, reference, createdBy,
createdAt}], total, page, size}`.


---

## 8. CRM Service

**Port:** 8088 | **DB:** crm_db

#### `POST /api/customers` - Create Customer

```json
// Request
{
  "firstName": "Ahmet",                // required
  "lastName": "Yilmaz",               // required
  "email": "ahmet@example.com",       // required, valid email, unique
  "phone": "+905551234567",            // optional
  "passportNumber": "U12345678"        // optional
}

// Response: 201 Created - tier defaults to BRONZE, loyaltyPoints to 0
{
  "success": true,
  "data": {
    "id": "uuid",
    "firstName": "Ahmet",
    "lastName": "Yilmaz",
    "email": "ahmet@example.com",
    "phone": "+905551234567",
    "passportNumber": "U12345678",
    "tier": "BRONZE",
    "loyaltyPoints": 0,
    "active": true,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/customers/{id}` - Get Customer
#### `GET /api/customers` - List All
#### `GET /api/customers/email/{email}` - By Email
#### `GET /api/customers/tier/{tier}` - By Tier (BRONZE, SILVER, GOLD, PLATINUM)
#### `GET /api/customers/search?q=ahmet` - Search by Name
#### `PUT /api/customers/{id}` - Update Customer
#### `DELETE /api/customers/{id}` - Delete Customer (204)
#### `POST /api/customers/{id}/loyalty-points?points=100` - Add Loyalty Points

### Added endpoints (CRM)

#### `GET /api/customers/me` - My customer profile (customer)

`X-User-Id` required. Response `data`: `{id, firstName, lastName, email, loyaltyEnabled, loyaltyPoints, tier}` (`loyaltyPoints` and `tier` are only meaningful
when `loyaltyEnabled` is true). `tier`: `BRONZE | SILVER | GOLD | PLATINUM`.


---

## 9. Check-in Service

**Port:** 8089 | **DB:** checkin_db

#### `POST /api/checkins` - Check In

```json
// Request
{
  "bookingId": "booking-uuid",    // required
  "customerId": "customer-uuid",  // required
  "flightId": "flight-uuid",      // required
  "seatNumber": "12A"             // optional
}

// Response: 201 Created - status CHECKED_IN, checkedInAt set
{
  "success": true,
  "data": {
    "id": "uuid",
    "bookingId": "booking-uuid",
    "customerId": "customer-uuid",
    "flightId": "flight-uuid",
    "seatNumber": "12A",
    "boardingPassUrl": null,
    "status": "CHECKED_IN",
    "checkedInAt": "2026-10-11T12:00:00Z",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/checkins/{id}` - Get Check-in
#### `GET /api/checkins` - List All
#### `GET /api/checkins/booking/{bookingId}` - By Booking
#### `GET /api/checkins/flight/{flightId}` - By Flight
#### `POST /api/checkins/{id}/boarding-pass` - Issue Boarding Pass

```json
// Response - status BOARDING_PASS_ISSUED, boardingPassUrl set
{
  "data": {
    "status": "BOARDING_PASS_ISSUED",
    "boardingPassUrl": "https://waylo.com/boarding-pass/{id}"
  }
}
```

#### `POST /api/checkins/{id}/cancel` - Cancel Check-in

### Added endpoints (order-item check-in)

Check-in is per flight order item (QR/PNR flow). The older booking-based endpoints above remain.

#### `POST /api/checkins/order-items/{orderItemId}` - Check in (customer)

No body. `X-User-Id` required; only the item's owner can check in. Response `data` (`OrderCheckinResponse`): `{id, orderItemId, orderId, customerId, pnr,
passengerName, flightNumber, departureAt, seatNumber, status, checkedInAt}`, message `Checked in`.

#### `GET /api/checkins/me` - My check-ins (customer)

Response: list of `OrderCheckinResponse`.

#### `GET /api/admin/checkins?page=&size=` - All check-ins (ADMIN)

`page` default 0, `size` default 20. Response `data`: `{items: [OrderCheckinResponse], page, size, total}`. 403 `FORBIDDEN` for non-ADMIN.


---

## 10. Notification Service

**Port:** 8090 | **DB:** notification_db

> **Note:** Currently uses mock notification (auto-marks as SENT).

#### `POST /api/notifications` - Send Notification

```json
// Request
{
  "customerId": "customer-uuid",   // required
  "bookingId": "booking-uuid",     // optional
  "type": "EMAIL",                 // required: EMAIL, SMS, PUSH
  "recipient": "ahmet@example.com",// required
  "subject": "Booking Confirmed",  // optional
  "body": "Your booking has been confirmed." // required
}

// Response: 201 Created - status SENT, sentAt set
```

#### `GET /api/notifications/{id}` - Get Notification
#### `GET /api/notifications` - List All
#### `GET /api/notifications/customer/{customerId}` - By Customer
#### `GET /api/notifications/booking/{bookingId}` - By Booking
#### `POST /api/notifications/{id}/retry` - Retry Failed Notification

### Added endpoints (notifications and push devices)

#### `GET /api/notifications/me` - My notifications (customer)

`X-User-Id` required. Response: list of `{id, customerId, bookingId, type, recipient, subject, body, status, retryCount, sentAt, createdAt, updatedAt}`.

#### `POST /api/me/devices` - Register a push device (customer)

Body: `token` (required, max 512), `platform` (required, `ios | android | web`). Response `data`: `{token, platform, createdAt}`, message `Device registered`.

#### `GET /api/me/devices` - List my devices (customer)

Response: list of `{token, platform, createdAt}`.

#### `DELETE /api/me/devices/{token}` - Unregister a device (customer)

204 No Content.


---

## 11. Saga Orchestrator

**Port:** 8091 | **DB:** saga_db

> **RETIRED.** `package-booking-saga-orchestrator` is out of the runtime path (see `SAGA-DECISION.md`). It sits behind the `legacy-saga` compose profile and
> is not started by `docker compose up` or `scripts/dev-up.sh`. The gateway route `/api/sagas/**` is kept, so these calls return **503** while the service is off.
> Nothing calls it: customer orders use synchronous orchestration in package-booking-service (`/api/orders/**`). The endpoints below are kept for
> reference and apply only if the service is re-enabled (`docker compose --profile legacy-saga up -d --no-deps package-booking-saga-orchestrator`).

#### `POST /api/sagas` - Start Saga

```json
// Request
{
  "bookingId": "booking-uuid",              // required, unique per saga
  "customerId": "customer-uuid",            // required
  "flightId": "flight-uuid",               // optional - adds RESERVE_FLIGHT step
  "hotelReservationId": "hotel-res-uuid",   // optional - adds RESERVE_HOTEL step
  "vehicleReservationId": "vehicle-uuid"    // optional - adds RESERVE_CAR step
}

// Response: 201 Created - Dynamically creates steps based on provided IDs
{
  "success": true,
  "data": {
    "id": "uuid",
    "bookingId": "booking-uuid",
    "customerId": "customer-uuid",
    "status": "STARTED",
    "currentStep": "RESERVE_FLIGHT",
    "failureReason": null,
    "steps": [
      { "id": "uuid", "stepType": "RESERVE_FLIGHT", "status": "PENDING", "stepOrder": 0 },
      { "id": "uuid", "stepType": "RESERVE_HOTEL", "status": "PENDING", "stepOrder": 1 },
      { "id": "uuid", "stepType": "RESERVE_CAR", "status": "PENDING", "stepOrder": 2 },
      { "id": "uuid", "stepType": "PROCESS_PAYMENT", "status": "PENDING", "stepOrder": 3 },
      { "id": "uuid", "stepType": "CONFIRM_BOOKING", "status": "PENDING", "stepOrder": 4 }
    ],
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

#### `GET /api/sagas/{id}` - Get Saga Instance
#### `GET /api/sagas` - List All
#### `GET /api/sagas/booking/{bookingId}` - By Booking ID
#### `GET /api/sagas/status/{status}` - By Status
#### `POST /api/sagas/{id}/compensate` - Trigger Compensation

---

## 12. Traffic Simulator

**Port:** 8095 | No database (in-memory)

> Load testing tool for the Waylo platform.

#### `POST /api/simulations` - Start Simulation

```json
// Request
{
  "scenarioType": "FULL_FLOW",     // required: FLIGHT_SEARCH, HOTEL_SEARCH, CAR_SEARCH, PACKAGE_BOOKING, FULL_FLOW, MIXED
  "concurrentUsers": 50,            // required, min 1
  "durationSeconds": 300,           // required, min 1
  "rampUpSeconds": 30,              // optional, min 0
  "targetBaseUrl": "http://localhost:8080" // optional, defaults to gateway
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "simulationId": "a1b2c3d4",
    "scenarioType": "FULL_FLOW",
    "status": "RUNNING",
    "concurrentUsers": 50,
    "durationSeconds": 300,
    "totalRequests": 0,
    "successfulRequests": 0,
    "failedRequests": 0,
    "avgResponseTimeMs": 0.0,
    "p95ResponseTimeMs": 0.0,
    "p99ResponseTimeMs": 0.0,
    "requestsPerSecond": 0.0,
    "startedAt": "...",
    "completedAt": null
  }
}
```

#### `GET /api/simulations/{simulationId}` - Get Status
#### `GET /api/simulations` - List All
#### `POST /api/simulations/{simulationId}/cancel` - Cancel Simulation

---

## 13. Auth Service and Account

**Port:** 8081 | **DB:** auth_db | Gateway route: `/api/auth/**`, `/api/organizations/**`, `/api/me/**`, `/api/admin/**`

> Auth levels used below: **public** (no token), **customer** (any authenticated user, `X-User-Id` set by the gateway from the JWT),
> **partner + permission** (authenticated and holding a permission on the organization), **ADMIN** (`X-User-Role=ADMIN`).
> Auth-service responses use `{success, message, data, code}` (`code` only on some errors; null fields are omitted).
> Paged admin lists use `{success, message, data: [...], total}`.

### Public auth endpoints (`/api/auth/*`)

All of `/api/auth/*` is public at the gateway (rate limits are listed under "Other changes").

#### `POST /api/auth/register` - Register (public)

| Field | Type | Validation |
|-------|------|-----------|
| `firstName` | String | required |
| `lastName` | String | required |
| `email` | String | required, valid e-mail |
| `password` | String | required, `@StrongPassword` (checked together with the e-mail) |

Response `data`: `{email, emailVerified}`. Errors: 400 validation or `success=false`, 409 duplicate registration.

#### `POST /api/auth/login` - Login (public)

Body: `email` (required, e-mail), `password` (required). Response `data` (`AuthResponse`): `{token, refreshToken, userId, email,
firstName, lastName, role, emailVerified}`. Errors: 401 bad credentials, 403 e-mail not verified (`code` = `EMAIL_NOT_VERIFIED`),
429 account locked.

#### `GET /api/auth/verify?token=` - Verify e-mail (public)

Query `token` (required). 200 on success, 400 on invalid or expired token. `data` is empty.

#### `POST /api/auth/resend-verification?email=` - Resend verification mail (public)

Query `email` (required). Always 200 with an `ApiResponse` (`data` empty).

#### `POST /api/auth/forgot-password` - Request password reset (public)

Body `{email}` (required, valid e-mail). Always 200.

#### `POST /api/auth/reset-password` - Reset password (public)

Body `{token, newPassword}`: `token` required, `newPassword` required and `@StrongPassword`. 400 on invalid token or weak password.

#### `POST /api/auth/refresh` - Refresh tokens (public)

Body `{"refreshToken": "..."}` (a plain map, not a validated DTO). Response `data`: `AuthResponse` (same as login).
Errors: 401 when the body or token is missing or invalid, 403 when the e-mail is no longer verified.

#### `POST /api/auth/logout` - Logout (public)

Body `{"refreshToken": "..."}` optional. If the gateway supplied `X-User-Id`, that user's refresh tokens are revoked,
otherwise the given refresh token is revoked. Always 200.

#### `GET /api/auth/health` - Health (public)

Plain text `Auth service is running` (not enveloped).

### Account (`/api/me`, customer)

All require a valid JWT (401 otherwise). Bodies are validated, errors are 400.

#### `GET /api/me` - Get my profile (customer)

Response `data`: `{id, email, firstName, lastName, role, emailVerified, createdAt}`.

#### `PATCH /api/me` - Update my profile (customer)

Body `{firstName, lastName}`, both required, max 100 chars each. Response: updated profile (same fields as `GET /api/me`).

#### `POST /api/me/password` - Change password (customer)

Body `{currentPassword, newPassword}`: both required, `newPassword` `@StrongPassword`. Response `data`: `AuthResponse` (new tokens).

#### `POST /api/me/email` - Request e-mail change (customer)

Body `{newEmail, currentPassword}`: `newEmail` required, valid e-mail, max 255; `currentPassword` required. Returns **202**; a
verification link is mailed to the new address and the change applies only once the link is confirmed.

#### `DELETE /api/me` - Delete my account (customer)

Body `{"password": "..."}` (required). 204 No Content (see "Other changes").

#### `GET /api/me/memberships` - My organization memberships (customer)

Response `data`: list of `{membershipId, organizationId, organizationName, organizationStatus, organizationBusinessType,
organizationLegalType, status, roles: [RoleGrantResponse]}`.

### Organizations and role grants

#### `POST /api/organizations` - Create organization (customer)

Body: `name` (required, max 255), `businessType` (required, `HOTEL | CAR_RENTAL | BUS`), `legalType` (required, `COMPANY | INDIVIDUAL`).
Returns **201**, `data`: `{id, name, status, businessType, legalType}`.

#### `GET /api/organizations/{organizationId}/members` - List members (partner)

Caller must hold one of the member-viewing roles in the organization (403 otherwise). Response `data`: list of
`{userId, email, name, role, scopeType, scopeId, grantId, createdAt}`.

#### `POST /api/organizations/{organizationId}/role-grants` - Grant a role (partner)

Body: `email` (required, valid), `role` (required, `ORGANIZATION_OWNER | HOTEL_OWNER | HOTEL_MANAGER | HOTEL_STAFF`), `hotelId` (required UUID).
Returns **201**, `data`: `{grantId, userId, role, scopeType, scopeId}`.

#### `DELETE /api/organizations/{organizationId}/role-grants/{grantId}` - Revoke a role (partner)

204 No Content.

### Admin (ADMIN only, 403 otherwise)

#### `POST /api/admin/organizations/{id}/approve` - Approve organization (ADMIN)

No body. `data` empty.

#### `POST /api/admin/organizations/{id}/suspend` - Suspend organization (ADMIN)

Body `{reason}` (required, max 500). `data` empty.

#### `POST /api/admin/users/{id}/role` - Change a user's platform role (ADMIN)

Body `{role}` (required, `USER | ADMIN`). Response `data`: `{id, email, fullName, role, verified, deletedAt}`.

`GET /api/admin/organizations` and `GET /api/admin/users` are described under "Other changes (2026-10-24)".

---

## 14. Bus Service

**Port:** 8096 | **DB:** bus_db | Contract: `BUS-CONTRACT.md`

> Responses use `ApiResponse<T>`: `{success, data, message, timestamp}`. Times are Europe/Istanbul local time.
> Ticket sales are gated by `BUS_SALES_ENABLED` (default false): while false, trip search returns an empty list and the seat map 404s.

| Group | Auth | Description |
|-------|------|-------------|
| Catalogue | public | terminals, sales status, trip search, seat map (GET only) |
| Customer | customer | own bookings |
| Partner | partner + `BUS_MANAGE` on a BUS organization | buses, trips, documents |
| Admin | ADMIN | terminals, documents review, all buses |
| Internal | `X-Service-Token` | hold lifecycle, see "Service-to-service endpoints" |

### Public catalogue

#### `GET /api/bus-sales-status` - Are bus sales enabled (public)

Response `data`: a `Map<String, Boolean>` of sales flags.

#### `GET /api/bus-terminals` - List terminals (public)

Response `data`: `[{id, city, name, active}]`.

#### `GET /api/bus-trips?fromCity=&toCity=&date=` - Search trips (public)

All three required; `date` is ISO date. Response `data`: list of `TripOffer`: `{tripId, organizationId, companyName, code, fromSeq, toSeq,
fromTerminalId, toTerminalId, fromCity, toCity, departureAt, arrivalAt, price, currency, availableSeats, busBrand, busModel, layout}`.

#### `GET /api/bus-trips/{id}/seats?fromSeq=&toSeq=` - Seat map (public)

`fromSeq`, `toSeq` required ints (stop sequence numbers). Response `data`: `{tripId, layout, rowCount, fromSeq, toSeq,
seats: [{seatNo, available, occupantGender}]}`. 404 when sales are disabled or the trip is unknown.

### Customer

#### `GET /api/bus-bookings/me` - My bus bookings (customer)

Response `data`: list of `BookingResponse`: `{id, organizationId, customerId, tripId, fromSeq, toSeq, currency, totalAmount, status,
holdExpiresAt, boardingAt, tickets: [{id, seatNo, passengerName, gender, price, status}], createdAt}`.

#### `GET /api/bus-bookings/{id}` - Get bus booking (customer / ADMIN)

Customers see their own bookings only (403 or 404 otherwise); `X-User-Role=ADMIN` may read any. Response: `BookingResponse`.

Bus orders are created through `POST /api/orders/bus` (see Package Booking, section 5a).

### Partner (`/api/bus-partner/organizations/{orgId}`, `BUS_MANAGE`)

The caller needs `BUS_MANAGE` on the organization (ORGANIZATION_OWNER of a BUS org). 403 when denied, 503 when auth-service is unreachable.

#### `GET /api/bus-partner/organizations/{orgId}/terminals` - List active terminals (partner)

Response: `[TerminalResponse]` (`{id, city, name, active}`).

#### `GET /api/bus-partner/organizations/{orgId}/buses` - List buses (partner)
#### `POST /api/bus-partner/organizations/{orgId}/buses` - Create bus (partner, 201)
#### `PUT /api/bus-partner/organizations/{orgId}/buses/{id}` - Update bus (partner)

Body (`BusRequest`): `plateNumber` (required, max 20), `brand` (required, max 100), `model` (required, max 100), `year` (int, 1980..2100),
`layout` (required, `TWO_PLUS_TWO | TWO_PLUS_ONE`), `rowCount` (int, 5..20), `active` (Boolean, optional).
Response `BusResponse`: `{id, organizationId, plateNumber, brand, model, year, layout, rowCount, seatCount, active, sellable,
missingDocuments, createdAt, updatedAt}`. Errors: 409 duplicate plate or `BUS_HAS_TRIPS` (seat plan cannot change while published trips exist).

#### `POST /api/bus-partner/organizations/{orgId}/buses/{id}/deactivate` - Deactivate bus (partner)
#### `POST /api/bus-partner/organizations/{orgId}/buses/{id}/activate` - Activate bus (partner)

No body. Response: `BusResponse`.

#### `GET /api/bus-partner/organizations/{orgId}/trips` - List trips (partner)
#### `POST /api/bus-partner/organizations/{orgId}/trips` - Create trip (partner, 201)

Body (`TripRequest`): `busId` (required UUID), `code` (required, max 40), `currency` (required, 3 uppercase letters),
`stops` (required, 2..20 items of `{terminalId, arrivalAt, departureAt}`, all required), `fares` (required, 1..200 items of
`{fromSeq (>=0), toSeq (>=1), price (>=0.01)}`). Response `TripResponse`: `{id, organizationId, busId, code, status, currency,
stops: [{seq, terminalId, city, terminalName, arrivalAt, departureAt}], fares: [{fromSeq, toSeq, price}], soldSeats, seatCount}`.

#### `POST /api/bus-partner/organizations/{orgId}/trips/{id}/cancel` - Cancel trip (partner)

No body. Response `TripResponse`.

#### `GET /api/bus-partner/organizations/{orgId}/documents` - List documents (partner)
#### `POST /api/bus-partner/organizations/{orgId}/documents` - Upload document (partner, 201)

`multipart/form-data`: `file` (required), `type` (required, `TRANSPORT_AUTHORIZATION | TAX_CERTIFICATE | VEHICLE_REGISTRATION |
VEHICLE_CARD | TRAFFIC_INSURANCE | SEAT_ACCIDENT_INSURANCE`), `documentNumber` (required), `validUntil` (ISO date, required for types where the contract
demands an expiry), `busId` (UUID, for bus-level types). Response `DocumentResponse`: `{id, organizationId, busId, type, documentNumber,
validUntil, status, rejectReason, reviewedBy, reviewedAt, fileName, contentType, sizeBytes, uploadedBy, createdAt, updatedAt}`.

#### `GET /api/bus-partner/organizations/{orgId}/documents/{id}/file` - Download document file (partner)

Binary body with the stored content type.

#### `GET /api/bus-partner/organizations/{orgId}/bookings` - List bookings of the organization (partner)

Response: list of `BookingResponse`.

#### `GET /api/bus-partner/organizations/{orgId}/status` - Sellability status (partner)

Response `data`: `{sellable, missingOrgDocuments, salesEnabled}`.

### Admin (ADMIN)

#### `GET /api/admin/bus-documents?status=` - List documents (ADMIN)

`status` optional (`DocumentStatus`). Response: list of `{document: DocumentResponse, organizationName, busLabel}`.

#### `GET /api/admin/bus-documents/{id}/file` - Download document file (ADMIN)
#### `POST /api/admin/bus-documents/{id}/approve` - Approve document (ADMIN)
#### `POST /api/admin/bus-documents/{id}/reject` - Reject document (ADMIN)

Reject body `{reason}` (required, max 500). Approve and reject return `DocumentResponse`.

#### `GET /api/admin/bus-vehicles` - List all buses (ADMIN)

Response: list of `{bus: BusResponse, organizationName}`.

#### `GET /api/admin/bus-terminals` - List all terminals (ADMIN)
#### `POST /api/admin/bus-terminals` - Create terminal (ADMIN, 201)
#### `PUT /api/admin/bus-terminals/{id}` - Update terminal (ADMIN)

Body (`TerminalRequest`): `city` (required, max 100), `name` (required, max 150), `active` (Boolean, optional). Response: `TerminalResponse`.

---

## 16. Added 2026-11-24

Full rules: `HOTEL-PUBLICATION-CONTRACT.md` (hotel publication) and `PAYOUT-CONTRACT.md` (payouts). Error bodies use the
usual `{status, error, message, path, timestamp}` envelope with the machine code in `error`; some carry `details`.

### 16.1 Hotel publication (hotel-service)

A hotel is `DRAFT -> SUBMITTED -> APPROVED -> PUBLISHED` (plus `REJECTED`, `SUSPENDED`, `ARCHIVED`). Only PUBLISHED,
active hotels of ACTIVE organizations are public: list, search, detail, availability, anonymous child reads
(room types, rate plans, rates, reviews) and new quotes/holds. Anything else is 404 for the public, and a quote on it is
**422 `HOTEL_NOT_BOOKABLE`**. Hotels that existed before V10 were backfilled as PUBLISHED.

Partner (permission `HOTEL_MANAGE` on the hotel; `HOTEL_STAFF` cannot):

| Method | Path | Body / notes |
|---|---|---|
| POST | `/api/hotels/{hotelId}/publication-requests` | `{note?, revisionId?, expectedVersion?}`. 201 `{id, status: SUBMITTED, hotelPublicationStatus, version}`. 409 `ORGANIZATION_NOT_ACTIVE`, `HOTEL_INCOMPLETE` (message names ROOM_TYPE / RATE_PLAN / INVENTORY), `REQUEST_ALREADY_OPEN`, `INVALID_STATE_TRANSITION` |
| GET | `/api/hotels/{hotelId}/publication-requests` | history, newest first, with decisions (reason, decision; never the admin's id) |
| POST | `/api/hotels/{hotelId}/publication-requests/{requestId}/withdraw` | 200, request `WITHDRAWN` |
| GET | `/api/hotels/{hotelId}/content` | live content + working revision + versions |
| PATCH | `/api/hotels/{hotelId}/content` | `{expectedVersion, content:{...}}`; edits of a PUBLISHED hotel go to a revision that needs review. 409 `VERSION_CONFLICT`, `HOTEL_LOCKED_FOR_REVIEW` |
| DELETE | `/api/hotels/{hotelId}/content/revision` | discard a DRAFT revision, 204 |
| POST | `/api/hotels/{hotelId}/archive` | `{reason}` (DRAFT or REJECTED hotels only) |

Admin (`ADMIN` role; an admin who is a member of the hotel's organization gets 403 `SELF_REVIEW_NOT_ALLOWED`):

| Method | Path | Body / notes |
|---|---|---|
| GET | `/api/admin/hotel-publications?status=&kind=&page=&size=` | `{items, page, size, total}` |
| GET | `/api/admin/hotel-publications/{requestId}` | request fields + `hotel`, `revision`, `diff[{field,from,to}]`, `decisions[]` |
| POST | `/api/admin/hotel-publications/{requestId}/decisions` | `{decision: APPROVE \| REJECT \| APPROVE_AND_PUBLISH, reason?, expectedVersion}`; reason (>= 5 chars) required for REJECT |
| POST | `/api/admin/hotels/{hotelId}/publication-status` | `{decision: PUBLISH \| SUSPEND \| REINSTATE \| ARCHIVE, reason}`; reason required except PUBLISH |

Every transition writes an append-only row in `hotel_publication_decisions`.

### 16.2 Stay check-out (package-booking-service)

`POST /api/partner/organizations/{organizationId}/order-items/{orderItemId}/check-out` (partner, same permission as
voucher redeem). Body `{expectedVersion, checkedOutAt?}` (`checkedOutAt` defaults to now, not in the future, not before
the redemption). Allowed only from voucher `REDEEMED`; repeating it returns 200 with the same `checkedOutAt`.
Response `{orderItemId, voucherStatus, checkedOutAt, version}`. 409 `INVALID_STATE_TRANSITION` (ACTIVE, NO_SHOW, VOID),
409 `VERSION_CONFLICT`. Partner order items expose `checkedOutAt`.

**Reviews now need a recorded check-out:** `POST /api/me/hotel-reviews` returns 422 `REVIEW_NOT_CHECKED_OUT` for a
redeemed stay without check-out (other reasons: `REVIEW_NOT_CHECKED_IN`, `REVIEW_CANCELLED_OR_NO_SHOW`,
`REVIEW_ORDER_NOT_CONFIRMED`; 403 `ORG_MEMBER_CANNOT_REVIEW`).

### 16.3 Partner payouts (payment-service)

No money moves through Waylo here: an admin pays by bank transfer and records the reference. Payable balance =
released escrow (net of commission, past the holdback) + payout ledger deltas; held escrow is shown as an estimate only.
Lists return `{items, page, size, total}`. IBANs are always masked except in the audited admin reveal.

Partner (balance and lists: `RESERVATION_READ`; writes: organization owner, else 403 `NOT_OWNER`):

| Method | Path | Body / notes |
|---|---|---|
| GET | `/api/partner/organizations/{organizationId}/payouts/balance` | per currency: payable, held estimate, reserved, paid, owed, minimum, `canRequest`, `blockedReason` |
| GET | `/api/partner/organizations/{organizationId}/payouts?status=&page=&size=` | history |
| GET | `/api/partner/organizations/{organizationId}/payouts/{payoutId}` | 404 if not this organization's |
| POST | `/api/partner/organizations/{organizationId}/payouts` | header `Idempotency-Key`; `{currency, amount}`. 201 first use, 200 replay, 409 `IDEMPOTENCY_KEY_REUSED` (other body), `BELOW_MINIMUM`, `INSUFFICIENT_PAYABLE_BALANCE`, `NEGATIVE_BALANCE`, `OPEN_PAYOUT`, bank account missing/unverified |
| POST | `/api/partner/organizations/{organizationId}/payouts/{payoutId}/cancel` | only REQUESTED |
| GET / PUT / DELETE | `/api/partner/organizations/{organizationId}/bank-account` | PUT `{accountHolderName, iban}` (TR IBAN, mod-97 checked); a change resets verification; 409 `IBAN_ALREADY_IN_USE` |

Admin (`ADMIN`):

| Method | Path | Body / notes |
|---|---|---|
| GET | `/api/admin/payouts?status=&organizationId=&currency=&page=&size=` | `status` accepts a comma list |
| GET | `/api/admin/payouts/{id}` | detail with events and masked IBAN |
| GET | `/api/admin/payouts/{id}/bank-details` | full IBAN, audited, `Cache-Control: no-store` |
| POST | `/api/admin/payouts/{id}/approve`, `/processing` | `{expectedVersion?}` |
| POST | `/api/admin/payouts/{id}/paid` | `{reference (unique), paidAt? (not future), expectedVersion?}` |
| POST | `/api/admin/payouts/{id}/failed`, `/cancel` | `{reason, expectedVersion?}`; reserved money returns to the balance |
| GET | `/api/admin/bank-accounts?status=PENDING_VERIFICATION` | verification queue (masked) |
| POST | `/api/admin/bank-accounts/{id}/verify`, `/reject` | reject needs `{reason}`; an admin cannot verify an account they changed |
| GET | `/api/admin/organizations/{organizationId}/payout-balance` | partner balance + `adjustmentsNet`, `reconciles` |
| POST | `/api/admin/organizations/{organizationId}/payout-adjustments` | `{currency, amount, direction: DEBIT \| CREDIT, reason, orderItemId?, idempotencyKey}` (e.g. chargeback after release) |

Status flow `REQUESTED -> APPROVED -> PROCESSING -> PAID | FAILED`, `CANCELLED` from REQUESTED/APPROVED. Every
transition writes append-only payout ledger and event rows; `PAYOUT_PAID` / `PAYOUT_FAILED` e-mail the partner.

### 16.4 Kafka dead-letter admin (crm, notification, flight-seat-inventory)

Consumers retry 3 times (1s, 2s, 4s), then publish the record to `<topic>.DLT` (original key, value and headers plus
exception/offset headers); deserialization and validation errors skip the retries.

- `GET /api/admin/kafka-dlt?service={crm-service|notification-service|flight-seat-inventory-service}&topic=&limit=`
  (ADMIN): bounded peek, payload truncated to 300 chars, e-mails masked.
- `POST /api/admin/kafka-dlt/replay?service=...` `{topic, partition, offset}` (ADMIN): republishes one record to its
  original topic; consumer idempotency (`processed_events`) makes a replay safe.

The gateway picks the service by the `service` query parameter. CLI fallback: `scripts/kafka-dlt.py`.

### 16.5 Hotel photos (hotel-service, contract section 5)

Partner (`HOTEL_MANAGE` on the hotel):

| Method | Path | Body / notes |
|---|---|---|
| POST | `/api/hotels/{id}/media` | multipart `file` + optional `caption`, `rightsSource` (`OWNED`, `LICENSED`, `PARTNER_ATTESTED`), `rightsNote`. JPEG/PNG/WebP by magic bytes, max 5 MB, min 800x600, max 20 per hotel; EXIF/XMP/IPTC stripped. 201 `MediaView`. Errors: 400 `EMPTY_FILE`, `IMAGE_TOO_SMALL`, `IMAGE_TOO_LARGE`, `CORRUPT_IMAGE`; 413 `FILE_TOO_LARGE`; 415 `UNSUPPORTED_FILE_TYPE`; 409 `MEDIA_LIMIT_REACHED`, `HOTEL_NOT_OWNED` |
| GET | `/api/hotels/{id}/media/mine` | all own photos with `reviewStatus` (PENDING/APPROVED/REJECTED + reason) |
| PUT | `/api/hotels/{id}/media/order` | `{ids:[...]}` new sort order |
| PATCH | `/api/hotels/{id}/media/{mediaId}` | `{caption}`; editing an approved photo sends it back to review |
| DELETE | `/api/hotels/{id}/media/{mediaId}` | 204 (soft delete: the review history stays, the file is removed, the photo disappears everywhere); 409 `MEDIA_LIVE` for an approved photo of a published hotel |
| GET | `/api/hotels/{id}/media/{mediaId}/file` | own file, `Cache-Control: no-store` |

Public (no token): `GET /api/hotels/{id}/media` -> `[{id, caption, sortOrder, width, height}]` and
`GET /api/hotels/{id}/media/{mediaId}` -> image bytes, both only for scan CLEAN + review APPROVED photos of a public hotel
(else 404), `Cache-Control: public, max-age=300`, `X-Content-Type-Options: nosniff`.

Admin (`ADMIN`): `GET /api/admin/hotel-media?status=&page=&size=`, `GET /api/admin/hotel-media/{mediaId}/file`,
`POST /api/admin/hotel-media/{mediaId}/decisions` `{decision: APPROVE | REJECT, reason}` (reason required for REJECT;
409 `ALREADY_DECIDED`). Every decision is an append-only row in `hotel_media_decisions`.

### 16.6 Order expiry and payment reconciliation (RM-27)

- Orders get a terminal status **`EXPIRED`**: a PENDING order whose holds lapsed (`holdExpiresAt` + 2 min grace) is closed
  by a job every 60s, its holds and pricing reservation are released and an `ORDER_EXPIRED` event is published.
  Paying it afterwards fails: payment confirm answers 409 `ORDER_EXPIRED` and the provider payment is cancelled.
  Cancel and cancellation preview treat EXPIRED as terminal.
- `GET /api/admin/payment-reconciliation?status=&page=&size=` (ADMIN, payment-service): decisions of the reconciliation job
  `{items:[{id, paymentIntentId, orderId, providerPaymentId, intentStatus, orderStatus, status, detail, createdAt}], page, size, total}`.
  `status` is `ESCROW_REPAIRED` (order CONFIRMED but escrow/ledger missing: rebuilt exactly as confirm would),
  `PROVIDER_CANCELLED` (order not paid: provider payment cancelled, intent CANCELLED) or `MANUAL_REVIEW`.

### 16.7 Notification delivery retries (RM-30)

FAILED e-mail/push notifications are retried automatically after 1m, 5m, 30m, 2h and 12h; after 5 attempts the status
becomes **`GAVE_UP`**. Responses carry `nextAttemptAt` and `lastError` (exception class only).
`GET /api/notifications/status/{status}` (ADMIN) lists by status. `POST /api/notifications/{id}/retry` (ADMIN) retries
now, also from GAVE_UP; it is rejected for SENT so a delivered message is never resent.

---

## 15. Service-to-service (internal) endpoints

`/internal/**` endpoints are **not** routed by the api-gateway (no gateway predicate matches them) and are never called by browsers or apps.
Services call each other directly (Eureka/Feign).

### X-Service-Token

Each service signs a short-lived JWT with its own Ed25519 key and sends it in the `X-Service-Token` header.
Claims: `iss` = calling service, `aud` = target service, `exp` <= 60 seconds, plus a `jti`. The receiving service verifies the signature with the
caller's public key, checks `aud`, and checks that `iss` is in the allowed caller list of the matching `/internal/**` route group
(`waylo.service-auth.routes` in that service's `application.yml`, shared code in the `service-auth-common` module).
Tokens are single use: an accepted `(iss, jti)` is remembered until `exp` + skew, and a reused token gets `401` ("replayed token").
The memory is per service instance (bounded at 100k entries, full cache fails closed).
Keys are generated by `python3 scripts/gen-service-keys.py` into `.env` (never printed). Verify with `python3 scripts/test-service-auth.py`.

Only signed `X-Service-Token` is accepted. The legacy shared `X-Internal-Token` / `INTERNAL_API_TOKEN` and the `WAYLO_SERVICE_AUTH_MODE` switch are retired; a service without keys cannot call or be called on `/internal`. A route marked `required: false` in its config (for example `/api/customers` and `/api/bookings`) accepts calls without a service token.

### Route groups and allowed callers

Read from `waylo.service-auth.routes` as currently on disk (uncommitted working tree).

| Callee service | Route prefix | Allowed callers |
|----------------|--------------|-----------------|
| auth-service | `/internal/access` | hotel-service, bus-service, car-rental-service, package-booking-service, payment-service |
| auth-service | `/internal/organizations` | hotel-service, bus-service, car-rental-service, notification-service |
| hotel-service | `/internal/hotels` | auth-service |
| hotel-service | `/internal/hotel-holds` | package-booking-service |
| car-rental-service | `/internal/car-holds` | package-booking-service |
| bus-service | `/internal/bus-holds` | package-booking-service |
| flight-seat-inventory-service | `/internal/flight-holds` | package-booking-service |
| pricing-service | `/internal/pricing` | package-booking-service |
| payment-service | `/internal/escrow-items` | package-booking-service |
| payment-service | `/internal/supplier-payouts` | flight-seat-inventory-service (no in-code caller yet) |
| payment-service | `/internal/supplier-credits` | flight-seat-inventory-service (no in-code caller yet) |
| package-booking-service | `/internal/orders/items` | hotel-service, checkin-service |
| package-booking-service | `/internal/orders` | payment-service, notification-service |
| package-booking-service | `/api/bookings` (`required: false`) | package-booking-saga-orchestrator |
| crm-service | `/api/customers` (`required: false`) | notification-service |

Note: `/internal/orders/items` is more specific than `/internal/orders`, so item endpoints are limited to hotel-service and checkin-service.
A caller listed for a prefix may call every endpoint under it. Routes are matched in order, first match wins. An `/internal/**` path matching no configured route is fail-closed (no caller allowed).

### Endpoint inventory

| Method and path | Callee | Allowed callers |
|-----------------|--------|-----------------|
| `GET /internal/access` (query `userId`, `organizationId`, `permission`, optional hotel scope) | auth-service | hotel, bus, car-rental, package-booking, payment |
| `GET /internal/organizations/active-ids` | auth-service | hotel, bus, car-rental |
| `GET /internal/organizations/{id}` | auth-service | hotel, bus, car-rental |
| `GET /internal/organizations/{id}/contacts` | auth-service | notification (e-mails of ACTIVE ORGANIZATION_OWNER / HOTEL_OWNER members, `{"emails":[...]}`) |
| `GET /internal/hotels/{id}` | hotel-service | auth-service |
| `POST /internal/hotel-holds` | hotel-service | package-booking-service |
| `GET /internal/hotel-holds/{holdId}` | hotel-service | package-booking-service |
| `POST /internal/hotel-holds/{holdId}/confirm` | hotel-service | package-booking-service |
| `POST /internal/hotel-holds/{holdId}/release` | hotel-service | package-booking-service |
| `POST /internal/car-holds` | car-rental-service | package-booking-service |
| `POST /internal/car-holds/{id}/confirm` | car-rental-service | package-booking-service |
| `POST /internal/car-holds/{id}/release` | car-rental-service | package-booking-service |
| `POST /internal/bus-holds` | bus-service | package-booking-service |
| `POST /internal/bus-holds/{id}/confirm` | bus-service | package-booking-service |
| `POST /internal/bus-holds/{id}/release` | bus-service | package-booking-service |
| `POST /internal/flight-holds` | flight-seat-inventory-service | package-booking-service |
| `GET /internal/flight-holds/{id}` | flight-seat-inventory-service | package-booking-service |
| `POST /internal/flight-holds/{id}/confirm` | flight-seat-inventory-service | package-booking-service |
| `POST /internal/flight-holds/{id}/release` | flight-seat-inventory-service | package-booking-service |
| `GET /internal/flight-holds/{id}/refund-quote` | flight-seat-inventory-service | package-booking-service |
| `POST /internal/pricing/quote` | pricing-service | package-booking-service |
| `POST /internal/pricing/reservations/{orderId}/release` | pricing-service | package-booking-service |
| `POST /internal/escrow-items/{orderItemId}/release` | payment-service | package-booking-service |
| `POST /internal/escrow-items/{orderItemId}/cancellation-preview` | payment-service | package-booking-service |
| `POST /internal/escrow-items/{orderItemId}/cancel` | payment-service | package-booking-service |
| `POST /internal/escrow-items/{orderItemId}/no-show` | payment-service | package-booking-service |
| `POST /internal/escrow-items/{orderItemId}/refund` | payment-service | package-booking-service |
| `POST /internal/supplier-payouts` | payment-service | flight-seat-inventory-service |
| `POST /internal/supplier-credits` | payment-service | flight-seat-inventory-service |
| `GET /internal/orders/items/{orderItemId}` | package-booking-service | hotel-service, checkin-service |
| `GET /internal/orders/items/{orderItemId}/review-eligibility` | package-booking-service | hotel-service, checkin-service |
| `GET /internal/orders/items/review-eligible` | package-booking-service | hotel-service, checkin-service |
| `GET /internal/orders/flight-items` (query `flightNumber`, `departureDate`) | package-booking-service | payment-service, notification-service |
| `GET /internal/orders/{id}` | package-booking-service | payment-service, notification-service |
| `POST /internal/orders/{id}/paid` | package-booking-service | payment-service, notification-service |

Hold request bodies are small DTOs owned by the callee (for example bus `HoldRequest`: `tripId, fromSeq, toSeq, passengers (1..5), principalUserId,
idempotencyKey, orderCorrelationId`; supplier movements `{supplier, orderId, orderItemId, amount, currency, idempotencyKey}`). Their per-field contracts are
described in the service contract files (`BUS-CONTRACT.md`, `CAR-RENTAL-CONTRACT.md`, `FLIGHT-SUPPLIER-CONTRACT.md`, `PACKAGE-ORDER-CONTRACT.md`, `CANCELLATION-CONTRACT.md`)
and were not re-expanded here.

---


## Enums Reference

| Enum | Values |
|------|--------|
| **FlightStatus** | `SCHEDULED`, `CANCELLED` |
| **RoomType** | `SINGLE`, `DOUBLE`, `SUITE`, `DELUXE`, `FAMILY` |
| **RoomStatus** | `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE` |
| **CarCategory** | `ECONOMY`, `COMPACT`, `MIDSIZE`, `FULLSIZE`, `SUV`, `MINIVAN`, `VAN`, `LUXURY` |
| **FuelType** | `PETROL`, `DIESEL`, `HYBRID`, `ELECTRIC`, `LPG` |
| **Transmission** | `MANUAL`, `AUTOMATIC` |
| **RentalStatus** | `HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED` |
| **DocumentStatus** | `PENDING`, `APPROVED`, `REJECTED` |
| **BookingStatus** | `PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED`, `PARTIALLY_CONFIRMED` |
| **PaymentStatus** | `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED` |
| **PaymentMethod** | `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `MOCK` |
| **CustomerTier** | `BRONZE`, `SILVER`, `GOLD`, `PLATINUM` |
| **CheckinStatus** | `NOT_CHECKED_IN`, `CHECKED_IN`, `BOARDING_PASS_ISSUED`, `BOARDED`, `CANCELLED` |
| **NotificationType** | `EMAIL`, `SMS`, `PUSH` |
| **NotificationStatus** | `PENDING`, `SENT`, `FAILED`, `RETRYING` |
| **PricingType** | `FLIGHT`, `HOTEL`, `CAR_RENTAL`, `PACKAGE_DISCOUNT` |
| **SagaStatus** | `STARTED`, `FLIGHT_RESERVED`, `HOTEL_RESERVED`, `CAR_RESERVED`, `PAYMENT_COMPLETED`, `CONFIRMED`, `COMPENSATING`, `COMPENSATED`, `FAILED` |
| **SagaStepType** | `RESERVE_FLIGHT`, `RESERVE_HOTEL`, `RESERVE_CAR`, `PROCESS_PAYMENT`, `CONFIRM_BOOKING`, `COMPENSATE_FLIGHT`, `COMPENSATE_HOTEL`, `COMPENSATE_CAR`, `COMPENSATE_PAYMENT` |
| **SagaStepStatus** | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `COMPENSATED`, `SKIPPED` |
| **ScenarioType** | `FLIGHT_SEARCH`, `HOTEL_SEARCH`, `CAR_SEARCH`, `PACKAGE_BOOKING`, `FULL_FLOW`, `MIXED` |
| **SimulationStatus** | `IDLE`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |

---

## Kafka Events

| Topic | Event | Producer | Consumers |
|-------|-------|----------|-----------|
| `flight-events` | `FlightCreatedEvent` | flight-service | flight-seat-inventory-service |
| `flight-events` | `FlightCancelledEvent` | flight-service | package-booking-service, notification-service |
| `booking-events` | `BookingCreatedEvent` | package-booking-service | saga-orchestrator, notification-service, crm-service |
| `booking-events` | `BookingCancelledEvent` | package-booking-service | notification-service, crm-service |
| `payment-events` | `PaymentCompletedEvent` | payment-service | saga-orchestrator, notification-service |
| `payment-events` | `PaymentRefundedEvent` | payment-service | saga-orchestrator, notification-service |
| `support-events` | `SupportCaseUpdatedEvent` (`supportCaseUpdated`, kind ANSWERED/CLOSED) | crm-service (outbox) | notification-service (customer e-mail) |

---

## Architecture

### Service Map

| Service | Port | Database | Dependencies |
|---------|------|----------|-------------|
| api-gateway | 8080 | - | Eureka |
| config-service | 8888 | - | - |
| discovery-service | 8761 | - | Config |
| flight-service | 8081 | flight_db | Postgres, Kafka, Redis, Eureka |
| flight-seat-inventory-service | 8082 | flight_seat_inventory_db | Postgres, Kafka, Redis, Eureka |
| hotel-service | 8083 | hotel_db | Postgres, Kafka, Redis, Eureka |
| car-rental-service | 8084 | car_rental_db | Postgres, Kafka, Redis, Eureka |
| package-booking-service | 8085 | package_booking_db | Postgres, Kafka, Redis, Eureka, Feign |
| pricing-service | 8086 | pricing_db | Postgres, Kafka, Redis, Eureka |
| payment-service | 8087 | payment_db | Postgres, Kafka, Redis, Eureka |
| crm-service | 8088 | crm_db | Postgres, Kafka, Redis, Eureka |
| checkin-service | 8089 | checkin_db | Postgres, Kafka, Redis, Eureka, Feign |
| notification-service | 8090 | notification_db | Postgres, Kafka, Eureka |
| saga-orchestrator | 8091 | saga_db | Postgres, Kafka, Redis, Eureka, Feign |
| traffic-simulator | 8095 | - (in-memory) | - |

### Inter-Service Communication (OpenFeign)

```
checkin-service        --> flight-service
package-booking-service --> flight-service, hotel-service, car-rental-service
saga-orchestrator      --> flight-seat-inventory-service, payment-service, package-booking-service
```

### Running with Docker

```bash
# Build all services
./gradlew build -x test

# Start infrastructure + all services
docker compose up -d

# Check service health
curl http://localhost:8080/actuator/health

# View Eureka dashboard
open http://localhost:8761

# View pgAdmin
open http://localhost:5050  # admin@waylo.com / admin
```

## Other changes (2026-10-24)

- **Quotes (`POST /api/quotes`), rentals (`POST /api/rentals/**`) and seat reserve (`POST /api/seat-inventory/seats/{flightNumber}/reserve`)**:
  platform ADMIN gets 403 `ADMIN_CANNOT_PURCHASE`.
- **Account deletion**: `DELETE /api/me` with body `{"password": "..."}` -> 204. The user is soft-deleted (`deletedAt`),
  email anonymized to `deleted+{id}@deleted.invalid`, memberships revoked.
- **Admin organizations**: `GET /api/admin/organizations?status=&page=&size=` (ADMIN only, 403 otherwise).
  `status` omitted -> `PENDING` (legacy default); `status=ALL` -> every status; or one of PENDING|ACTIVE|SUSPENDED|ARCHIVED.
  `page` default 0, `size` default 100, max 100. Response: `{success, message, data:[{id,name,status}], total}`.
- **Admin users**: `GET /api/admin/users?page=&size=&q=` (ADMIN only). `q` is a case-insensitive prefix match on email,
  first name, last name or full name; `size` default 20, max 100. Response: `{success, message, data:[{id,email,fullName,role,
  verified,deletedAt}], total}`; deleted users have non-null `deletedAt` and an anonymized email.
  `GET /api/admin/users?email=x` still returns the single-user `{success,message,data:{...same fields}}` (404 if missing).
- **Rate limits (gateway, per client IP, per minute)**: login 10, register 5, forgot-password 3, resend-verification 3,
  refresh 30. Exceeding returns 429. Fails open if Redis is down.

### 16.8 Support cases (crm-service, RM-36)

A support case is opened on the customer's own order. There is no live chat. Cases are asynchronous message threads.
Status: `OPEN` (customer wrote last), `ANSWERED` (partner or admin wrote last), `CLOSED` (terminal, no more messages). Messages are append-only.

- Customer (JWT): `POST /api/me/support-cases` `{orderId, orderItemId?, subject (max 150), message (max 4000)}` -> 201 (404 if the order is not yours),
  `GET /api/me/support-cases`, `GET /api/me/support-cases/{id}` (with `messages`), `POST /api/me/support-cases/{id}/messages` `{body}`,
  `POST /api/me/support-cases/{id}/close`. Another customer's case is 404.
- Partner (`RESERVATION_READ` on the organization, else 403): `GET /api/partner/organizations/{orgId}/support-cases`, `GET .../{id}`,
  `POST .../{id}/messages` `{body}` (sets `ANSWERED`). Only cases whose item supplier is that organization are visible (others 404).
- Admin (ADMIN only, else 403): `GET /api/admin/support-cases?status=`, `GET .../{id}`, `POST .../{id}/messages`, `POST .../{id}/close`.
- Posting to a CLOSED case: 422 `CASE_CLOSED`.
- Public: `GET /api/support/capabilities` -> `{liveChat:false, channels:[], hours:null, decisionPending:true}` until the RM-06 operations playbook is decided.
- UI: web customer `/support` (plus "Destek talebi ac" on each order), partner `/partner/support`, admin `/admin/support`;
  mobile `support/index`, `support/[id]`, `support/new?orderId=` (entry points on the order detail and the profile tab).

### 16.9 Photos in publication requests (hotel-service, media phase 3, V13)

A publication request (INITIAL or CONTENT_CHANGE, section 16.1) can carry the hotel's PENDING photos, so one admin
decision covers the content and the photo set (HOTEL-PUBLICATION-CONTRACT.md section 5). Table
`hotel_publication_request_media(request_id, media_id, hotel_id, organization_id, created_at)`, RLS like
`hotel_publication_requests`, rows immutable (no UPDATE policy, delete only in service mode).

- `POST /api/hotels/{hotelId}/publication-requests` body gains optional `mediaIds: uuid[]` (null or empty: no photos).
  Errors: 400 `VALIDATION_ERROR` (more than 20, duplicates, null entries), 404 `MEDIA_NOT_FOUND` (`details.mediaId`;
  not a non-deleted photo of this hotel), 409 `MEDIA_NOT_PENDING` (`details.mediaId`, `details.reviewStatus`).
- Responses: the request view gains `mediaIds` (linked non-deleted photos by sort order); the partner history item and the
  admin list item gain `mediaCount`; the admin detail gains top-level `media` (MediaView list as in `.../media/mine`).
- Admin `APPROVE` / `APPROVE_AND_PUBLISH` approves every linked photo that is still PENDING (review APPROVED, scan CLEAN,
  one `hotel_media_decisions` row each). `REJECT` and withdraw leave photos PENDING so they can join the next request.
  To drop one bad photo the admin rejects it first with `POST /api/admin/hotel-media/{mediaId}/decisions`; photos decided
  that way are skipped by the cascade. Photos not attached to any request keep using the per-photo queue (16.5).
- UI: the partner submit panel lists PENDING photos as a checklist (all checked); the admin request detail shows the
  photos with thumbnails and a per-photo reject.
