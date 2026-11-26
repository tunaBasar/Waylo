#!/usr/bin/env python3
"""Live acceptance test for hotel photos (hotel-service V11, contract sec. 5) through the gateway.

Needs a running stack, the gateway admin route for /api/admin/hotel-media (see report) and an admin account:
  WAYLO_ADMIN_EMAIL / WAYLO_ADMIN_PASSWORD
Standard library only. Images are generated in memory (valid PNG), nothing is read from disk.
"""
import json, os, secrets, struct, subprocess, sys, time, uuid, zlib
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = os.environ.get("WAYLO_BASE_URL", "http://localhost:8080")
T = 20
P = F = 0
failures = []


import sys as _sys, pathlib as _pl
_sys.path.insert(0, str(_pl.Path(__file__).resolve().parent))
import org_cleanup  # noqa: E402
TEST_ORGS, TEST_EMAILS = [], []


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print(f"PASS: {label}")
    else:
        F += 1
        failures.append(label)
        print(f"FAIL: {label}")


def raw(method, path, body=None, token=None, headers=None):
    h = dict(headers or {})
    if token:
        h["Authorization"] = f"Bearer {token}"
    r = Request(BASE_URL + path, data=body, headers=h, method=method)
    try:
        with urlopen(r, timeout=T) as resp:
            return resp.status, resp.read(), resp.headers
    except HTTPError as e:
        return e.code, e.read(), e.headers


def js(method, path, obj=None, token=None):
    data = json.dumps(obj).encode() if obj is not None else None
    s, b, h = raw(method, path, data, token, {"Content-Type": "application/json"})
    return s, (json.loads(b) if b else None)


def png(w, h):
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    row = b"\x00" + b"\x80\x40\xc0" * w
    data = zlib.compress(row * h, 9)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", data) + chunk(b"IEND", b"")


def multipart(fname, content, ctype, caption=None):
    b = "----" + uuid.uuid4().hex
    parts = []
    if caption:
        parts.append(f'--{b}\r\nContent-Disposition: form-data; name="caption"\r\n\r\n{caption}\r\n'.encode())
    parts.append(f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n'.encode()
                 + content + b"\r\n")
    parts.append(f"--{b}--\r\n".encode())
    return b"".join(parts), {"Content-Type": f"multipart/form-data; boundary={b}"}


def upload(token, hotel, fname, content, ctype="image/png", caption=None):
    body, h = multipart(fname, content, ctype, caption)
    s, b, _ = raw("POST", f"/api/hotels/{hotel}/media", body, token, h)
    return s, (json.loads(b) if b else None)


def register(n):
    email = f"media{uuid.uuid4().hex}@example.test"
    TEST_EMAILS.append(email)
    pw = "Pw7" + secrets.token_urlsafe(16)
    s, d = js("POST", "/api/auth/register", {"firstName": "T", "lastName": f"U{n}", "email": email, "password": pw})
    assert s == 200, f"register {s}"
    subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", "auth_db", "-tAc",
                    f"UPDATE users SET email_verified = true WHERE email = '{email}'"], check=True, capture_output=True)
    time.sleep(13)
    s, d = js("POST", "/api/auth/login", {"email": email, "password": pw})
    assert s == 200, f"login {s}"
    return d["data"]["token"]


