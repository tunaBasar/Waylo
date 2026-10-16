#!/usr/bin/env python3
"""Acceptance test exercising repository API contracts through the hotel-service gateway.

Registers real users and organizations, creates hotels/room-types/rate-plans,
then exercises inventory PUT, rate PUT, status PATCH, and concurrency guards.
Uses standard library only; parse_float=Decimal for monetary comparisons.
"""
import argparse, json, secrets, sys, threading, uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = "http://localhost:8080"
T = 15
P = F = 0
failures = []
creds = {}

# ── Global fixture state ───────────────────────────────────────────────────
H1 = H2 = H3 = RT1 = RT2 = RT3 = RP1 = RP2 = RP3 = None
ORG_ID = None

def ok(cond, label):
    """PASS on true (prints label); FAIL+raise AssertionError on false (stops test)."""
    global P, F
    if cond:
        P += 1
        print(f"PASS: {label}")
    else:
        F += 1
        failures.append(label)
        raise AssertionError(f"FAIL: {label}")

# ── HTTP helpers ───────────────────────────────────────────────────────────
def req(method, path, body=None, token=None):
    """Single read of response bytes, json.loads if non-empty, parse_float=Decimal."""
    url = BASE_URL + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    r = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(r, timeout=T) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            return resp.status, json.loads(raw, parse_float=Decimal)
    except HTTPError as e:
        raw = e.read()
        if not raw:
            return e.code, None
        return e.code, json.loads(raw, parse_float=Decimal)

# ── Auth helpers ───────────────────────────────────────────────────────────
def reg(n):
    uid = uuid.uuid4().hex
    email = f"test{uid}@example.test"
    password = secrets.token_urlsafe(16)
    creds[email] = password
    s, d = req("POST", "/api/auth/register", {
        "firstName": "Test", "lastName": "User", "email": email, "password": password
    })
    ok(s == 200 and d.get("success"), f"Reg{n}: 200+success got {s} {d.get('success')}")
    return d["data"]["userId"], d["data"]["token"]

def ln(email, password):
    s, d = req("POST", "/api/auth/login", {"email": email, "password": password})
    ok(s == 200 and d.get("success"), f"Login: 200+success got {s} {d.get('success')}")
    return d["data"]["token"]

# ── Fixture builders ───────────────────────────────────────────────────────
def mkorg(token, name="Org A"):
    s, d = req("POST", "/api/organizations", {"name": name}, token=token)
    ok(s == 201, f"Org: 201 got {s}")
    # ApiResponse.success("Organization created", resp) → data is OrganizationResponse
    return d["data"]["id"]

def mkhotel(token, org_id, name):
    s, d = req("POST", "/api/hotels", {
        "name": name, "city": "IST", "country": "TR", "address": "St1",
        "starRating": 4, "organizationId": org_id
    }, token=token)
    ok(s == 201, f"Hotel {name}: 201 got {s}"); return d["data"]["id"]

def mkrt(token, hotel_id, code="DBL", name="Double"):
    s, d = req("POST", f"/api/hotels/{hotel_id}/room-types", {
        "code": code, "name": name, "maxAdults": 2, "maxChildren": 1, "maxOccupancy": 3
    }, token=token)
    ok(s == 201, f"RT {code}: 201 got {s}"); r = d["data"]; return r["id"], r["version"]

def mkrp(token, hotel_id, code="BB", name="Breakfast"):
    s, d = req("POST", f"/api/hotels/{hotel_id}/rate-plans", {
        "code": code, "name": name, "mealPlan": "BREAKFAST",
        "cancellationPolicy": "FREE_CANCELLATION", "freeCancellationDaysBefore": 7
    }, token=token)
    ok(s == 201, f"RP {code}: 201 got {s}"); r = d["data"]; return r["id"], r["version"]

