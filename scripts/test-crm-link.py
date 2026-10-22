#!/usr/bin/env python3
"""Acceptance test: a plain registration is linked to a CRM customer end to end.

Registers a user through the gateway (no manual CRM call), then asserts:
  1. crm-service created a Customer whose id equals the auth userId (via auth-events)
  2. a booking for that user produces a notification addressed to the user's email
  3. crm-service added booking loyalty points to that customer
Standard library only.
"""
import argparse, json, secrets, sys, time, uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = "http://localhost:8080"
P = F = 0


def ok(cond, label):
    global P, F
    print(("PASS: " if cond else "FAIL: ") + label)
    if cond:
        P += 1
    else:
        F += 1


def req(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urlopen(Request(BASE_URL + path, data=data, headers=headers, method=method), timeout=15) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except HTTPError as e:
        raw = e.read()
        try:
            return e.code, (json.loads(raw) if raw else None)
        except ValueError:
            return e.code, None


def poll(fn, timeout=20):
    """Retry an eventually-consistent check (Kafka consumers are asynchronous)."""
    deadline = time.time() + timeout
    while True:
        result = fn()
        if result or time.time() > deadline:
            return result
        time.sleep(1)


def main():
    global BASE_URL
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=BASE_URL)
    BASE_URL = ap.parse_args().base_url

    email = f"crmlink{uuid.uuid4().hex[:10]}@example.test"
    s, d = req("POST", "/api/auth/register",
               {"firstName": "Plain", "lastName": "User", "email": email, "password": secrets.token_urlsafe(16)})
    ok(s == 200 and d and d.get("success"), f"register 200 (got {s})")
    uid, tok = d["data"]["userId"], d["data"]["token"]

    def customer():
        s, c = req("GET", f"/api/customers/{uid}", None, tok)
        return c["data"] if s == 200 and c and c.get("data") else None

    c = poll(customer)
    ok(c is not None and c["email"] == email and c["firstName"] == "Plain" and c["lastName"] == "User",
       "CRM customer auto-created with id == auth userId")

    s, b = poll(lambda: (lambda r: r if r[0] == 201 else None)(req("POST", "/api/bookings", {
        "customerId": uid, "flightId": str(uuid.uuid4()),
        "hotelReservationId": None, "vehicleReservationId": None, "notes": "crm link test"}, tok)), 15) or (None, None)
    ok(s == 201, f"booking created (got {s})")

    def notified():
        s, n = req("GET", f"/api/notifications/customer/{uid}", None, tok)
        return s == 200 and any(x["recipient"] == email and x["subject"] == "Rezervasyonunuz alindi"
                                for x in (n or {}).get("data", []))

    ok(poll(notified), "booking notification addressed to the user's email")

    pts = poll(lambda: (customer() or {}).get("loyaltyPoints") == 100)
    ok(pts, "booking loyalty points added (100)")

    print(f"\n{P} passed, {F} failed")
    sys.exit(1 if F else 0)


if __name__ == "__main__":
    main()
