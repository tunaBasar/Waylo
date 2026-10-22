#!/usr/bin/env python3
"""Acceptance test for the booking flow that feeds notification-service.

Verifies through the gateway that:
1. A booking can be created and cancelled (each publishes a booking-events record that
   notification-service and crm-service consume).
2. GET /api/bookings/flight/{flightId}, which notification-service uses to find the
   customers affected by a flight cancellation, returns exactly that flight's bookings.
The resulting notification and loyalty side effects are asserted by test-crm-link.py.
Uses standard library only.
"""
import argparse, json, secrets, sys, time, uuid
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
        print(f"FAIL: {label}")


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
            return resp.status, (json.loads(raw) if raw else None)
    except HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else None)


def reg():
    uid = uuid.uuid4().hex
    email = f"notiftest{uid}@example.test"
    password = secrets.token_urlsafe(16)
    s, d = req("POST", "/api/auth/register", {
        "firstName": "Notif", "lastName": "Test", "email": email, "password": password
    })
    ok(s == 200 and d.get("success"), f"Register: 200+success got {s} {d.get('success') if d else None}")
    return d["data"]["userId"], d["data"]["token"]


def main():
    global BASE_URL
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE_URL)
    args = parser.parse_args()
    BASE_URL = args.base_url

    customer_id, token = reg()

    # 1. Create a package booking (uses legacy /api/bookings endpoint, publishes BookingCreatedEvent)
    flight_id = str(uuid.uuid4())
    s, d = req("POST", "/api/bookings", {
        "customerId": customer_id,
        "flightId": flight_id,
        "hotelReservationId": None,
        "vehicleReservationId": None,
        "notes": "notification wiring test",
    }, token=token)
    ok(s in (200, 201) and d.get("success"), f"Create booking: 200/201+success got {s} {d}")
    booking_id = d["data"]["id"]

    # 2. New endpoint: GET /api/bookings/flight/{flightId} should return this booking
    s, d = req("GET", f"/api/bookings/flight/{flight_id}", token=token)
    ok(s == 200 and d.get("success"), f"GET /bookings/flight/{{flightId}}: 200+success got {s}")
    bookings = d["data"]
    ok(any(b["id"] == booking_id for b in bookings), "New booking present in flight-filtered list")
    ok(all(b["flightId"] == flight_id for b in bookings), "All returned bookings match flightId filter")

    # 3. Give Kafka consumers (notification-service, crm-service) time to process
    time.sleep(5)

    # 4. Cancel the booking -> BookingCancelledEvent (exercises the other listener branch)
    s, d = req("POST", f"/api/bookings/{booking_id}/cancel", token=token)
    ok(s == 200 and d.get("success"), f"Cancel booking: 200+success got {s} {d}")
    time.sleep(3)

    print(f"\n{P} passed, {F} failed")
    sys.exit(1 if F else 0)


if __name__ == "__main__":
    main()
