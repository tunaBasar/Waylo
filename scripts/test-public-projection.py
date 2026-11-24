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
import argparse, json, os, pathlib, secrets, subprocess, sys, time, uuid
from datetime import date, timedelta
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
    subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", "auth_db", "-tAc",
                    f"UPDATE users SET email_verified = true WHERE email = '{email}'"], check=True, capture_output=True)
    time.sleep(13)  # gateway register limit 5/min
    s, d = req("POST", "/api/auth/login", {"email": email, "password": password})
    ok(s == 200 and d.get("success"), f"Login after verify: 200+success got {s}")
    return d["data"]["userId"], d["data"]["token"]


def env_file_value(key):
    try:
        for line in open(pathlib.Path(__file__).resolve().parent.parent / ".env").read().splitlines():
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    except OSError:
        pass
    return os.environ.get(key)


def publish_hotel(hotel_id, owner_token, admin_token):
    # Publication workflow: owner submits, admin approves and publishes (org ACTIVE, hotel complete)
    s, d = req("POST", f"/api/hotels/{hotel_id}/publication-requests", {}, owner_token)
    ok(s == 201, f"publish helper: submit 201 got {s}")
    s, d2 = req("POST", f"/api/admin/hotel-publications/{d['data']['id']}/decisions",
                {"decision": "APPROVE_AND_PUBLISH", "expectedVersion": d["data"].get("version", 0)}, admin_token)
    ok(s == 200, f"publish helper: approve-and-publish 200 got {s}")


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

    s, d = req("POST", "/api/organizations", {"name": f"PubProj Org {uuid.uuid4().hex[:8]}", "businessType": "HOTEL", "legalType": "COMPANY"}, token=token)
    ok(s == 201, f"Create org 201 got {s}")
    org_id = d["data"]["id"]
    ok(d["data"]["status"] == "PENDING", "New org starts PENDING")

    s, d = req("POST", "/api/hotels", {
        "name": f"PubProj Hotel {uuid.uuid4().hex[:8]}", "city": "IST", "country": "TR",
        "address": "St1", "starRating": 4, "organizationId": org_id
    }, token=token)
    ok(s == 201, f"Create hotel under PENDING org 201 got {s}")
    hotel_id = d["data"]["id"]
    ok(d["data"].get("publicationStatus") == "DRAFT", "New hotel starts DRAFT")

    # Direct GET by id: owner sees the unpublished hotel, anonymous callers do not (unpublished hidden since 4025805)
    s, d = req("GET", f"/api/hotels/{hotel_id}", token=token)
    ok(s == 200 and d["data"]["id"] == hotel_id, "GET by id visible to owner while org PENDING")
    s, _ = req("GET", f"/api/hotels/{hotel_id}")
    ok(s == 404, f"GET by id hidden from anonymous while org PENDING got {s}")

    # Partner management view scoped by organizationId: unfiltered, visible while PENDING
    s, d = req("GET", f"/api/hotels?organizationId={org_id}", token=token)
    ok(s == 200 and any(h["id"] == hotel_id for h in d["data"]), "organizationId-scoped list unfiltered while PENDING")

    # Public unscoped list: hotel of a PENDING org must NOT appear
    s, d = req("GET", "/api/hotels")
    ok(s == 200, "Public GET /api/hotels 200")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public list excludes hotel of PENDING organization")

    # Public search: same exclusion
    s, d = req("GET", "/api/hotels/search?city=IST")
    ok(s == 200, "Public search 200")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public search excludes hotel of PENDING organization")

    # --- catalog so the completeness gate can pass ---
    s, d = req("POST", f"/api/hotels/{hotel_id}/room-types", {"code": "PP_DBL", "name": "PP double", "maxAdults": 2,
                                                              "maxChildren": 0, "maxOccupancy": 2}, token)
    ok(s == 201, f"Create room type 201 got {s}")
    room_id = d["data"]["id"]
    s, d = req("POST", f"/api/hotels/{hotel_id}/rate-plans", {"code": "PP_NR", "name": "PP nr", "mealPlan": "ROOM_ONLY",
                                                              "cancellationPolicy": "NON_REFUNDABLE"}, token)
    ok(s == 201, f"Create rate plan 201 got {s}")
    plan_id = d["data"]["id"]
    start = date.today() + timedelta(days=20)
    nights = [(start + timedelta(days=i)).isoformat() for i in range(2)]
    check_out = (start + timedelta(days=2)).isoformat()
    s, _ = req("PUT", f"/api/hotels/{hotel_id}/inventory", {"items": [
        {"roomTypeId": room_id, "stayDate": n, "allotment": 2, "expectedVersion": None} for n in nights],
        "reason": "public projection fixture"}, token)
    ok(s in (200, 201), f"Set inventory got {s}")
    s, _ = req("PUT", f"/api/hotels/{hotel_id}/rates", {"items": [
        {"roomTypeId": room_id, "ratePlanId": plan_id, "stayDate": n, "occupancy": 2, "currency": "EUR",
         "amount": 100, "expectedVersion": None} for n in nights]}, token)
    ok(s in (200, 201), f"Set rates got {s}")
    quote_body = {"hotelId": hotel_id, "roomTypeId": room_id, "ratePlanId": plan_id, "checkIn": nights[0],
                  "checkOut": check_out, "rooms": 1, "adults": 2, "childrenAges": []}

    def hidden_publicly(label):
        s, d = req("GET", "/api/hotels")
        ok(s == 200 and not any(h["id"] == hotel_id for h in d["data"]), f"Public list hides hotel: {label}")
        s, d = req("GET", "/api/hotels/search?city=IST")
        ok(s == 200 and not any(h["id"] == hotel_id for h in d["data"]), f"Public search hides hotel: {label}")
        s, _ = req("GET", f"/api/hotels/{hotel_id}")
        ok(s == 404, f"Anonymous GET by id 404: {label} got {s}")

    def visible_publicly(label):
        s, d = req("GET", "/api/hotels")
        ok(s == 200 and any(h["id"] == hotel_id for h in d["data"]), f"Public list shows hotel: {label}")
        s, d = req("GET", "/api/hotels/search?city=IST")
        ok(s == 200 and any(h["id"] == hotel_id for h in d["data"]), f"Public search shows hotel: {label}")
        s, d = req("GET", f"/api/hotels/{hotel_id}")
        ok(s == 200 and d["data"]["id"] == hotel_id, f"Anonymous GET by id 200: {label}")

    # PENDING org: submit refused
    s, d = req("POST", f"/api/hotels/{hotel_id}/publication-requests", {}, token)
    ok(s == 409 and (d or {}).get("error") == "ORGANIZATION_NOT_ACTIVE", f"Submit under PENDING org 409 ORGANIZATION_NOT_ACTIVE got {s}")

    # Admin approves the organization: the hotel must STILL be hidden (org approval never publishes)
    admin_email = env_file_value("WAYLO_BOOTSTRAP_ADMIN_EMAIL")
    admin_pw = env_file_value("WAYLO_BOOTSTRAP_ADMIN_PASSWORD")
    ok(bool(admin_email and admin_pw), "Bootstrap admin credentials available")
    s, d = req("POST", "/api/auth/login", {"email": admin_email, "password": admin_pw})
    ok(s == 200 and d.get("success"), f"Admin login 200 got {s}")
    admin = d["data"]["token"]
    s, _ = req("POST", f"/api/admin/organizations/{org_id}/approve", token=admin)
    ok(s in (200, 201), f"Admin approves org got {s}")
    hidden_publicly("org ACTIVE but hotel DRAFT")
    s, d = req("GET", f"/api/hotels/{hotel_id}", token=token)
    ok(s == 200 and d["data"].get("publicationStatus") == "DRAFT", "Owner still sees DRAFT hotel after org approval")

    # R1: DRAFT hotel is not bookable
    s, d = req("POST", "/api/quotes", quote_body, token)
    ok(s == 409 and (d or {}).get("error") == "HOTEL_NOT_BOOKABLE", f"Quote on DRAFT hotel 409 HOTEL_NOT_BOOKABLE got {s}")

    # Submit: hidden while SUBMITTED, owner cannot decide, non-admin 403, validation, version conflict
    s, d = req("POST", f"/api/hotels/{hotel_id}/publication-requests", {}, token)
    ok(s == 201 and d["data"]["status"] == "SUBMITTED", f"Submit publication request 201 got {s}")
    request_id, version = d["data"]["id"], d["data"].get("version", 0)
    s, d = req("GET", f"/api/hotels/{hotel_id}", token=token)
    ok(s == 200 and d["data"].get("publicationStatus") == "SUBMITTED", "Hotel is SUBMITTED after submit")
    hidden_publicly("hotel SUBMITTED")
    s, d = req("POST", "/api/quotes", quote_body, token)
    ok(s == 409 and (d or {}).get("error") == "HOTEL_NOT_BOOKABLE", f"Quote on SUBMITTED hotel 409 HOTEL_NOT_BOOKABLE got {s}")
    decide = f"/api/admin/hotel-publications/{request_id}/decisions"
    s, _ = req("POST", decide, {"decision": "APPROVE_AND_PUBLISH", "expectedVersion": version}, token)
    ok(s == 403, f"Owner cannot self-approve (non-admin decision) 403 got {s}")
    s, _ = req("POST", decide, {"decision": "APPROVE", "expectedVersion": version}, token)
    ok(s == 403, f"Non-admin APPROVE 403 got {s}")
    s, _ = req("GET", "/api/admin/hotel-publications", token=token)
    ok(s == 403, f"Non-admin admin list 403 got {s}")
    s, d = req("POST", decide, {"decision": "REJECT", "expectedVersion": version}, admin)
    ok(s == 400, f"REJECT without reason 400 got {s}")
    s, d = req("POST", decide, {"decision": "APPROVE_AND_PUBLISH", "expectedVersion": version + 99}, admin)
    ok(s == 409 and (d or {}).get("error") == "VERSION_CONFLICT", f"Stale expectedVersion 409 VERSION_CONFLICT got {s}")
    hidden_publicly("after failed decisions")

    # Admin publishes
    s, d = req("POST", decide, {"decision": "APPROVE_AND_PUBLISH", "expectedVersion": version}, admin)
    ok(s == 200 and d["data"].get("hotelPublicationStatus") == "PUBLISHED", f"Admin APPROVE_AND_PUBLISH 200 got {s}")
    visible_publicly("hotel PUBLISHED")
    s, d = req("POST", "/api/quotes", quote_body, token)
    ok(s in (200, 201), f"Quote on PUBLISHED hotel succeeds got {s}")

    # Admin SUSPEND hides, REINSTATE shows
    s, d = req("POST", f"/api/admin/hotels/{hotel_id}/publication-status", {"decision": "SUSPEND", "reason": "projection test"}, admin)
    ok(s == 200 and d["data"].get("publicationStatus") == "SUSPENDED", f"Admin SUSPEND 200 got {s}")
    hidden_publicly("hotel SUSPENDED")
    s, d = req("POST", f"/api/admin/hotels/{hotel_id}/publication-status", {"decision": "REINSTATE", "reason": "projection test"}, admin)
    ok(s == 200 and d["data"].get("publicationStatus") == "PUBLISHED", f"Admin REINSTATE 200 got {s}")
    visible_publicly("hotel reinstated")

    # Legacy hotels unchanged: every public hotel is PUBLISHED, public list is not empty
    s, d = req("GET", "/api/hotels")
    ok(s == 200 and len(d["data"]) > 0 and all(h.get("publicationStatus", "PUBLISHED") == "PUBLISHED" for h in d["data"]),
       "Public list only has PUBLISHED hotels (legacy backfilled hotels still listed)")

    # Deactivate a legacy-style check is out of scope (no legacy hotel creation API); verify
    # that active=false also hides a hotel from public listing using activate/deactivate on
    # the one we made (owner-authorized).
    s, d = req("POST", f"/api/hotels/{hotel_id}/deactivate", token=token)
    ok(s == 200 and d["data"]["active"] is False, "Deactivate hotel 200")

    s, d = req("GET", "/api/hotels")
    ok(not any(h["id"] == hotel_id for h in d["data"]), "Public list excludes deactivated hotel")

    s, d = req("GET", f"/api/hotels?organizationId={org_id}", token=token)
    ok(any(h["id"] == hotel_id for h in d["data"]), "organizationId-scoped list still shows deactivated hotel")

    print(f"PASS: {P}  FAIL: {F}")
    return 0 if F == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
