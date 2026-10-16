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
12. [Saga Orchestrator](#11-saga-orchestrator)
13. [Traffic Simulator](#12-traffic-simulator)
14. [Enums Reference](#enums-reference)
15. [Kafka Events](#kafka-events)
16. [Architecture](#architecture)

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

---

## 4. Car Rental Service

**Port:** 8084 | **DB:** car_rental_db

#### `POST /api/vehicles` - Create Vehicle

```json
// Request
{
  "plateNumber": "34 ABC 123",  // required, unique
  "brand": "Toyota",            // required
  "model": "Corolla",           // required
  "year": 2024,                 // required, min 1990
  "vehicleType": "COMPACT",     // required: ECONOMY, COMPACT, MIDSIZE, FULLSIZE, SUV, LUXURY, VAN
  "color": "White",             // optional
  "seatCount": 5,               // required, min 1
  "dailyRate": 350.00,          // required, min 0
  "location": "IST Airport",    // required
  "mileage": 15000              // optional
}

// Response: 201 Created - status defaults to AVAILABLE
```

#### `GET /api/vehicles/{id}` - Get Vehicle
#### `GET /api/vehicles/plate/{plateNumber}` - Get by Plate Number
#### `GET /api/vehicles` - List All

#### `GET /api/vehicles/search` - Search Vehicles

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `location` | String | No | Location filter |
| `vehicleType` | VehicleType | No | Vehicle type |
| `status` | VehicleStatus | No | Status filter |
| `maxDailyRate` | BigDecimal | No | Max daily rate |

#### `PUT /api/vehicles/{id}` - Update Vehicle
#### `DELETE /api/vehicles/{id}` - Delete Vehicle (204)
#### `POST /api/vehicles/{id}/status?status=RENTED` - Update Status
#### `POST /api/vehicles/{id}/deactivate` - Deactivate
#### `POST /api/vehicles/{id}/activate` - Activate
#### `GET /api/vehicles/available-count?location=IST Airport` - Available Count by Location

---

## 5. Package Booking Service

**Port:** 8085 | **DB:** package_booking_db

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

### Order / OrderItem (additive, same service)

Self-service customer orders. `customerId` in the request body must equal the caller's
`X-User-Id` (forwarded by api-gateway from a verified JWT); mismatches return 403. v1 accepts
exactly one `OrderItem` per order (array of size 1); the model is ready for multiple items.
Legacy `/api/bookings` is unaffected and untouched by this addition.

#### `POST /api/orders` - Create Order

```json
// Request
{
  "customerId": "customer-uuid",         // required, must equal caller
  "currency": "USD",                     // required, 3-letter ISO 4217
  "quoteId": null,                       // optional, pricing-service not implemented yet
  "notes": "Honeymoon package",          // optional, max 2000 chars
  "items": [
    {
      "vertical": "HOTEL",                       // required: HOTEL, FLIGHT, CAR
      "supplierOrganizationId": "org-uuid",      // optional
      "productRef": "room-type-or-rate-uuid",    // required
      "description": "Deluxe room, 2 nights",    // required, max 500 chars
      "unitAmount": 100.00,                      // required, >= 0.01
      "quantity": 2,                             // required, 1-20
      "currency": "USD"                          // required, must equal order currency or 422
    }
  ]
}

// Response: 201 Created - status PENDING, totalAmount = sum(unitAmount * quantity)
{
  "success": true,
  "data": {
    "id": "uuid",
    "customerId": "customer-uuid",
    "status": "PENDING",
    "currency": "USD",
    "quoteId": null,
    "totalAmount": 200.00,
    "notes": "Honeymoon package",
    "version": 0,
    "items": [ { "id": "uuid", "orderId": "uuid", "vertical": "HOTEL", "productRef": "...",
                 "unitAmount": 100.00, "quantity": 2, "subtotal": 200.00, "currency": "USD" } ],
    "createdAt": "...", "updatedAt": "..."
  }
}
```

Errors: 401 no identity, 403 `customerId` does not match caller or order not owned by caller,
422 `CURRENCY_MISMATCH` item currency differs from order currency, 400 validation (more than
one item, non-positive amount, lowercase/invalid currency), 404 order not found.

#### `GET /api/orders/{id}` - Get own order (403 if not owner)
#### `GET /api/orders` - List caller's own orders
#### `POST /api/orders/{id}/cancel?expectedVersion=0` - Cancel own order

`expectedVersion` is optional; when provided and stale, returns 409 `CONCURRENT_MODIFICATION`.
Cancelling an already-`CANCELLED` or `FAILED` order returns 422 (`ALREADY_CANCELLED` /
`TERMINAL_STATE`). The response reflects the persisted version (flushed before responding).

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

---

## 11. Saga Orchestrator

**Port:** 8091 | **DB:** saga_db

> Manages distributed transactions for package bookings using the Saga pattern.

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

## Enums Reference

| Enum | Values |
|------|--------|
| **FlightStatus** | `SCHEDULED`, `CANCELLED` |
| **RoomType** | `SINGLE`, `DOUBLE`, `SUITE`, `DELUXE`, `FAMILY` |
| **RoomStatus** | `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE` |
| **VehicleType** | `ECONOMY`, `COMPACT`, `MIDSIZE`, `FULLSIZE`, `SUV`, `LUXURY`, `VAN` |
| **VehicleStatus** | `AVAILABLE`, `RESERVED`, `RENTED`, `MAINTENANCE` |
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
