#!/usr/bin/env python3
"""Acceptance test: email change flow (auth-service) + CRM propagation and manual-customer linking.

1. register+verify user A, login; POST /api/me/email (wrong password -> 400, duplicate -> 409)
2. valid request -> 202; old email still logs in; outbox rows published for VERIFICATION + EMAIL_CHANGE_REQUESTED
3. confirm via the token read from auth_db (GET /api/auth/verify) -> email switched
4. old refresh token rejected (401), old email login fails, new email login works
5. crm customer email updated (USER_UPDATED)
6. a manually created CRM customer with the same email is linked (id == auth userId) on registration
Standard library only. Uses docker exec psql like the other scripts.
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


def poll(fn, timeout=25):
    deadline = time.time() + timeout
    while True:
        result = fn()
        if result or time.time() > deadline:
            return result
        time.sleep(1)


def psql(sql):
    out = subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", "auth_db", "-tAc", sql],
                         check=True, capture_output=True, text=True)
    return out.stdout.strip()


def env_value(key):
    for line in (pathlib.Path(__file__).resolve().parent.parent / ".env").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip()
    return None


def login(email, password):
    """POST /api/auth/login, waiting out the gateway's per-IP auth rate limit (429) instead of failing on it."""
    deadline = time.time() + 75
    while True:
        s, d = req("POST", "/api/auth/login", {"email": email, "password": password})
        if s != 429 or time.time() > deadline:
            return s, d
        time.sleep(5)


def register_verified(email, pw, first="Mail", last="Changer"):
    s, _ = req("POST", "/api/auth/register", {"firstName": first, "lastName": last, "email": email, "password": pw})
    psql(f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    return s


def main():
    global BASE_URL
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=BASE_URL)
    BASE_URL = ap.parse_args().base_url

    tag = uuid.uuid4().hex[:10]
    old_email, new_email, other_email = f"emc-old-{tag}@example.test", f"emc-new-{tag}@example.test", f"emc-oth-{tag}@example.test"
    pw = secrets.token_urlsafe(16) + "Aa1!"
    ok(register_verified(old_email, pw) == 200, "register user A")
    ok(register_verified(other_email, pw, "Other", "User") == 200, "register user B")
    s, d = login(old_email, pw)
    ok(s == 200, f"login A (got {s})")
    uid, tok, rtok = d["data"]["userId"], d["data"]["token"], d["data"]["refreshToken"]

    s, a = login(env_value("WAYLO_BOOTSTRAP_ADMIN_EMAIL"), env_value("WAYLO_BOOTSTRAP_ADMIN_PASSWORD"))
    atok = a["data"]["token"] if s == 200 else None
    ok(atok is not None, "admin login")

    def crm_email():
        s, c = req("GET", f"/api/customers/{uid}", None, atok)
        return c["data"]["email"] if s == 200 and c and c.get("data") else None

    ok(poll(lambda: crm_email() == old_email), "CRM customer created via outbox-relayed auth event")

    s, _ = req("POST", "/api/me/email", {"newEmail": new_email, "currentPassword": "wrong-" + pw}, tok)
    ok(s == 400, f"wrong password rejected 400 (got {s})")
    s, _ = req("POST", "/api/me/email", {"newEmail": other_email, "currentPassword": pw}, tok)
    ok(s == 409, f"duplicate email 409 (got {s})")
    s, _ = req("POST", "/api/me/email", {"newEmail": new_email, "currentPassword": pw})
    ok(s == 401, f"anonymous email change 401 (got {s})")
    s, _ = req("POST", "/api/me/email", {"newEmail": new_email.upper(), "currentPassword": pw}, tok)
    ok(s == 202, f"email change requested 202 (got {s})")

    ok(psql(f"SELECT pending_email FROM users WHERE id = '{uid}'") == new_email, "pending_email stored (normalized)")
    ok(psql(f"SELECT email FROM users WHERE id = '{uid}'") == old_email, "email unchanged before confirm")
    s, _ = login(old_email, pw)
    ok(s == 200, f"old email login still works before confirm (got {s})")
    published = poll(lambda: psql(
        f"SELECT count(*) FROM outbox_event WHERE aggregate_id = '{uid}' AND published_at IS NOT NULL "
        f"AND (payload LIKE '%EMAIL_CHANGE_REQUESTED%' OR payload LIKE '%{new_email}%')") == "2", 15)
    ok(published, "VERIFICATION (to new) + EMAIL_CHANGE_REQUESTED (to old) relayed from outbox")

    token = psql(f"SELECT verification_token FROM users WHERE id = '{uid}'")
    s, _ = req("GET", f"/api/auth/verify?token={token}")
    ok(s == 200, f"confirm link 200 (got {s})")
    ok(psql(f"SELECT email || '|' || coalesce(pending_email, '') FROM users WHERE id = '{uid}'") == new_email + "|",
       "email switched, pending cleared")
    s, _ = req("GET", f"/api/auth/verify?token={token}")
    ok(s == 400, f"confirm link single-use (got {s})")

    s, _ = req("POST", "/api/auth/refresh", {"refreshToken": rtok})
    ok(s == 401, f"old refresh token rejected (got {s})")
    s, _ = login(old_email, pw)
    ok(s == 401, f"old email login fails after confirm (got {s})")
    s, d = login(new_email, pw)
    ok(s == 200 and d["data"]["email"] == new_email, f"login with new email (got {s})")

    ok(poll(lambda: crm_email() == new_email), "CRM customer email updated (USER_UPDATED)")

    # --- manual CRM customer with the same email gets linked on registration ---
    manual_email = f"emc-manual-{tag}@example.test"
    s, c = req("POST", "/api/customers", {"firstName": "Manual", "lastName": "Customer", "email": manual_email}, atok)
    ok(s in (200, 201) and c and c.get("data"), f"admin creates manual CRM customer (got {s})")
    manual_id = c["data"]["id"] if c and c.get("data") else None
    ok(register_verified(manual_email, pw, "Manual", "Customer") == 200, "register user with the manual customer's email")
    muid = psql(f"SELECT id FROM users WHERE email = '{manual_email}'")

    def linked():
        s, c = req("GET", f"/api/customers/email/{manual_email}", None, atok)
        return c["data"] if s == 200 and c and c.get("data", {}).get("id") == muid else None

    ok(poll(linked) is not None and muid != manual_id, "manual CRM customer re-keyed to auth userId (linked, no WARN)")
    s, _ = req("GET", f"/api/customers/{manual_id}", None, atok)
    ok(s == 404, f"old manual id no longer exists, no duplicate row (got {s})")

    print(f"\n{P} passed, {F} failed")
    sys.exit(1 if F else 0)


if __name__ == "__main__":
    main()
