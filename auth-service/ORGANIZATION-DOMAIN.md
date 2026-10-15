# Auth organization domain

This slice adds organization membership and scoped role storage. It does not add organization endpoints or change the existing UserRole, login, JWT, or user migration.

## Entities and ownership

- `Organization`: UUID identity, name, lifecycle status (PENDING by default), optimistic version and timestamps.
- `OrganizationMembership`: immutable user/organization references, membership status, permission version, optimistic version and timestamps. A user can belong to multiple organizations, but only once per organization.
- `RoleAssignment`: immutable membership, organization UUID, role, scope type and scope UUID. Global USER/ADMIN roles remain in `UserRole`. Organization roles live here.
- `ScopeType`: ORGANIZATION or HOTEL. There is no central Scope entity. Organization scope references the organization UUID. Hotel scope references a hotel-service UUID.

Persist the organization before constructing assignments, because assignment validation requires its UUID. Construct a membership with `new OrganizationMembership(user, organization)`, then construct a grant with `new RoleAssignment(membership, role, scopeType, scopeId)`. Persist the referenced entities explicitly, since these associations do not cascade. Construct grants inside a transaction with a managed membership, since accessing an uninitialized detached membership requires an active persistence context.

The intentionally duplicated assignment `organization_id` allows a composite foreign key to `(membership.id, membership.organization_id)`. PostgreSQL prevents mismatched organizations and prevents moving a membership with an existing grant to another organization, including raw SQL updates. Organization scopes must match this organization. ORGANIZATION_OWNER is restricted to organization scope. Deletion is restricted rather than cascading away grants.

## Required follow-up outside this slice

A transactional grant/revoke service must authorize the actor, check membership and organization status, verify a HOTEL scope exists and belongs to the same organization through hotel-service, and increment the membership permission version whenever permissions change. Creating a RoleAssignment does not automatically bump that version. Membership status changes do bump it. These storage checks alone do not implement request authorization or token invalidation.

Hotel scope has no database foreign key across service databases. An arbitrary hotel UUID is accepted at the storage layer, intentionally requiring service-level validation before any public grant endpoint is exposed.

## Verification

Run from the repository root:

```bash
bash auth-service/scripts/test-entities-postgres.sh
```

Requires local Docker and Java. The script creates a disposable PostgreSQL 16 container bound to loopback, runs unit tests, explicit integration tests and bootJar, then removes its own container. It does not start the application, Kafka, SMTP or the Compose stack. Test setup refuses a database name without `_test` suffix or a non-empty public schema.

Observed on October 14, 2026: 8 unit tests and 20 PostgreSQL integration test executions passed, with zero skipped, failed or errored tests. All seven Gradle tasks executed, including schema validation and bootJar. The temporary container was removed. Reports are under `build/test-results/test` and `build/test-results/integrationTest`.

| Requirement / changed output | Actual check and observation |
| --- | --- |
| Additive V2 upgrade preserves existing users | Migrate V1, insert an existing user, migrate V2 and read unchanged password hash, global USER role and verified flag. Second migrate applies zero migrations and Flyway validation passes. |
| JPA mappings, UUIDs, timestamps and lazy relations | Hibernate validates the migrated PostgreSQL schema. Entities persist and load, generated identifiers/timestamps are present, relations remain lazy until accessed. An assignment constructed through an uninitialized managed membership proxy persists and reads back correctly. |
| Multi-organization membership | Same persisted user receives memberships and different scoped grants in two organizations. Duplicate membership returns SQLSTATE 23505. |
| Unique grants and required scope | Duplicate membership/role/type/scope returns 23505. Null scope returns 23502. Missing membership returns 23503. |
| Tenant consistency on insert and membership update | Forged assignment organization and reparenting a membership with a grant both return 23503 through raw SQL. |
| Organization scope and owner restriction | Wrong organization scope and ORGANIZATION_OWNER with HOTEL scope return 23514. Java constructor checks also reject these. |
| Valid lifecycle, role and scope values | Invalid organization/membership status, role and scope type, negative permission version and blank organization name return 23514. Constructor name/null checks pass. |
| Safe deletion | Deleting a referenced user, organization or membership returns 23503 rather than cascading away grants. |
| Permission revision and concurrent changes | Status change persists permissionVersion=1 and version=1. A competing stale update raises OptimisticLockException. |
| Packaging | `:auth-service:bootJar` succeeds alongside the full isolated test run. This is not a complete application startup or endpoint authorization test. |

