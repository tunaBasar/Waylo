#!/usr/bin/env python3
"""Acceptance test for hotel-service public projection.

Verifies GET /api/hotels and /api/hotels/search exclude:
  - inactive hotels
  - hotels whose organization is PENDING or SUSPENDED
while GET /api/hotels/{id} (direct lookup, used internally by auth-service for
role-grant verification) and GET /api/hotels?organizationId= (partner management)
remain unfiltered by organization status.

Registers a real user, creates an organization (stays PENDING by default),
creates a hotel under it, and asserts the hotel is absent from public listing
while PENDING, then (if an admin exists) approving flow is out of scope here
since admin bootstrap requires direct DB access.
"""
import argparse, json, secrets, sys, uuid
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = "http://localhost:8080"
T = 15
P = F = 0


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print(f"PASS: {label}")
    else:
        F += 1
        raise AssertionError(f"FAIL: {label}")


def req(method, path, body=None, token=None):
    url = BASE_URL + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    r = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(r, timeout=T) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw, parse_float=Decimal) if raw else None)
    except HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw, parse_float=Decimal) if raw else None)


def reg():
    uid = uuid.uuid4().hex
    email = f"pubproj{uid}@example.test"
    password = secrets.token_urlsafe(16)
    s, d = req("POST", "/api/auth/register", {
        "firstName": "Test", "lastName": "User", "email": email, "password": password
    })
    ok(s == 200 and d.get("success"), f"Register: 200+success got {s}")
    return d["data"]["userId"], d["data"]["token"]


def main():
    global BASE_URL
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    BASE_URL = args.base_url.rstrip("/") or BASE_URL

    if args.dry_run:
        ok(req("GET", "/api/hotels")[0] == 200, "dry-run public hotels list reachable")
        print(f"DRY-RUN: PASS: {P}  FAIL: {F}")
        return 0

    user_id, token = reg()

    s, d = req("POST", "/api/organizations", {"name": f"PubProj Org {uuid.uuid4().hex[:8]}"}, token=token)
    ok(s == 201, f"Create org 201 got {s}")
    org_id = d["data"]["id"]
    ok(d["data"]["status"] == "PENDING", "New org starts PENDING")

    s, d = req("POST", "/api/hotels", {
        "name": f"PubProj Hotel {uuid.uuid4().hex[:8]}", "city": "IST", "country": "TR",
        "address": "St1", "starRating": 4, "organizationId": org_id
    }, token=token)
    ok(s == 201, f"Create hotel under PENDING org 201 got {s}")
    hotel_id = d["data"]["id"]

    # Direct GET by id: unfiltered, visible even while org is PENDING (internal lookup contract)
    s, d = req("GET", f"/api/hotels/{hotel_id}")
    ok(s == 200 and d["data"]["id"] == hotel_id, "GET by id unfiltered while org PENDING")

    # Partner management view scoped by organizationId: unfiltered, visible while PENDING
    s, d = req("GET", f"/api/hotels?organizationId={org_id}")
    ok(s == 200 and any(h["id"] == hotel_id for h in d["data"]), "organizationId-scoped list unfiltered while PENDING")

    # Public unscoped list: hotel of a PENDING org must NOT appear
    s, d = req("GET", "/api/hotels")
    ok(s == 200, "Public GET /api/hotels 200")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public list excludes hotel of PENDING organization")

    # Public search: same exclusion
    s, d = req("GET", "/api/hotels/search?city=IST")
    ok(s == 200, "Public search 200")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public search excludes hotel of PENDING organization")

    # Deactivate a legacy-style check is out of scope (no legacy hotel creation API); verify
    # that active=false also hides a hotel from public listing using activate/deactivate on
    # the one we made (owner-authorized).
    s, d = req("POST", f"/api/hotels/{hotel_id}/deactivate", token=token)
    ok(s == 200 and d["data"]["active"] is False, "Deactivate hotel 200")

    s, d = req("GET", "/api/hotels")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public list excludes deactivated hotel")

    s, d = req("GET", f"/api/hotels?organizationId={org_id}")
    ok(any(h["id"] == hotel_id for h in d["data"]), "organizationId-scoped list still shows deactivated hotel")

    print(f"PASS: {P}  FAIL: {F}")
    return 0 if F == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