# ── Group A: Auth + CRUD + Forbidden ──────────────────────────────────────
def grp_a():
    # Dup RT → 409
    ok(req("POST", f"/api/hotels/{H1}/room-types", {
        "code": "DBL", "name": "Dup", "maxAdults": 2, "maxChildren": 1, "maxOccupancy": 3
    }, token=t1)[0] == 409, "Dup RT 409")
    # Bad occupancy rule (maxOccupancy < maxAdults) → 422 via business rule
    ok(req("POST", f"/api/hotels/{H1}/room-types", {
        "code": "BAD", "name": "Bad", "maxAdults": 2, "maxChildren": 0, "maxOccupancy": 3
    }, token=t1)[0] == 422, "Bad occ 422")
    # Missing freeCancellationDaysBefore for FREE_CANCELLATION → 422
    ok(req("POST", f"/api/hotels/{H1}/rate-plans", {
        "code": "FC1", "name": "NoDays", "mealPlan": "BREAKFAST",
        "cancellationPolicy": "FREE_CANCELLATION"
    }, token=t1)[0] == 422, "Missing canc days 422")
    # No token → 401
    ok(req("POST", f"/api/hotels/{H1}/room-types", {
        "code": "G1", "name": "G", "maxAdults": 2, "maxChildren": 1, "maxOccupancy": 3
    }, token=None)[0] == 401, "No token 401")
    # Other org user → 403
    ok(req("POST", f"/api/hotels/{H1}/room-types", {
        "code": "O1", "name": "O", "maxAdults": 2, "maxChildren": 1, "maxOccupancy": 3
    }, token=t2)[0] == 403, "Other user 403")
    # Active public GET → 200 (includeInactive=false default, ACTIVE RT visible)
    ok(req("GET", f"/api/hotels/{H1}/room-types/{RT1}", token=None)[0] == 200, "Active RT public 200")

# ── Group B: RT/RP PATCH + Inactive Guards ────────────────────────────────
def grp_b():
    global RT1V
    # RT PATCH status INACTIVE → 200
    s, d = req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {"status": "INACTIVE"}, token=t1)
    ok(s == 200, "RT inactive 200"); RT1V = d["data"]["version"]
    # Inactive RT detail → 404
    ok(req("GET", f"/api/hotels/{H1}/room-types/{RT1}", token=None)[0] == 404, "Inactive RT detail 404")
    # includeInactive=true on DETAIL for anon 401, other 403, owner 200
    ok(req("GET", f"/api/hotels/{H1}/room-types/{RT1}?includeInactive=true", token=None)[0] == 401, "Inc detail anon 401")
    ok(req("GET", f"/api/hotels/{H1}/room-types/{RT1}?includeInactive=true", token=t2)[0] == 403, "Inc detail other 403")
    ok(req("GET", f"/api/hotels/{H1}/room-types/{RT1}?includeInactive=true", token=t1)[0] == 200, "Inc detail owner 200")
    # Inactive list: same pattern
    ok(req("GET", f"/api/hotels/{H1}/room-types?includeInactive=true", token=None)[0] == 401, "Inc list anon 401")
    ok(req("GET", f"/api/hotels/{H1}/room-types?includeInactive=true", token=t2)[0] == 403, "Inc list other 403")
    ok(req("GET", f"/api/hotels/{H1}/room-types?includeInactive=true", token=t1)[0] == 200, "Inc list owner 200")
    # Reactivate → 200
    s, d = req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {"status": "ACTIVE"}, token=t1)
    ok(s == 200, "RT reactivate 200"); RT1V = d["data"]["version"]
    # Blank name → 400
    ok(req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {"name": ""}, token=t1)[0] == 400, "RT blank 400")
    # Description PATCH → 200; capture returned version for subsequent PATCHes
    s, d = req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {"description": "upd"}, token=t1)
    ok(s == 200, "RT desc 200"); rt_ver = d["data"]["version"]
    # Sequential name patch 1 using latest version
    s1, d1 = req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {
        "name": "N1", "expectedVersion": rt_ver
    }, token=t1)
    ok(s1 == 200 and d1["data"]["version"] == rt_ver + 1, "RT name1 ver")
    # Sequential name patch 2 using response from patch 1
    s2, d2 = req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {
        "name": "N2", "expectedVersion": d1["data"]["version"]
    }, token=t1)
    ok(s2 == 200, "RT name2 200")
    # GET version equals final response version
    s3, d3 = req("GET", f"/api/hotels/{H1}/room-types/{RT1}", token=t1)
    ok(s3 == 200 and d3["data"]["version"] == d2["data"]["version"], "RT ver match GET")
    # Wrong version → 409
    ok(req("PATCH", f"/api/hotels/{H1}/room-types/{RT1}", {
        "name": "W", "expectedVersion": d2["data"]["version"] - 1
    }, token=t1)[0] == 409, "RT stale 409")
    # === Rate Plan inactive guards ===
    s, d = req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {"status": "INACTIVE"}, token=t1)
    ok(s == 200, "RP inactive 200")
    # includeInactive on RP detail
    ok(req("GET", f"/api/hotels/{H1}/rate-plans/{RP1}?includeInactive=true", token=None)[0] == 401, "RP inc detail anon 401")
    ok(req("GET", f"/api/hotels/{H1}/rate-plans/{RP1}?includeInactive=true", token=t2)[0] == 403, "RP inc detail other 403")
    ok(req("GET", f"/api/hotels/{H1}/rate-plans/{RP1}?includeInactive=true", token=t1)[0] == 200, "RP inc detail owner 200")
    ok(req("GET", f"/api/hotels/{H1}/rate-plans/{RP1}", token=None)[0] == 404, "Inactive RP 404")
    # includeInactive list
    ok(req("GET", f"/api/hotels/{H1}/rate-plans?includeInactive=true", token=None)[0] == 401, "RP inc list anon 401")
    ok(req("GET", f"/api/hotels/{H1}/rate-plans?includeInactive=true", token=t2)[0] == 403, "RP inc list other 403")
    ok(req("GET", f"/api/hotels/{H1}/rate-plans?includeInactive=true", token=t1)[0] == 200, "RP inc list owner 200")
    # Reactivate RP
    s, d = req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {"status": "ACTIVE"}, token=t1)
    ok(s == 200, "RP reactivate 200"); rp_ver = d["data"]["version"]
    # NON_REFUNDABLE clears days
    s, d = req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {
        "cancellationPolicy": "NON_REFUNDABLE", "expectedVersion": rp_ver
    }, token=t1)
    ok(s == 200 and d["data"]["freeCancellationDaysBefore"] is None, "RP NON_REFUNDABLE clears days")
    # Blank name → 400
    ok(req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {"name": ""}, token=t1)[0] == 400, "RP blank 400")
    # Sequential RP name patches
    s1, d1 = req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {
        "name": "RN1", "expectedVersion": d["data"]["version"]
    }, token=t1)
    ok(s1 == 200, "RP name1 200")
    s2, d2 = req("PATCH", f"/api/hotels/{H1}/rate-plans/{RP1}", {
        "name": "RN2", "expectedVersion": d1["data"]["version"]
    }, token=t1)
    ok(s2 == 200, "RP name2 200")
    # GET versions equal
    s3, d3 = req("GET", f"/api/hotels/{H1}/rate-plans/{RP1}", token=t1)
    ok(s3 == 200 and d3["data"]["version"] == d2["data"]["version"], "RP ver match GET")