Existing auth logic and V1 are unchanged. The user's separate Hotel.java change is outside this slice and is not staged with it.

## Endpoints (2026-10-15)

**POST /api/organizations** — Create a new organization.

- Caller must have a valid identity (JWT verified by api-gateway, forwarded as X-User-Id).
- Creates an Organization with status `PENDING` and creates the caller's membership as `ACTIVE` with role `ORGANIZATION_OWNER` scoped to the new organization.
- Returns the created organization and the new membership/role assignment.
- Returns `409` if the organization name already exists.

**GET /api/me/memberships** — List the current user's organization memberships.

- Identity from gateway X-User-Id (JWT verified at api-gateway).
- Returns all memberships (active and inactive) for the authenticated user across all organizations.
- Each item includes: organization details, membership status, and the set of role assignments (role, scopeType, scopeId).
- Returns `401` if no valid identity is present.

**GET /internal/access** — Permission check for service-to-service authorization.

- Called by downstream services (hotel-service) to verify whether a user has permission to perform an action on a specific resource.
- No service-to-service authentication; protected by not being routed through the gateway and 127.0.0.1 port binding.
- Exact permission rules:
  - `ORGANIZATION_OWNER` → `HOTEL_CREATE`, `HOTEL_MANAGE`, `ROOM_MANAGE` for resources in its organization.
  - `HOTEL_OWNER` / `HOTEL_MANAGER` with `HOTEL` scope → `HOTEL_MANAGE` / `ROOM_MANAGE` for that specific hotel only.
  - `HOTEL_STAFF` → no permissions granted.
  - Inactive membership or `SUSPENDED`/`ARCHIVED` organization → deny.
  - Any error (missing user, organization, membership, role, etc.) → deny.
- Returns `ALLOWED` or `DENIED` with details about the denial reason.

## Delegation and administration (2026-10-15)

### Role delegation endpoints

**POST /api/organizations/{orgId}/role-grants** — Assign or reassign a scoped role to a user within an organization.

- Requires `X-User-Id` (JWT verified by api-gateway) and an authorization check.
- **Subset rule**: If the caller is `ORGANIZATION_OWNER`, they may grant `HOTEL_OWNER`, `HOTEL_MANAGER`, or `HOTEL_STAFF`. If the caller is `HOTEL_OWNER`, they may grant `HOTEL_MANAGER` or `HOTEL_STAFF` scoped only to hotels they own.
- Hotel scope is verified against hotel-service: the hotel must exist, belong to the same organization, and be owned by the caller if they are a hotel owner.
- Returns `400` for invalid role/scope combinations (e.g. `ORGANIZATION_OWNER` grant, or `HOTEL_STAFF` with `HOTEL` scope).
- Returns `403` if the caller lacks authority or the hotel does not belong to the caller's organization.
- Returns `409` on duplicate role assignment.
- Returns `200` on success.

**DELETE /api/organizations/{orgId}/role-grants** — Revoke a scoped role assignment.

- Same authorization rules as POST. The revokee loses the granted permissions immediately.
- Returns `204` on success.

### Platform admin endpoints

**GET /internal/platform/organizations**, **POST /internal/platform/organizations/{orgId}/approve**, **POST /internal/platform/organizations/{orgId}/suspend**

- Platform admin access is enforced by the api-gateway via the `X-User-Role` header (JWT verified by the gateway, not the auth-service).
- `approve` transitions an organization from `PENDING` to `ACTIVE`. Returns `409` if already approved. Requires a reason in the request body; returns `400` without one.
- `suspend` transitions an organization to `SUSPENDED`. Returns `200` on success.
- A user may own at most **5 organizations**. Attempting a 6th returns `409`.

### Service-to-service authentication

All `/internal/**` endpoints on auth-service and hotel-service now require the `X-Internal-Token` header. The api-gateway strips this header before forwarding to downstream services, so internal endpoints enforce it directly. Without the header or with an invalid token, requests receive `401`.

This prevents unauthenticated internal calls from any local process, complementing the `127.0.0.1` port binding.
