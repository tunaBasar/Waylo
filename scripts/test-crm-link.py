#!/usr/bin/env python3
"""Acceptance test: a plain registration is linked to a CRM customer end to end.

Registers a user through the gateway (no manual CRM call), then asserts:
  1. crm-service created a Customer whose id equals the auth userId (via auth-events)
  2. registering the same email again does not create a second CRM customer
(Customers are created only from auth-events. Legacy /api/bookings is internal-only now and
order-events have no CRM or notification consumer, so no booking/loyalty assertions here.)
Standard library only.
"""
import argparse, json, pathlib, secrets, subprocess, sys, time, uuid
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


def env_value(key):
    for line in (pathlib.Path(__file__).resolve().parent.parent / ".env").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip()
    return None


def main():
    global BASE_URL
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=BASE_URL)
    BASE_URL = ap.parse_args().base_url

    email = f"crmlink{uuid.uuid4().hex[:10]}@example.test"
    pw = secrets.token_urlsafe(16)
    s, d = req("POST", "/api/auth/register",
               {"firstName": "Plain", "lastName": "User", "email": email, "password": pw})
    ok(s == 200 and d and d.get("success"), f"register 200 (got {s})")
    ok("token" not in d["data"], "register issues no token")
    subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", "auth_db", "-tAc",
                    f"UPDATE users SET email_verified = true WHERE email = '{email}'"], check=True, capture_output=True)
    s, d = req("POST", "/api/auth/login", {"email": email, "password": pw})
    ok(s == 200 and d and d.get("success"), f"login after verify 200 (got {s})")
    uid, tok = d["data"]["userId"], d["data"]["token"]

    s, a = req("POST", "/api/auth/login", {"email": env_value("WAYLO_BOOTSTRAP_ADMIN_EMAIL"),
                                          "password": env_value("WAYLO_BOOTSTRAP_ADMIN_PASSWORD")})
    atok = a["data"]["token"] if s == 200 else None

    def customer():
        s, c = req("GET", f"/api/customers/{uid}", None, atok)
        return c["data"] if s == 200 and c and c.get("data") else None

    c = poll(customer)
    ok(c is not None and c["email"] == email and c["firstName"] == "Plain" and c["lastName"] == "User",
       "CRM customer auto-created with id == auth userId")

    s2, _ = req("POST", "/api/auth/register",
                {"firstName": "Plain", "lastName": "User", "email": email, "password": secrets.token_urlsafe(16)})
    ok(s2 in (400, 409, 422), f"duplicate register rejected (got {s2})")
    s3, lst = req("GET", f"/api/customers/email/{email}", None, atok)
    ok(s3 == 200 and lst and lst.get("data", {}).get("id") == uid, "exactly the same CRM customer found by email")

    # --- access control ---
    s, _ = req("GET", "/api/customers", None, tok)
    ok(s == 403, f"non-admin list customers 403 (got {s})")
    s, _ = req("GET", f"/api/customers/{uid}", None, tok)
    ok(s == 403, f"non-admin read by id (even own) 403 (got {s})")
    s, _ = req("GET", "/api/customers/search?q=Plain", None, tok)
    ok(s == 403, f"non-admin search 403 (got {s})")
    s, _ = req("DELETE", f"/api/customers/{uid}", None, tok)
    ok(s == 403, f"non-admin delete 403 (got {s})")
    s, _ = req("PUT", f"/api/customers/{uid}", {"firstName": "X", "lastName": "Y", "email": email}, tok)
    ok(s == 403, f"non-admin put 403 (got {s})")
    s, _ = req("POST", f"/api/customers/{uid}/loyalty-points?points=999", None, tok)
    ok(s == 403, f"non-admin add loyalty 403 (got {s})")
    s, _ = req("GET", "/api/customers", None, None)
    ok(s == 401, f"anonymous list 401 (got {s})")
    s, me = req("GET", "/api/customers/me", None, tok)
    ok(s == 200 and me["data"]["id"] == uid and "passportNumber" not in me["data"], "GET /me returns own record, no passport")
    ok(me["data"].get("loyaltyEnabled") is False and me["data"].get("loyaltyPoints") is None,
       "loyalty disabled by default: /me hides points (no points earned)")

    # --- profile change propagates ---
    s, _ = req("PATCH", "/api/me", {"firstName": "Renamed", "lastName": "Person"}, tok)
    ok(s == 200, f"profile update 200 (got {s})")

    # --- admin ---
    ok(atok is not None, "admin login")
    s, lst = req("GET", "/api/customers", None, atok)
    ok(s == 200 and isinstance(lst["data"], list) and len(lst["data"]) > 0, "admin list customers 200")
    ok(all("passportNumber" not in c for c in lst["data"]), "admin list never contains passportNumber")

    def renamed():
        s, c = req("GET", f"/api/customers/{uid}", None, atok)
        d = c["data"] if s == 200 and c else None
        return d if d and d["firstName"] == "Renamed" and d["lastName"] == "Person" else None

    ok(poll(renamed, 15) is not None, "profile name change propagated to CRM (USER_UPDATED)")

    print(f"\n{P} passed, {F} failed")
    sys.exit(1 if F else 0)


if __name__ == "__main__":
    main()