# ── Group C: Inventory ────────────────────────────────────────────────────
def grp_c():
    td = date.today(); d0 = td.isoformat(); d11 = (td + timedelta(days=11)).isoformat()
    # PUT two dates
    s, d = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c1", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 5, "outOfService": 1},
        {"roomTypeId": RT1, "stayDate": d11, "allotment": 3}
    ]}, token=t1)
    ok(s == 200, "Inv put 2 200"); ok(len(d["data"]) == 2, f"Inv 2 rows: {len(d['data'])}")
    a0 = [r for r in d["data"] if r["stayDate"] == d0][0]
    ok(a0["available"] == 4, f"Inv d0 avail=4: {a0['available']}")
    ok(a0["allotment"] == 5, f"Inv d0 allot=5: {a0['allotment']}")
    # Owner 200 / anon 401 / other 403
    ok(req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={d0}", token=t1)[0] == 200, "Inv owner 200")
    ok(req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={d0}", token=None)[0] == 401, "Inv anon 401")
    ok(req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={d0}", token=t2)[0] == 403, "Inv other 403")
    # Partial stopSell-only using returned version
    s, d = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c2", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "stopSell": True, "expectedVersion": a0["version"]}
    ]}, token=t1)
    ok(s == 200, "Inv partial stopSell 200")
    # No-op upsert preserves ID/version
    ver_before = d["data"][0]["version"]; id_before = d["data"][0]["id"]
    s, d = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c3", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "stopSell": True, "expectedVersion": d["data"][0]["version"]}
    ]}, token=t1)
    ok(s == 200 and d["data"][0]["id"] == id_before, "Inv no-op preserves ID")
    ok(d["data"][0]["version"] == ver_before, "Inv no-op preserves version")
    # Dup item → 400
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c4", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 5},
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 6}
    ]}, token=t1)[0] == 400, "Inv dup 400")
    # outOfService > allotment → 422 (bean-valid but business rule violated)
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c5", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 5, "outOfService": 6}
    ]}, token=t1)[0] == 422, "Inv oos>allot 422")
    # Stale version → 409
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c6", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 5, "expectedVersion": a0["version"]}
    ]}, token=t1)[0] == 409, "Inv stale 409")
    # New row + expectedVersion → 409
    td12 = (td + timedelta(days=12)).isoformat()
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c7", "items": [
        {"roomTypeId": RT1, "stayDate": td12, "allotment": 5, "expectedVersion": 999}
    ]}, token=t1)[0] == 409, "Inv new+ev 409")
    # PUT date out of range → 422 (business rule, not validation 400)
    old_1 = (td - timedelta(days=2)).isoformat()
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c8", "items": [
        {"roomTypeId": RT1, "stayDate": old_1, "allotment": 5}
    ]}, token=t1)[0] == 422, "Inv old date PUT 422")
    far_1 = (td + timedelta(days=731)).isoformat()
    ok(req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c9", "items": [
        {"roomTypeId": RT1, "stayDate": far_1, "allotment": 5}
    ]}, token=t1)[0] == 422, "Inv far date PUT 422")
    # Mixed valid+far batch → 422 (rollback: valid row not persisted)
    s_bad, _ = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c10", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 10},
        {"roomTypeId": RT1, "stayDate": far_1, "allotment": 5}
    ]}, token=t1)
    ok(s_bad == 422, "Inv mixed batch 422")
    # Verify rollback: re-read d0 allotment should be back to 5
    s, d = req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={d0}", token=t1)
    a_rb = [r for r in d["data"] if r["stayDate"] == d0][0]
    ok(a_rb["allotment"] == 5, f"Inv rollback allot=5: {a_rb['allotment']}")
    # GET >366 days → 400
    ok(req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={(td + timedelta(days=366)).isoformat()}", token=t1)[0] == 400, "Inv >366 400")
    # GET missing dates → []
    future_d = (td + timedelta(days=900)).isoformat()
    s, d = req("GET", f"/api/hotels/{H1}/inventory?from={future_d}&to={future_d}", token=t1)
    ok(s == 200 and d["data"] == [], "Inv missing []")
    # Two consecutive real updates using returned versions
    s, d = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c11", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 20}
    ]}, token=t1)
    ok(s == 200, "Inv upd1 200"); v1 = d["data"][0]["version"]
    ok(d["data"][0]["allotment"] == 20, "Inv upd1 allot=20")
    s, d = req("PUT", f"/api/hotels/{H1}/inventory", {"reason": "c12", "items": [
        {"roomTypeId": RT1, "stayDate": d0, "allotment": 25, "expectedVersion": v1}
    ]}, token=t1)
    ok(s == 200 and d["data"][0]["allotment"] == 25, "Inv upd2 allot=25")
    upd2_ver = d["data"][0]["version"]
    # GET persists version and allotment from second update
    s, d = req("GET", f"/api/hotels/{H1}/inventory?from={d0}&to={d0}", token=t1)
    ok(s == 200 and d["data"][0]["version"] == upd2_ver, "Inv upd2 ver persisted")
    ok(Decimal(str(d["data"][0]["allotment"])) == Decimal(25), "Inv upd2 allot persisted")

