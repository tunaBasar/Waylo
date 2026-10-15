# Waylo Project Overview

## Purpose
Waylo is a travel marketplace being built hotel-first: hotels (businesses) join directly, manage their own properties via a B2B partner web panel, and end customers search, book, pay and review published hotels (consumer mobile app comes after the hotel web + backend). Flights and car rental exist as v0 skeletons and are future verticals; the shared core (identity, organizations/memberships, orders, payments, notifications, audit) must stay vertical-agnostic.

## Target users
- Hotel businesses: ORGANIZATION_OWNER, HOTEL_OWNER, HOTEL_MANAGER, HOTEL_STAFF (scoped per organization/hotel)
- Platform staff: PLATFORM_ADMIN, PLATFORM_SUPPORT
- End customers (CUSTOMER); one identity can be a customer and a partner member at the same time

## Core flows
1. Partner: organization application -> admin approval -> hotel draft -> content -> room types/rate plans -> daily inventory/prices -> publication request -> admin publish
2. Customer: search published hotels -> availability -> quote -> atomic multi-night hold -> order -> payment -> confirmation -> stay (check-in/check-out) -> verified review
3. Cancellation/refund with policy snapshot and inventory release

## Microservices (current state)

| Service | Port | Current state |
|---------|------|---------------|
| config-service | 8888 | Spring Cloud Config (native) |
| discovery-service | 8761 | Eureka |
| api-gateway | 8080 | Spring Cloud Gateway, CORS localhost:3000, no JWT validation yet |
| auth-service | 8092 | register/login/verify/reset, JWT (jjwt), BCrypt; Organization/Membership/RoleAssignment entities added (V2), no endpoints yet |
| hotel-service | 8083 | Hotel + Room CRUD (v0), no tenant ownership yet, no tests |
| flight-service | 8081 | v0 CRUD skeleton |
| flight-seat-inventory-service | 8082 | v0 CRUD skeleton |
| car-rental-service | 8084 | v0 CRUD skeleton |
| package-booking-service | 8085 | single flight/hotel/vehicle booking (to become Order/OrderItem) |
| pricing-service | 8086 | v0 CRUD |
| payment-service | 8087 | mock payments |
| crm-service | 8088 | v0 CRUD, loyalty |
| checkin-service | 8089 | v0 CRUD |
| notification-service | 8090 | real SMTP email for auth events via Kafka |
| package-booking-saga-orchestrator | 8091 | saga skeleton |
| traffic-simulator | 8095 | load generator |
| frontend (Next.js) | 3000 | operations UI, real auth integration |

## Stack
Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3, Gradle multi-module, PostgreSQL 16 (DB per service), Flyway, Kafka (KRaft), Redis, MapStruct, Docker Compose.

## Key references
ARCHITECTURE.md (current runtime), PRODUCT-ARCHITECTURE.md (target design), WAYLO-MVP-ROADMAP.md (phased roadmap), auth-service/ORGANIZATION-DOMAIN.md.
