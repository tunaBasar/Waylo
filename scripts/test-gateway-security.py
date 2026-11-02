#!/usr/bin/env python3
"""Gateway security acceptance: spoofed headers, /internal, traversal, refresh-as-access, XFF rate limit.
Standard library only. Needs the stack up (gateway on :8080)."""
import json, secrets, sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://localhost:8080"
P = F = 0


def ok(c, label):
    global P, F
    print(("PASS: " if c else "FAIL: ") + label)
    if c: P += 1
    else: F += 1


def req(method, path, body=None, headers=None):
    h = {"Content-Type": "application/json"}
    h.update(headers or {})
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urlopen(Request(BASE + path, data=data, headers=h, method=method), timeout=15) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw[:1] in (b"{", b"[") else raw)
    except HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def raw_status(path):
    """Send the raw request line untouched (urllib would normalise nothing, but keep it explicit)."""
    import socket
    s = socket.create_connection(("localhost", 8080), timeout=10)
    s.sendall(f"GET {path} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".encode())
    line = s.recv(64).split(b"\r\n")[0].decode()
    s.close()
    return int(line.split()[1])


def main():
    # 1. /internal never reachable from outside
    for m, p in [("POST", "/internal/orders/1/paid"), ("GET", "/internal/hotels/1"),
                 ("GET", "/api/internal/orders"), ("POST", "/internal/escrow-items")]:
        c, _ = req(m, p, {} if m == "POST" else None, {"X-Internal-Token": "guess"})
        ok(c in (401, 403, 404), f"{m} {p} blocked ({c})")

    # 2. traversal / encoded variants
    for p in ["/api/hotels/%2e%2e/orders", "/api/hotels/..%2fauth", "/api/hotels//x", "/api/hotels;x=1",
              "/api/hotels/x%00", "/api/hotels/%5c.."]:
        c = raw_status(p)
        ok(c == 400, f"traversal {p} -> {c}")

    # 3. register, login, refresh-as-access, spoofed identity
    email = f"gwsec-{secrets.token_hex(4)}@example.com"
    pw = "Gw-" + secrets.token_urlsafe(12)
    c, _ = req("POST", "/api/auth/register", {"firstName": "Gw", "lastName": "Sec", "email": email, "password": pw},
               {"X-Forwarded-For": "203.0.113.1"})
    ok(c == 200, f"register 200 ({c})")
    import subprocess
    subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", "auth_db", "-tAc",
                    f"UPDATE users SET email_verified = true WHERE email = '{email}'"], check=True, capture_output=True)
    c, body = req("POST", "/api/auth/login", {"email": email, "password": pw})
    d = (body or {}).get("data", {}) if isinstance(body, dict) else {}
    refresh = d.get("refreshToken")
    access = d.get("token")
    if refresh:
        c, _ = req("GET", "/api/me", None, {"Authorization": f"Bearer {refresh}"})
        ok(c == 401, f"refresh token as access -> {c}")
        c, _ = req("GET", "/api/me", None, {"Authorization": f"Bearer {access}"})
        ok(c != 401, f"access token accepted -> {c}")
        c, _ = req("GET", "/api/me", None, {"Authorization": f"Bearer {access}", "X-User-Role": "ADMIN", "X-User-Id": "1"})
        ok(c != 401, f"valid token with spoofed headers still ok -> {c}")
    else:
        ok(False, f"login returned no refreshToken ({c})")
    c, _ = req("GET", "/api/orders", None, {"Authorization": "Bearer garbage"})
    ok(c == 401, f"garbage bearer -> {c}")
    c, _ = req("GET", "/api/orders", None, {"X-User-Id": "1", "X-User-Role": "ADMIN", "X-User-Email": "a@b.c"})
    ok(c == 401, f"spoofed identity headers without token -> {c}")
    c, _ = req("GET", "/api/admin/orders", None, {"X-User-Id": "1", "X-User-Role": "ADMIN"})
    ok(c == 401, f"spoofed ADMIN header on admin route -> {c}")
    c, _ = req("GET", "/api/hotels", None, {"X-User-Role": "ADMIN", "X-User-Id": "1"})
    ok(c in (200, 404), f"public GET with spoofed headers still anonymous-ok ({c})")

    # 4. actuator surface
    for p in ["/actuator/env", "/actuator/heapdump", "/actuator/configprops", "/actuator/gateway/routes"]:
        c, _ = req("GET", p)
        ok(c in (401, 403, 404), f"{p} not exposed ({c})")

    # 5. XFF spoof must not bypass register limit (limit 5/min per IP)
    codes = []
    for i in range(9):
        c, _ = req("POST", "/api/auth/register", {"email": f"x{secrets.token_hex(4)}@example.com", "password": "Aa1!aaaaaaaa", "firstName": "a", "lastName": "b"},
                   {"X-Forwarded-For": f"198.51.100.{i + 1}"})
        codes.append(c)
    ok(429 in codes, f"XFF rotation still hits 429 on register: {codes}")
    print(f"\n{P} passed, {F} failed")
    sys.exit(1 if F else 0)


main()