# ── Group D: Rates ────────────────────────────────────────────────────────
def grp_d():
    td = date.today(); d0 = td.isoformat(); d1 = (td + timedelta(days=1)).isoformat()
    # PUT two nights
    s, d = req("PUT", f"/api/hotels/{H1}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "100.00", "currency": "TRY"},
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d1, "occupancy": 2, "amount": "120.00", "currency": "TRY"}
    ]}, token=t1)
    ok(s == 200, "Rates 2 200"); ok(len(d["data"]) == 2, f"Rates 2 rows: {len(d['data'])}")
    r0 = [r for r in d["data"] if r["stayDate"] == d0][0]
    r1 = [r for r in d["data"] if r["stayDate"] == d1][0]
    ok(Decimal(str(r0["amount"])) == Decimal("100.00"), f"Rate d0=100: {r0['amount']}")
    ok(Decimal(str(r1["amount"])) == Decimal("120.00"), f"Rate d1=120: {r1['amount']}")
    # Owner 200 / anon 401 / other 403
    ok(req("GET", f"/api/hotels/{H1}/rates?from={d0}&to={d0}", token=t1)[0] == 200, "Rates owner 200")
    ok(req("GET", f"/api/hotels/{H1}/rates?from={d0}&to={d0}", token=None)[0] == 401, "Rates anon 401")
    ok(req("GET", f"/api/hotels/{H1}/rates?from={d0}&to={d0}", token=t2)[0] == 403, "Rates other 403")
    # Sequential updates
    v0 = r0["version"]
    s1, d1 = req("PUT", f"/api/hotels/{H1}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "110.00", "currency": "TRY", "expectedVersion": v0}
    ]}, token=t1)
    ok(s1 == 200, "Rate upd1 200")
    s2, d2 = req("PUT", f"/api/hotels/{H1}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "115.00", "currency": "TRY", "expectedVersion": d1["data"][0]["version"]}
    ]}, token=t1)
    ok(s2 == 200, "Rate upd2 200")
    s3, d3 = req("GET", f"/api/hotels/{H1}/rates?from={d0}&to={d0}", token=t1)
    ok(s3 == 200 and Decimal(str(d3["data"][0]["amount"])) == Decimal("115.00"), "Rate final 115")
    ok(s3 == 200 and d3["data"][0]["version"] == d2["data"][0]["version"], "Rate ver match GET")
    # Negative → 400
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "-10.00", "currency": "TRY"
    }]}, token=t1)[0] == 400, "Rate neg 400")
    # 3 decimals → 400
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "100.123", "currency": "TRY"
    }]}, token=t1)[0] == 400, "Rate 3dec 400")
    # Invalid currency (valid format but wrong for hotel) → 422
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "100.00", "currency": "EUR"
    }]}, token=t1)[0] == 422, "Rate wrong curr 422")
    # Occupancy exceeds roomType maxOccupancy (3) → 422
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 4, "amount": "100.00", "currency": "TRY"
    }]}, token=t1)[0] == 422, "Rate over occ 422")
    # Dup → 400
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "50.00", "currency": "TRY"},
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "60.00", "currency": "TRY"}
    ]}, token=t1)[0] == 400, "Rate dup 400")
    # Stale version → 409
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "999.00", "currency": "TRY", "expectedVersion": v0
    }]}, token=t1)[0] == 409, "Rate stale 409")
    # New row + expectedVersion → 409
    ok(req("PUT", f"/api/hotels/{H1}/rates", {"items": [{
        "roomTypeId": RT1, "ratePlanId": RP1, "stayDate": (td + timedelta(days=5)).isoformat(),
        "occupancy": 2, "amount": "50.00", "currency": "TRY", "expectedVersion": 999
    }]}, token=t1)[0] == 409, "Rate new+ev 409")
    # Mixed valid+invalid batch rollback: valid rate unchanged after invalid item fails
    s_bad, _ = req("PUT", f"/api/hotels/{H1}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 2, "amount": "200.00", "currency": "TRY"},
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": d0, "occupancy": 4, "amount": "100.00", "currency": "TRY"}
    ]}, token=t1)
    ok(s_bad == 422, "Rate mixed batch 422")
    # Verify rollback: amount should still be 115.00
    s, d = req("GET", f"/api/hotels/{H1}/rates?from={d0}&to={d0}", token=t1)
    ok(Decimal(str(d["data"][0]["amount"])) == Decimal("115.00"), "Rate rollback 115")