def env_file_value(key):
    """Value of KEY from the repo .env (never printed). Used when the env var itself is not set."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if "=" in line and not line.lstrip().startswith("#"):
                    k, v = line.split("=", 1)
                    if k.strip() == key:
                        return v.strip().strip('"').strip("'")
    except OSError:
        pass
    return None


def main():
    admin_email = os.environ.get("WAYLO_ADMIN_EMAIL") or env_file_value("WAYLO_BOOTSTRAP_ADMIN_EMAIL")
    admin_pw = os.environ.get("WAYLO_ADMIN_PASSWORD") or env_file_value("WAYLO_BOOTSTRAP_ADMIN_PASSWORD")
    if not admin_email or not admin_pw:
        print("SKIP: set WAYLO_ADMIN_EMAIL and WAYLO_ADMIN_PASSWORD (or the bootstrap admin in .env)")
        return 2
    s, d = js("POST", "/api/auth/login", {"email": admin_email, "password": admin_pw})
    assert s == 200, f"admin login {s}"
    admin = d["data"]["token"]

    ta, tb = register("a"), register("b")
    s, d = js("POST", "/api/organizations", {"name": "Media Org A", "businessType": "HOTEL", "legalType": "COMPANY"}, ta)
    org_a = d["data"]["id"]
    TEST_ORGS.append(org_a)
    js("POST", f"/api/admin/organizations/{org_a}/approve", {}, admin)
    s, d = js("POST", "/api/hotels", {"name": "Media Hotel", "city": "IST", "country": "TR", "address": "St1",
                                      "starRating": 4, "organizationId": org_a}, ta)
    ok(s == 201, f"hotel created {s}")
    hotel = d["data"]["id"]

    good = png(800, 600)
    s, d = upload(ta, hotel, "../../evil.php", good, "text/plain", "Lobi")
    ok(s == 201, f"valid PNG accepted whatever the file name/content type says ({s})")
    mid = d["data"]["id"]
    ok(d["data"]["reviewStatus"] == "PENDING" and d["data"]["scanStatus"] == "CLEAN", "new photo is PENDING review")

    s, d = upload(ta, hotel, "x.jpg", b"%PDF-1.4 not an image", "image/jpeg")
    ok(s == 415, f"fake extension (PDF bytes named .jpg) rejected 415 ({s})")
    s, d = upload(ta, hotel, "small.png", png(100, 100))
    ok(s == 400 and d.get("error") == "IMAGE_TOO_SMALL", f"too small rejected ({s})")
    s, d = upload(ta, hotel, "big.png", good + b"\x00" * (5 * 1024 * 1024))
    ok(s in (413, 400), f"over 5 MB rejected ({s})")
    s, d = upload(tb, hotel, "o.png", good)
    ok(s in (403, 404), f"other organization cannot upload ({s})")

    s, b, _ = raw("GET", f"/api/hotels/{hotel}/media/{mid}")
    ok(s == 404, f"public file 404 while PENDING + hotel unpublished ({s})")
    s, b, h = raw("GET", f"/api/hotels/{hotel}/media/{mid}/file", None, ta)
    ok(s == 200 and b[:4] == b"\x89PNG", f"partner reads own file ({s})")
    s, b, _ = raw("GET", f"/api/hotels/{hotel}/media/{mid}/file", None, tb)
    ok(s in (403, 404), f"other partner cannot read file ({s})")
    s, b, _ = raw("GET", f"/api/hotels/{hotel}/media/{mid}/file")
    ok(s in (401, 403), f"anonymous cannot read partner file ({s})")

    s, d = js("GET", f"/api/hotels/{hotel}/media/mine", None, ta)
    ok(s == 200 and len(d["data"]) == 1, "partner lists own media")
    s, d = js("PATCH", f"/api/hotels/{hotel}/media/{mid}", {"caption": "Resepsiyon"}, ta)
    ok(s == 200 and d["data"]["caption"] == "Resepsiyon", "caption updated")
    s, d = upload(ta, hotel, "b.png", good)
    mid2 = d["data"]["id"]
    s, d = js("PUT", f"/api/hotels/{hotel}/media/order", {"ids": [mid2, mid]}, ta)
    ok(s == 200 and [x["id"] for x in d["data"]] == [mid2, mid], "reorder")

    # admin review
    s, d = js("GET", "/api/admin/hotel-media?reviewStatus=PENDING", None, admin)
    ok(s == 200 and any(i["media"]["id"] == mid for i in d["data"]["items"]), "admin sees pending photo")
    s, d = js("GET", "/api/admin/hotel-media?reviewStatus=PENDING", None, ta)
    ok(s in (401, 403), f"partner cannot use admin review ({s})")
    s, d = js("POST", f"/api/admin/hotel-media/{mid2}/decisions", {"decision": "REJECT", "reason": "x"}, admin)
    ok(s == 400, f"reject needs a reason ({s})")
    s, d = js("POST", f"/api/admin/hotel-media/{mid2}/decisions", {"decision": "REJECT", "reason": "Dusuk kalite"}, admin)
    ok(s == 200 and d["data"]["media"]["reviewStatus"] == "REJECTED" and len(d["data"]["decisions"]) == 1, "reject with reason, audit row written")
    s, d = js("POST", f"/api/admin/hotel-media/{mid}/decisions", {"decision": "APPROVE"}, admin)
    ok(s == 200 and d["data"]["media"]["reviewStatus"] == "APPROVED", "approve")

    # approved but hotel not PUBLISHED -> still 404
    s, b, _ = raw("GET", f"/api/hotels/{hotel}/media/{mid}")
    ok(s == 404, f"approved photo of unpublished hotel is 404 ({s})")

    # publish the hotel: publication needs one room type, rate plan and inventory row (HOTEL_INCOMPLETE otherwise)
    s, d = js("POST", f"/api/hotels/{hotel}/room-types", {"code": "MD_DBL", "name": "Media double",
                                                         "maxAdults": 2, "maxChildren": 0, "maxOccupancy": 2}, ta)
    ok(s == 201, f"room type created ({s})")
    room = d["data"]["id"]
    s, d = js("POST", f"/api/hotels/{hotel}/rate-plans", {"code": "MD_FLEX", "name": "Media flex", "mealPlan": "BREAKFAST",
                                                         "cancellationPolicy": "FREE_CANCELLATION",
                                                         "freeCancellationDaysBefore": 2}, ta)
    ok(s == 201, f"rate plan created ({s})")
    night = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 40 * 86400))
    s, d = js("PUT", f"/api/hotels/{hotel}/inventory", {"items": [{"roomTypeId": room, "stayDate": night, "allotment": 1,
                                                                  "expectedVersion": None}],
                                                       "reason": "media acceptance fixture"}, ta)
    ok(s == 200, f"inventory set ({s})")
    s, d = js("POST", f"/api/hotels/{hotel}/publication-requests", {}, ta)
    ok(s in (200, 201), f"submit publication ({s})")
    req = d.get("data") or {}
    s, d = js("POST", f"/api/admin/hotel-publications/{req.get('id')}/decisions",
              {"decision": "APPROVE_AND_PUBLISH", "reason": None, "expectedVersion": req.get("version", 0)}, admin)
    ok(s == 200, f"hotel published ({s})")

    s, b, h = raw("GET", f"/api/hotels/{hotel}/media/{mid}")
    ok(s == 200 and b[:4] == b"\x89PNG", f"approved photo of published hotel served ({s})")
    ok("max-age=300" in (h.get("Cache-Control") or "") and "public" in (h.get("Cache-Control") or ""), "Cache-Control public, max-age=300")
    s, b, _ = raw("GET", f"/api/hotels/{hotel}/media/{mid2}")
    ok(s == 404, f"rejected photo is 404 even when published ({s})")
    s, d = js("GET", f"/api/hotels/{hotel}/media")
    ok(s == 200 and [x["id"] for x in d["data"]] == [mid], "public list has approved photo only")

    s, _ = js("DELETE", f"/api/hotels/{hotel}/media/{mid}", None, ta)
    ok(s == 409, f"live approved photo cannot be deleted ({s})")
    s, _ = js("DELETE", f"/api/hotels/{hotel}/media/{mid2}", None, ta)
    ok(s == 204, f"rejected photo can be deleted ({s})")

    print(f"\n{P} passed, {F} failed")
    for f in failures:
        print("  -", f)
    return 1 if F else 0


if __name__ == "__main__":
    try:
        code = main()
    finally:
        org_cleanup.cleanup_test_orgs(TEST_ORGS, TEST_EMAILS)
    sys.exit(code)
