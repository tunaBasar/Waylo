#!/usr/bin/env python3
"""Live acceptance test for media phase 3 (hotel-service V13): photos attached to a publication request.

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


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print(f"PASS: {label}")
    else:
        F += 1
        failures.append(label)
        print(f"FAIL: {label}")


def public_media_ids(hotel, expected):
    """hotel-service caches the active-organization set for up to 30s (hotel.public-projection.org-cache-ttl-seconds),
    so a hotel of a just-approved organization can stay non-public for one cache period. Poll like
    test-public-projection.py instead of sleeping blindly; returns the last observed public id list."""
    deadline = time.time() + 40
    while True:
        s, d = js("GET", f"/api/hotels/{hotel}/media")
        ids = sorted(x["id"] for x in d["data"]) if s == 200 and d else None
        if ids == sorted(expected) or time.time() > deadline:
            return ids
        time.sleep(3)


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
    email = f"mpub{uuid.uuid4().hex}@example.test"
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


def psql(db, sql):
    r = subprocess.run(["docker", "exec", "waylo-postgres", "psql", "-U", "waylo", "-d", db, "-tAc", sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ""


def cleanup(hotels):
    """FK order, link rows first. Best effort: a failing statement is skipped."""
    ids = ",".join(f"'{h}'" for h in hotels)
    if not ids:
        return
    for sql in [
        f"DELETE FROM hotel_publication_request_media WHERE hotel_id IN ({ids})",
        f"DELETE FROM hotel_media_decisions WHERE hotel_id IN ({ids})",
        f"DELETE FROM hotel_media WHERE hotel_id IN ({ids})",
        f"DELETE FROM hotel_publication_decisions WHERE hotel_id IN ({ids})",
        f"UPDATE hotels SET published_revision_id = NULL WHERE id IN ({ids})",
        f"DELETE FROM hotel_publication_requests WHERE hotel_id IN ({ids})",
        f"DELETE FROM hotel_content_revisions WHERE hotel_id IN ({ids})",
        f"DELETE FROM daily_inventory WHERE hotel_id IN ({ids})",
        f"DELETE FROM daily_rates WHERE hotel_id IN ({ids})",
        f"DELETE FROM rate_plans WHERE hotel_id IN ({ids})",
        f"DELETE FROM room_types WHERE hotel_id IN ({ids})",
        f"DELETE FROM hotels WHERE id IN ({ids})",
    ]:
        psql("hotel_db", sql)


def decide(admin, req, decision, reason=None):
    return js("POST", f"/api/admin/hotel-publications/{req['id']}/decisions",
              {"decision": decision, "reason": reason, "expectedVersion": req["version"]}, admin)


def main():
    admin_email = os.environ.get("WAYLO_ADMIN_EMAIL") or env_file_value("WAYLO_BOOTSTRAP_ADMIN_EMAIL")
    admin_pw = os.environ.get("WAYLO_ADMIN_PASSWORD") or env_file_value("WAYLO_BOOTSTRAP_ADMIN_PASSWORD")
    if not admin_email or not admin_pw:
        print("SKIP: set WAYLO_ADMIN_EMAIL and WAYLO_ADMIN_PASSWORD (or the bootstrap admin in .env)")
        return 2
    s, d = js("POST", "/api/auth/login", {"email": admin_email, "password": admin_pw})
    assert s == 200, f"admin login {s}"
    admin = d["data"]["token"]

    ta = register("a")
    s, d = js("POST", "/api/organizations", {"name": "MediaPub Org", "businessType": "HOTEL", "legalType": "COMPANY"}, ta)
    org = d["data"]["id"]
    js("POST", f"/api/admin/organizations/{org}/approve", {}, admin)
    hotels = []
    try:
        s, d = js("POST", "/api/hotels", {"name": "MediaPub Hotel", "city": "IST", "country": "TR", "address": "St1",
                                          "starRating": 4, "organizationId": org}, ta)
        ok(s == 201, f"hotel created {s}")
        hotel = d["data"]["id"]
        hotels.append(hotel)
        s, d = js("POST", "/api/hotels", {"name": "MediaPub Other", "city": "IST", "country": "TR", "address": "St2",
                                          "starRating": 3, "organizationId": org}, ta)
        other = d["data"]["id"]
        hotels.append(other)

        s, d = js("POST", f"/api/hotels/{hotel}/room-types", {"code": "MP_DBL", "name": "MP double",
                                                             "maxAdults": 2, "maxChildren": 0, "maxOccupancy": 2}, ta)
        room = d["data"]["id"]
        js("POST", f"/api/hotels/{hotel}/rate-plans", {"code": "MP_FLEX", "name": "MP flex", "mealPlan": "BREAKFAST",
                                                      "cancellationPolicy": "FREE_CANCELLATION",
                                                      "freeCancellationDaysBefore": 2}, ta)
        night = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 40 * 86400))
        s, d = js("PUT", f"/api/hotels/{hotel}/inventory", {"items": [{"roomTypeId": room, "stayDate": night, "allotment": 1,
                                                                      "expectedVersion": None}],
                                                           "reason": "media publication fixture"}, ta)
        ok(s == 200, f"inventory set ({s})")

        img = png(800, 600)
        ids = []
        for n in ("p1", "p2"):
            s, d = upload(ta, hotel, n + ".png", img)
            ok(s == 201, f"upload {n} ({s})")
            ids.append(d["data"]["id"])
        p1, p2 = ids
        s, d = upload(ta, other, "o.png", img)
        foreign = d["data"]["id"]

        # negatives that run before the INITIAL submit
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests", {"mediaIds": [foreign]}, ta)
        ok(s == 404 and d.get("error") == "MEDIA_NOT_FOUND", f"photo of another hotel -> 404 MEDIA_NOT_FOUND ({s})")
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests", {"mediaIds": [str(uuid.uuid4()) for _ in range(21)]}, ta)
        ok(s == 400 and d.get("error") == "VALIDATION_ERROR", f"21 ids -> 400 ({s})")
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests", {"mediaIds": [p1, p1]}, ta)
        ok(s == 400, f"duplicate ids -> 400 ({s})")

        # INITIAL with [p1, p2]
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests", {"mediaIds": [p1, p2]}, ta)
        ok(s == 201 and sorted(d["data"]["mediaIds"]) == sorted([p1, p2]), f"INITIAL submit carries both photos ({s})")
        req = d["data"]
        s, d = js("GET", f"/api/hotels/{hotel}/publication-requests", None, ta)
        ok(s == 200 and d["data"][0]["mediaCount"] == 2, "partner history mediaCount=2")
        s, d = js("GET", f"/api/admin/hotel-publications/{req['id']}", None, admin)
        m = d["data"]["media"]
        ok(s == 200 and len(m) == 2 and all(x["reviewStatus"] == "PENDING" for x in m), "admin detail media: 2 PENDING")
        s, d = js("GET", "/api/admin/hotel-publications?status=SUBMITTED", None, admin)
        ok(s == 200 and any(i["requestId"] == req["id"] and i["mediaCount"] == 2 for i in d["data"]["items"]),
           "admin list mediaCount=2")

        s, d = js("POST", f"/api/admin/hotel-media/{p2}/decisions", {"decision": "REJECT", "reason": "Dusuk kalite"}, admin)
        ok(s == 200 and d["data"]["media"]["reviewStatus"] == "REJECTED", "admin rejects p2 per photo")
        s, d = decide(admin, req, "APPROVE_AND_PUBLISH")
        ok(s == 200, f"APPROVE_AND_PUBLISH ({s})")
        s, d = js("GET", f"/api/hotels/{hotel}/media/mine", None, ta)
        st = {x["id"]: x["reviewStatus"] for x in d["data"]}
        ok(st.get(p1) == "APPROVED" and st.get(p2) == "REJECTED", "p1 APPROVED, p2 stays REJECTED")
        ok(public_media_ids(hotel, [p1]) == [p1], "public list has p1 only")

        # CONTENT_CHANGE
        s, d = upload(ta, hotel, "p3.png", img)
        p3 = d["data"]["id"]
        s, d = js("GET", f"/api/hotels/{hotel}/content", None, ta)
        cv = d["data"]["contentVersion"]
        s, d = js("PATCH", f"/api/hotels/{hotel}/content", {"expectedVersion": cv, "content": {"description": "Yeni aciklama"}}, ta)
        ok(s == 200, f"content revision created ({s})")
        rev = d["data"]
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests",
                  {"revisionId": rev["id"], "expectedVersion": rev["version"], "mediaIds": [p1]}, ta)
        ok(s == 409 and d.get("error") == "MEDIA_NOT_PENDING" and d.get("details", {}).get("reviewStatus") == "APPROVED",
           f"APPROVED photo -> 409 MEDIA_NOT_PENDING ({s})")
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests",
                  {"revisionId": rev["id"], "expectedVersion": rev["version"], "mediaIds": [p3]}, ta)
        ok(s == 201 and d["data"]["kind"] == "CONTENT_CHANGE" and d["data"]["mediaIds"] == [p3], f"CONTENT_CHANGE carries p3 ({s})")
        req = d["data"]
        s, d = decide(admin, req, "REJECT", "Aciklama uygun degil")
        ok(s == 200, f"REJECT content change ({s})")
        s, d = js("GET", f"/api/hotels/{hotel}/media/mine", None, ta)
        ok({x["id"]: x["reviewStatus"] for x in d["data"]}.get(p3) == "PENDING", "p3 stays PENDING after REJECT")

        s, d = js("PATCH", f"/api/hotels/{hotel}/content", {"expectedVersion": cv, "content": {"description": "Duzeltilmis aciklama"}}, ta)
        rev = d["data"]
        s, d = js("POST", f"/api/hotels/{hotel}/publication-requests",
                  {"revisionId": rev["id"], "expectedVersion": rev["version"], "mediaIds": [p3]}, ta)
        ok(s == 201 and d["data"]["mediaIds"] == [p3], f"new request with p3 accepted ({s})")
        req = d["data"]
        s, d = decide(admin, req, "APPROVE")
        ok(s == 200, f"APPROVE content change ({s})")
        ok(public_media_ids(hotel, [p1, p3]) == sorted([p1, p3]), "public list has p1 and p3")
    finally:
        cleanup(hotels)

    print(f"\n{P}/{P + F} passed")
    for f in failures:
        print("  -", f)
    return 1 if F else 0


if __name__ == "__main__":
    sys.exit(main())