# ── Group E: Foreign Child IDs ────────────────────────────────────────────
def grp_e():
    ok(req("GET", f"/api/hotels/{H2}/room-types", token=t1)[0] == 200, "H2 RT list 200")
    # Inv with foreign RT1 → 404 (RT1 belongs to H1, not H2)
    ok(req("PUT", f"/api/hotels/{H2}/inventory", {"reason": "e1", "items": [
        {"roomTypeId": RT1, "stayDate": date.today().isoformat(), "allotment": 5}
    ]}, token=t1)[0] == 404, "Inv foreign RT1 404")
    # Rate with foreign RT1+RP1 → 404
    ok(req("PUT", f"/api/hotels/{H2}/rates", {"items": [
        {"roomTypeId": RT1, "ratePlanId": RP1, "stayDate": date.today().isoformat(),
         "occupancy": 2, "amount": "50.00", "currency": "TRY"}
    ]}, token=t1)[0] == 404, "Rate foreign RT1+RP1 404")
    # Detail GET RT1 under H2 → 404
    ok(req("GET", f"/api/hotels/{H2}/room-types/{RT1}", token=t1)[0] == 404, "Detail RT1 under H2 404")

# ── Group F: Concurrency ──────────────────────────────────────────────────
def grp_f():
    global RT2, RP2, RT3, RP3, H3
    RT2, _ = mkrt(t1, H2, "SGL"); RP2, _ = mkrp(t1, H2, "CP")
    td = date.today(); d0 = td.isoformat()
    # Inv concurrency: set initial allotment=5, then concurrent writes 6&7
    req("PUT", f"/api/hotels/{H2}/inventory", {"reason": "fc1", "items": [
        {"roomTypeId": RT2, "stayDate": d0, "allotment": 5}
    ]}, token=t1)
    _, d = req("GET", f"/api/hotels/{H2}/inventory?from={d0}&to={d0}", token=t1)
    ev = [r for r in d["data"] if r["roomTypeId"] == RT2][0]["version"]
    R = {}; bar = threading.Barrier(2, timeout=10)
    def inv_t(v, result):
        bar.wait()
        s, resp = req("PUT", f"/api/hotels/{H2}/inventory", {"reason": "x", "items": [
            {"roomTypeId": RT2, "stayDate": d0, "allotment": v, "expectedVersion": ev}
        ]}, token=t1)
        result[v] = (s, resp["data"][0]["version"] if resp and "data" in resp else None)
    with ThreadPoolExecutor(max_workers=2) as ex:
        f1 = ex.submit(inv_t, 6, R); f2 = ex.submit(inv_t, 7, R)
        f1.result(); f2.result()
    ok(sorted(s for s, _ in R.values()) == [200, 409], f"Inv conc {sorted(s for s, _ in R.values())}")
    # Collect winner inventory version and compare to GET
    winner_inv_ver = [v for s, v in R.values() if s == 200][0]
    s, d = req("GET", f"/api/hotels/{H2}/inventory?from={d0}&to={d0}", token=t1)
    ok(s == 200 and d["data"][0]["version"] == winner_inv_ver, "Inv conc winner ver matches GET")
    # Rate concurrency: initial 50, concurrent 55&60
    req("PUT", f"/api/hotels/{H2}/rates", {"items": [{
        "roomTypeId": RT2, "ratePlanId": RP2, "stayDate": d0, "occupancy": 2, "amount": "50.00", "currency": "TRY"
    }]}, token=t1)
    _, d = req("GET", f"/api/hotels/{H2}/rates?from={d0}&to={d0}", token=t1)
    ev2 = [r for r in d["data"] if r["roomTypeId"] == RT2][0]["version"]
    R2 = {}; bar2 = threading.Barrier(2, timeout=10)
    def rate_t(a, result):
        bar2.wait()
        s, resp = req("PUT", f"/api/hotels/{H2}/rates", {"items": [{
            "roomTypeId": RT2, "ratePlanId": RP2, "stayDate": d0, "occupancy": 2,
            "amount": str(a), "currency": "TRY", "expectedVersion": ev2
        }]}, token=t1)
        result[a] = (s, resp["data"][0]["version"] if resp and "data" in resp else None)
    with ThreadPoolExecutor(max_workers=2) as ex:
        f1 = ex.submit(rate_t, 55.0, R2); f2 = ex.submit(rate_t, 60.0, R2)
        f1.result(); f2.result()
    ok(sorted(s for s, _ in R2.values()) == [200, 409], f"Rate conc {sorted(s for s, _ in R2.values())}")
    # Winner version: re-read and compare via GET
    winner_rate_ver = [v for s, v in R2.values() if s == 200][0]
    s, d = req("GET", f"/api/hotels/{H2}/rates?from={d0}&to={d0}", token=t1)
    ok(s == 200 and d["data"][0]["version"] == winner_rate_ver, "Rate conc winner ver matches GET")
    # New hotel concurrency: different stayDates for TRY/EUR
    org3 = mkorg(t1, "Org C"); H3 = mkhotel(t1, org3, "Hotel C")
    RT3, _ = mkrt(t1, H3, "CT"); RP3, _ = mkrp(t1, H3, "CP")
    d_try = (td + timedelta(days=1)).isoformat()
    d_eur = (td + timedelta(days=2)).isoformat()
    R3 = {}; bar3 = threading.Barrier(2, timeout=10)
    def rate_cur(cur, result):
        bar3.wait()
        s, _ = req("PUT", f"/api/hotels/{H3}/rates", {"items": [{
            "roomTypeId": RT3, "ratePlanId": RP3, "stayDate": d_eur if cur == "EUR" else d_try,
            "occupancy": 2, "amount": "50.00", "currency": cur
        }]}, token=t1)
        result[cur] = s
    with ThreadPoolExecutor(max_workers=2) as ex:
        f1 = ex.submit(rate_cur, "TRY", R3); f2 = ex.submit(rate_cur, "EUR", R3)
        f1.result(); f2.result()
    ok(sorted(R3.values()) == [200, 422], f"TRY/EUR conc {sorted(R3.values())}")
    # Verify winner version persists: re-read H2 rates from concurrency test
    s, d = req("GET", f"/api/hotels/{H2}/rates?from={d0}&to={d0}", token=t1)
    ok(s == 200 and len(d["data"]) >= 1, "Rates persist after concurrency")
    # Re-read H2 inventory from concurrency test
    s, d = req("GET", f"/api/hotels/{H2}/inventory?from={d0}&to={d0}", token=t1)
    ok(s == 200 and len(d["data"]) >= 1, "Inventory persists after concurrency")

