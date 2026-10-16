#!/usr/bin/env python3
"""Acceptance test for package-booking-service Order/OrderItem API through the gateway.

Registers two real users, exercises order create/get/list/cancel, self-service
ownership (customerId must equal caller), currency consistency, v1 single-item
cap, optimistic concurrency on cancel, and foreign-order access denial.
Uses standard library only; parse_float=Decimal for monetary comparisons.
"""
import argparse, json, secrets, sys, uuid
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = "http://localhost:8080"
T = 15
P = F = 0
failures = []
creds = {}


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print(f"PASS: {label}")
    else:
        F += 1
        failures.append(label)
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
            if not raw:
                return resp.status, None
            return resp.status, json.loads(raw, parse_float=Decimal)
    except HTTPError as e:
        raw = e.read()
        if not raw:
            return e.code, None
        return e.code, json.loads(raw, parse_float=Decimal)


def reg(n):
    uid = uuid.uuid4().hex
    email = f"ordtest{uid}@example.test"
    password = secrets.token_urlsafe(16)
    creds[email] = password
    s, d = req("POST", "/api/auth/register", {
        "firstName": "Test", "lastName": "User", "email": email, "password": password
    })
    ok(s == 200 and d.get("success"), f"Reg{n}: 200+success got {s} {d.get('success')}")
    return d["data"]["userId"], d["data"]["token"]


def mkitem(ref=None, currency="USD", amount="100.00", qty=2):
    return {
        "vertical": "HOTEL",
        "supplierOrganizationId": None,
        "productRef": ref or str(uuid.uuid4()),
        "description": "Deluxe room, 2 nights",
        "unitAmount": amount,
        "quantity": qty,
        "currency": currency,
    }


def main():
    global BASE_URL
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    BASE_URL = args.base_url.rstrip("/") or BASE_URL

    if args.dry_run:
        ok(req("GET", "/api/orders", token="bad")[0] in (401, 403), "dry-run req with bad token")
        print(f"DRY-RUN: PASS: {P}  FAIL: {F}")
        return 0

    u1, t1 = reg(1)
    u2, t2 = reg(2)

    # No token -> 401
    ok(req("POST", "/api/orders", {})[0] == 401, "Create no token 401")
    ok(req("GET", "/api/orders")[0] == 401, "List no token 401")

    # Create for self -> 201
    s, d = req("POST", "/api/orders", {
        "customerId": u1, "currency": "USD", "notes": "n1", "items": [mkitem()]
    }, token=t1)
    ok(s == 201 and d["data"]["status"] == "PENDING", f"Create self 201 got {s}")
    order_id = d["data"]["id"]
    ok(Decimal(str(d["data"]["totalAmount"])) == Decimal("200.00"), "Total amount computed 200.00")
    ok(d["data"]["version"] == 0, "Initial version 0")

    # Create on behalf of another customer -> 403
    ok(req("POST", "/api/orders", {
        "customerId": u2, "currency": "USD", "items": [mkitem()]
    }, token=t1)[0] == 403, "Create for other customer 403")

    # Item currency mismatch -> 422
    ok(req("POST", "/api/orders", {
        "customerId": u1, "currency": "USD", "items": [mkitem(currency="EUR")]
    }, token=t1)[0] == 422, "Item currency mismatch 422")

    # More than 1 item -> 400 (v1 cap)
    ok(req("POST", "/api/orders", {
        "customerId": u1, "currency": "USD", "items": [mkitem(), mkitem()]
    }, token=t1)[0] == 400, "More than 1 item 400")

    # Zero items -> 400
    ok(req("POST", "/api/orders", {
        "customerId": u1, "currency": "USD", "items": []
    }, token=t1)[0] == 400, "Zero items 400")

    # Negative amount -> 400
    ok(req("POST", "/api/orders", {
        "customerId": u1, "currency": "USD", "items": [mkitem(amount="-5.00")]
    }, token=t1)[0] == 400, "Negative amount 400")

    # Invalid currency format -> 400
    ok(req("POST", "/api/orders", {
        "customerId": u1, "currency": "usd", "items": [mkitem(currency="usd")]
    }, token=t1)[0] == 400, "Lowercase currency 400")

    # GET by id: owner 200, other user 403, no token 401
    ok(req("GET", f"/api/orders/{order_id}", token=t1)[0] == 200, "GET owner 200")
    ok(req("GET", f"/api/orders/{order_id}", token=t2)[0] == 403, "GET other user 403")
    ok(req("GET", f"/api/orders/{order_id}")[0] == 401, "GET no token 401")

    # GET nonexistent -> 404
    ok(req("GET", f"/api/orders/{uuid.uuid4()}", token=t1)[0] == 404, "GET nonexistent 404")

    # List own: only own orders
    s, d = req("GET", "/api/orders", token=t1)
    ok(s == 200 and any(o["id"] == order_id for o in d["data"]), "List own contains created order")
    s, d = req("GET", "/api/orders", token=t2)
    ok(s == 200 and not any(o["id"] == order_id for o in d["data"]), "List other user does not contain order")

    # Cancel: other user -> 403
    ok(req("POST", f"/api/orders/{order_id}/cancel", token=t2)[0] == 403, "Cancel other user 403")

    # Cancel: wrong expectedVersion -> 409
    ok(req("POST", f"/api/orders/{order_id}/cancel?expectedVersion=99", token=t1)[0] == 409, "Cancel stale version 409")

    # Cancel: correct -> 200, version incremented and persisted (not stale)
    s, d = req("POST", f"/api/orders/{order_id}/cancel?expectedVersion=0", token=t1)
    ok(s == 200 and d["data"]["status"] == "CANCELLED", "Cancel valid 200")
    ok(d["data"]["version"] == 1, f"Cancel response version=1 (not stale): got {d['data']['version']}")

    # GET after cancel reflects persisted version
    s, d = req("GET", f"/api/orders/{order_id}", token=t1)
    ok(s == 200 and d["data"]["version"] == 1, "GET after cancel version=1 matches persisted")

    # Cancel again -> 422 ALREADY_CANCELLED
    s, d = req("POST", f"/api/orders/{order_id}/cancel", token=t1)
    ok(s == 422 and d["error"] == "ALREADY_CANCELLED", "Cancel already-cancelled 422")

    # Second order, cancel without expectedVersion -> 200 (optional field)
    s, d = req("POST", "/api/orders", {
        "customerId": u1, "currency": "TRY", "items": [mkitem(currency="TRY", amount="50.00", qty=1)]
    }, token=t1)
    ok(s == 201, "Create order2 201")
    order2_id = d["data"]["id"]
    s, d = req("POST", f"/api/orders/{order2_id}/cancel", token=t1)
    ok(s == 200 and d["data"]["status"] == "CANCELLED", "Cancel without expectedVersion 200")

    print(f"PASS: {P}  FAIL: {F}")
    return 0 if F == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