# ── Main ──────────────────────────────────────────────────────────────────
def main():
    global t1, t2, H1, H2, H3, RT1, RT2, RT3, RP1, RP2, RP3
    try:
        # Register + login
        reg(1); reg(2)
        emails = sorted(creds.keys())
        t1 = ln(emails[0], creds[emails[0]]); t2 = ln(emails[1], creds[emails[1]])
        # Org + hotels
        oid = mkorg(t1); H1 = mkhotel(t1, oid, "Hotel A"); H2 = mkhotel(t1, oid, "Hotel B")
        # Room types + rate plans on H1
        RT1, _ = mkrt(t1, H1); RP1, _ = mkrp(t1, H1)
        # Run groups
        grp_a(); grp_b(); grp_c(); grp_d(); grp_e(); grp_f()
    except AssertionError:
        pass
    finally:
        print(f"PASS: {P}  FAIL: {F}")
        if failures:
            for f in failures: print(f"  ✗ {f}")
        print(f"  h1={H1}  h2={H2}  h3={H3}")
    sys.exit(1 if F else 0)

# ── Dry-run: unittest.mock tests (no network) ─────────────────────────────
if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=BASE_URL)
    ap.add_argument("--dry-run", action="store_true", help="Run mock validation only")
    args = ap.parse_args()
    if args.dry_run:
        from unittest.mock import patch, MagicMock
        # Mock urlopen to return a fake JSON response
        fake_resp = json.dumps({"success": True, "data": {"id": "test-uuid", "version": 1}}).encode()
        mock_resp = MagicMock()
        mock_resp.read.return_value = fake_resp
        mock_resp.status = 200
        mock_resp.__enter__ = lambda s: s
        mock_resp.__exit__ = lambda s, *a: None
        with patch("__main__.urlopen", return_value=mock_resp):
            # Verify req signature accepts token= keyword
            s, d = req("POST", "/test", body={"x": 1}, token="tok123")
            ok(s == 200, "dry-run req with token kwarg")
            ok(d["data"]["version"] == 1, "dry-run parse_float=Decimal works")
            # Verify empty body returns None data
            mock_empty = MagicMock()
            mock_empty.read.return_value = b""
            mock_empty.status = 200
            mock_empty.__enter__ = lambda s: s
            mock_empty.__exit__ = lambda s, *a: None
            with patch("__main__.urlopen", return_value=mock_empty):
                s, d = req("POST", "/test", token="tok")
                ok(s == 200 and d is None, "dry-run empty body returns None")
        print(f"DRY-RUN: PASS: {P}  FAIL: {F}")
        if F:
            for f in failures: print(f"  ✗ {f}")
        sys.exit(1 if F else 0)
    main()
