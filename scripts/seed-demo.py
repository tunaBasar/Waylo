#!/usr/bin/env python3
"""Rolling, idempotent demo data driven by the automation rules (INVENTORY-AUTOMATION-CONTRACT.md). Run it as often
as you like (daily is fine): nothing is written row by row any more, the rules open the dates (and the server also
applies them every night).

  python3 scripts/seed-demo.py [--dry-run] [--only hotels|flights|cars] [--hide-others]

  hotels   : a dedicated demo PARTNER user (SEED_HOTEL_PARTNER_EMAIL, default seed-otel@waylo.test) owns the org
             "Waylo Demo Hotels" and does every hotel write. The admin only approves the org and decides the publication
             (an admin cannot review an organization they belong to: 409 SELF_REVIEW_NOT_ALLOWED).
             Per hotel: two room types, one rate plan, then via /api/hotels/{id}/automation: an inventory rule per room
             type (5 rooms, SEED_HOTEL_DAYS horizon, default 365), one default rate rule per (room type, rate plan)
             (base occupancy 2 at the 2-person price, extra/reduced person amounts, weekend base amount), then
             reset (today .. today + horizon, this org's hotels only) and apply. Existing rules are updated (PUT), never
             duplicated. No daily_inventory / daily_rates rows are written by hand.
  cars     : runs seed-car-partner.py (generates SEED_CAR_PARTNER_PASSWORD into .env when missing).
  flights  : airports, then ONE recurring schedule per route (code WD<route>, every day, horizon SEED_FLIGHT_DAYS, default
             60) via /api/flights/schedules, then /api/flights/schedules/apply; flights and seats come from the server
             (flightCreated event). validFrom is the day after the last old-style flight WD<route><day-of-year> of that
             route (or today), so no departure exists twice. Existing schedules are updated only when fields differ.
             Reuses the existing flight seller org ('Waylo Ucus Satis' / FLIGHT_SELLER_ORGANIZATION_ID) and ensures its
             FLIGHT commission rate.
  --hide-others (opt-in): SUSPENDs other PUBLISHED hotels, never E2E/PubProj/Media/Cancel test fixtures, hotels
             created in the last 2 hours or legacy hotels without an organization.

Credentials come from the environment or .env, never printed. Generated passwords are appended to .env (mode kept).
SEED_BASE (default http://localhost:8080), SEED_HOTEL_DAYS (365, 30..730), SEED_FLIGHT_DAYS (60, 1..120),
SEED_FLIGHT_COMMISSION (0.0800).
"""
import argparse
import datetime
import json
import os
import pathlib
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zoneinfo

BASE = os.environ.get('SEED_BASE', 'http://localhost:8080')
ROOT = pathlib.Path(__file__).resolve().parent.parent
HOTEL_DAYS = max(30, min(int(os.environ.get('SEED_HOTEL_DAYS', '365')), 730))
FLIGHT_DAYS = max(1, min(int(os.environ.get('SEED_FLIGHT_DAYS', '60')), 120))
FLIGHT_COMMISSION = float(os.environ.get('SEED_FLIGHT_COMMISSION', '0.0800'))
HOTEL_COMMISSION = float(os.environ.get('SEED_HOTEL_COMMISSION', '0.1000'))
HOTEL_ORG = 'Waylo Demo Hotels'
FLIGHT_ORG = 'Waylo Ucus Satis'
TZ = zoneinfo.ZoneInfo('Europe/Istanbul')
PROTECTED_PREFIXES = ('E2E', 'PubProj', 'Media', 'Cancel test')
DRY = False

# name, city, country, address, stars, description, base price per night for 2 guests (TRY)
DEMO_HOTELS = [
    ('Waylo Grand Istanbul', 'Istanbul', 'Turkey', 'Beyoglu, Istiklal Caddesi 42', 5, 'Bogaz manzarali luks otel, spa ve havuzlu', 4200),
    ('Waylo Sultanahmet Suites', 'Istanbul', 'Turkey', 'Sultanahmet, Divanyolu 18', 4, 'Tarihi yarimadada butik suit otel', 2600),
    ('Waylo Boutique Antalya', 'Antalya', 'Turkey', 'Kaleici, Hesapci Sokak 8', 4, "Tarihi Kaleici'de butik otel", 2300),
    ('Waylo Resort Bodrum', 'Bodrum', 'Turkey', 'Torba Mahallesi, Sahil Yolu 15', 5, 'Ege kiyisinda her sey dahil resort', 5200),
    ('Waylo Ankara Business', 'Ankara', 'Turkey', 'Kizilay, Ataturk Bulvari 120', 4, 'Sehir merkezinde is oteli', 1700),
    ('Waylo Kordon Izmir', 'Izmir', 'Turkey', 'Alsancak, Kordon Boyu 55', 3, "Kordon'da ekonomik konaklama", 1300),
    ('Waylo Cave Suites Kapadokya', 'Nevsehir', 'Turkey', 'Goreme, Muze Yolu 3', 5, 'Peri bacalari manzarali magara otel', 3800),
    ('Waylo Lodge Uzungol', 'Trabzon', 'Turkey', 'Uzungol Mahallesi, Gol Kenari 1', 3, 'Gol kenarinda huzurlu dagevi', 1500),
]
ROOM_TYPES = [  # code, name, maxAdults, maxChildren, maxOccupancy, price multiplier
    ('DEMO_STD', 'Standart oda', 2, 1, 3, 1.0),
    ('DEMO_FAM', 'Aile odasi', 4, 2, 4, 1.6),
]
OCC_FACTOR = {1: 0.8, 2: 1.0, 3: 1.25, 4: 1.45}
ALLOTMENT = 5
RATE_PLAN = ('DEMO_FLEX', 'Kahvalti dahil, esnek iptal')

AIRPORTS = [
    ('IST', 'Istanbul Havalimani', 'Istanbul', 'Turkiye', 'Europe/Istanbul', 41.2753, 28.7519),
    ('SAW', 'Sabiha Gokcen Havalimani', 'Istanbul', 'Turkiye', 'Europe/Istanbul', 40.8986, 29.3092),
    ('ESB', 'Esenboga Havalimani', 'Ankara', 'Turkiye', 'Europe/Istanbul', 40.1281, 32.9951),
    ('ADB', 'Adnan Menderes Havalimani', 'Izmir', 'Turkiye', 'Europe/Istanbul', 38.2924, 27.1570),
    ('AYT', 'Antalya Havalimani', 'Antalya', 'Turkiye', 'Europe/Istanbul', 36.8987, 30.8005),
    ('BJV', 'Milas-Bodrum Havalimani', 'Bodrum', 'Turkiye', 'Europe/Istanbul', 37.2506, 27.6643),
]
# route digit, from, to, hour, minute, duration min, economy, business
ROUTES = [
    (1, 'IST', 'ESB', 8, 30, 70, 1250, 3400), (2, 'ESB', 'IST', 18, 0, 70, 1300, 3500),
    (3, 'SAW', 'ADB', 7, 15, 65, 1100, 3000), (4, 'ADB', 'SAW', 19, 20, 65, 1100, 3000),
    (5, 'IST', 'AYT', 11, 45, 85, 1650, 4200), (6, 'AYT', 'IST', 15, 30, 85, 1700, 4300),
    (7, 'IST', 'BJV', 9, 10, 80, 1800, 4500), (8, 'BJV', 'IST', 17, 40, 80, 1800, 4500),
]


def env(key):
    if os.environ.get(key):
        return os.environ[key]
    f = ROOT / '.env'
    if f.exists():
        for line in f.read_text().splitlines():
            if line.startswith(key + '='):
                return line.split('=', 1)[1].strip().strip('"').strip("'")
    return ''


def ensure_secret(key):
    """Env/.env value, else generate one and append it to .env (append keeps the mode). Never prints the value."""
    v = env(key)
    if v or DRY:
        return v
    v = secrets.token_urlsafe(18) + 'a1'
    f = ROOT / '.env'
    text = f.read_text() if f.exists() else ''
    with f.open('a') as fh:
        fh.write(('' if not text or text.endswith('\n') else '\n') + f'{key}={v}\n')
    os.environ[key] = v
    print(f'  (new password written to .env)')
    return v


def psql(db, sql):
    r = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def q(v):
    return str(v).replace("'", "''")


def call(method, path, body=None, token=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, method=method, headers=h), timeout=60) as r:
            b = r.read()
            return r.status, json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except ValueError:
            return e.code, {}


def must(method, path, body=None, token=None, ok=(200, 201)):
    s, b = call(method, path, body, token)
    if s not in ok:
        sys.exit(f'HATA [{s}] {method} {path} {str(b)[:200]}')
    return b.get('data')


def login(email, pw):
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': pw})
    return b['data']['token'] if s == 200 else None


def partner_creds():
    return env('SEED_HOTEL_PARTNER_EMAIL') or 'seed-otel@waylo.test', ensure_secret('SEED_HOTEL_PARTNER_PASSWORD')


def ensure_partner():
    """Login, else register + mark verified (pattern of seed-car-partner.py). Returns token, or None in a dry run."""
    email, pw = partner_creds()
    if not pw:
        print('  [dry] SEED_HOTEL_PARTNER_PASSWORD uretilip .env dosyasina yazilacak')
        return None
    tok = login(email, pw)
    if tok:
        return tok
    if DRY:
        print(f'  [dry] otel partner kullanicisi kayit edilecek: {email}')
        return None
    s, _ = call('POST', '/api/auth/register', {'firstName': 'Otel', 'lastName': 'Partner', 'email': email, 'password': pw})
    if s not in (200, 201):
        sys.exit(f'HATA [{s}] partner register (kullanici var ama sifre uyusmuyor olabilir)')
    subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', 'auth_db', '-tAc',
                    f"UPDATE users SET email_verified = true WHERE email = '{q(email)}'"], check=True, capture_output=True)
    time.sleep(13)  # only after a NEW registration
    tok = login(email, pw)
    if not tok:
        sys.exit('HATA partner login')
    print(f'  otel partner kullanicisi olusturuldu: {email}')
    return tok


def find_org(name, token):
    for m in call('GET', '/api/me/memberships', token=token)[1].get('data') or []:
        if m.get('organizationName') == name and m.get('organizationStatus') in ('ACTIVE', 'PENDING'):
            return m
    return None


def ensure_org(name, partner, admin):
    """The partner owns the org, the admin only approves it."""
    m = find_org(name, partner)
    if m:
        if m['organizationStatus'] == 'PENDING':
            call('POST', f'/api/admin/organizations/{m["organizationId"]}/approve', token=admin)
        return m['organizationId']
    org = must('POST', '/api/organizations', {'name': name, 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, partner)['id']
    call('POST', f'/api/admin/organizations/{org}/approve', token=admin)
    print(f'  organizasyon olusturuldu: {name}')
    return org


def publish(hotel_id, partner, admin):
    s, b = call('POST', f'/api/hotels/{hotel_id}/publication-requests', {}, partner)
    if s in (200, 201):
        rid, ver = b['data']['id'], b['data'].get('version', 0)
    else:  # an open request may already exist: decide that one
        page = call('GET', f'/api/admin/hotel-publications?status=SUBMITTED&hotelId={hotel_id}', token=admin)[1].get('data') or {}
        items = page.get('items') or []
        if not items:
            print(f'  [!] yayin istegi {hotel_id}: {s}')
            return False
        rid, ver = items[0]['requestId'], items[0]['version']
    s, _ = call('POST', f'/api/admin/hotel-publications/{rid}/decisions',
                {'decision': 'APPROVE_AND_PUBLISH', 'expectedVersion': ver}, admin)
    return s == 200


def ensure_commission(vertical, org, rate, admin):
    """Without a commission rate the order can be created but payment fails (COMMISSION_RATE_MISSING)."""
    rates = call('GET', '/api/admin/commission-rates', token=admin)[1].get('data') or []
    if any(r.get('vertical') == vertical and str(r.get('supplierOrganizationId')) == str(org) for r in rates):
        return
    print(f'  {vertical} komisyon orani {rate} {"eklenecek" if DRY else "eklendi"}')
    if not DRY:
        must('POST', '/api/admin/commission-rates', {'vertical': vertical, 'supplierOrganizationId': org, 'rate': rate}, admin)


def as_list(data):
    if isinstance(data, dict):
        data = data.get('items') or data.get('content') or []
    return data or []


def num(v):
    return None if v is None else round(float(v), 2)


def rate_rule_body(rt, plan, tname, base, mult, max_occ):
    """Default rate rule from the old per-occupancy prices: OCC_FACTOR 0.8/1.0/1.25 -> reduced = 0.2 * 2p price,
    extra = 0.25 * 2p price; weekend factor 1.15 -> weekendBaseAmount."""
    b2 = base * mult
    return {'roomTypeId': rt, 'ratePlanId': plan, 'name': f'{tname} varsayilan fiyat', 'validFrom': None, 'validTo': None,
            'currency': 'TRY', 'baseOccupancy': min(2, max_occ), 'baseAmount': round(b2, -1),
            'weekendBaseAmount': round(b2 * 1.15, -1),
            'extraPersonAmount': round(b2 * (OCC_FACTOR[3] - OCC_FACTOR[2]), -1),
            'reducedPersonAmount': round(b2 * (OCC_FACTOR[2] - OCC_FACTOR[1]), -1), 'active': True}


RATE_KEYS = ('name', 'currency', 'baseOccupancy', 'baseAmount', 'weekendBaseAmount', 'extraPersonAmount',
             'reducedPersonAmount', 'active')


def same_rule(old, body):
    for k in RATE_KEYS:
        a, b = old.get(k), body[k]
        if isinstance(b, float) or isinstance(a, (float, int)) and not isinstance(a, bool) and k.endswith('Amount'):
            a, b = num(a), num(b)
        if a != b:
            return False
    return True


def seed_hotels(admin):
    partner = ensure_partner()
    today = datetime.datetime.now(TZ).date()
    if not partner:
        print(f'hotels : [dry] org "{HOTEL_ORG}", {len(DEMO_HOTELS)} otel, {len(ROOM_TYPES)} oda tipi, {HOTEL_DAYS} gun icin stok ve fiyat kurali olusturulacak')
        return
    if DRY and not find_org(HOTEL_ORG, partner):
        print(f'hotels : [dry] org "{HOTEL_ORG}" olusturulacak, {len(DEMO_HOTELS)} otel, {HOTEL_DAYS} gun icin stok ve fiyat kurali')
        return
    org = find_org(HOTEL_ORG, partner)['organizationId'] if DRY else ensure_org(HOTEL_ORG, partner, admin)
    ensure_commission('HOTEL', org, HOTEL_COMMISSION, admin)
    if not DRY:
        partner = login(*partner_creds())  # fresh token after org creation
    existing = {h['name']: h for h in (call('GET', f'/api/hotels?organizationId={org}', token=partner)[1].get('data') or [])}
    new_h = inv_rules = rate_rules = 0
    total = {'created': 0, 'updated': 0, 'skippedExisting': 0, 'skippedManual': 0, 'skippedPast': 0}
    for name, city, country, addr, stars, desc, base in DEMO_HOTELS:
        h = existing.get(name)
        if not h:
            new_h += 1
            if DRY:
                print(f'  [dry] otel olusturulacak: {name} ({len(ROOM_TYPES)} stok kurali, {len(ROOM_TYPES)} fiyat kurali, sifirla+uygula, yayin)')
                continue
            h = must('POST', '/api/hotels', {'name': name, 'city': city, 'country': country, 'address': addr,
                                            'starRating': stars, 'description': desc, 'organizationId': org}, partner)
        hid = h['id']
        types = {t['code']: t for t in (call('GET', f'/api/hotels/{hid}/room-types', token=partner)[1].get('data') or [])}
        for code, tname, adults, kids, occ, _mult in ROOM_TYPES:
            if code not in types:
                if DRY:
                    continue
                types[code] = must('POST', f'/api/hotels/{hid}/room-types', {
                    'code': code, 'name': tname, 'maxAdults': adults, 'maxChildren': kids, 'maxOccupancy': occ}, partner)
        plans = {p['code']: p for p in (call('GET', f'/api/hotels/{hid}/rate-plans', token=partner)[1].get('data') or [])}
        if RATE_PLAN[0] not in plans and not DRY:
            plans[RATE_PLAN[0]] = must('POST', f'/api/hotels/{hid}/rate-plans', {
                'code': RATE_PLAN[0], 'name': RATE_PLAN[1], 'mealPlan': 'BREAKFAST',
                'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, partner)
        if DRY and (len(types) < len(ROOM_TYPES) or RATE_PLAN[0] not in plans):
            print(f'  [dry] {name}: eksik oda tipi/rate plan olusturulacak')
            continue
        plan = plans[RATE_PLAN[0]]['id']
        auto = call('GET', f'/api/hotels/{hid}/automation', token=partner)[1].get('data') or {}
        old_inv = {str(r['roomTypeId']): r for r in auto.get('inventoryRules') or []}
        old_rate = {(str(r['roomTypeId']), str(r['ratePlanId'])): r for r in auto.get('rateRules') or []
                    if not r.get('validFrom') and not r.get('validTo')}
        for code, tname, _a, _k, occ, mult in ROOM_TYPES:
            rt = types[code]['id']
            want = {'defaultAllotment': ALLOTMENT, 'horizonDays': HOTEL_DAYS, 'active': True}
            o = old_inv.get(str(rt))
            if not (o and all(o.get(k) == v for k, v in want.items())):
                inv_rules += 1
                if not DRY:
                    must('PUT', f'/api/hotels/{hid}/automation/inventory-rules/{rt}', want, partner)
            body = rate_rule_body(rt, plan, tname, base, mult, occ)
            o = old_rate.get((str(rt), str(plan)))
            if o and same_rule(o, body):
                continue
            rate_rules += 1
            if DRY:
                continue
            if o:  # idempotent re-run: update the rule found in GET /automation
                if o.get('version') is not None:
                    body['expectedVersion'] = o['version']
                must('PUT', f'/api/hotels/{hid}/automation/rate-rules/{o["id"]}', body, partner)
            else:
                must('POST', f'/api/hotels/{hid}/automation/rate-rules', body, partner)
        if not DRY:
            rng = {'from': today.isoformat(), 'to': (today + datetime.timedelta(days=HOTEL_DAYS)).isoformat()}
            for step, body in (('reset', rng), ('apply', None)):  # this org's own hotels only
                summ = must('POST', f'/api/hotels/{hid}/automation/{step}', body, partner) or {}
                for k in total:
                    total[k] += int(summ.get(k) or 0)
        status = h.get('publicationStatus')
        if status != 'PUBLISHED':
            if DRY:
                print(f'  [dry] {name}: yayinlanacak (durum {status})')
            elif status == 'SUSPENDED':
                call('POST', f'/api/admin/hotels/{hid}/publication-status',
                     {'decision': 'REINSTATE', 'reason': 'demo seed reinstate'}, admin)
            else:
                publish(hid, partner, admin)
    print(f'hotels : {"[dry] " if DRY else ""}+{new_h} otel, {inv_rules} stok kurali, {rate_rules} fiyat kurali yazildi/guncellendi ({HOTEL_DAYS} gun, {today} baslangic)')
    if not DRY:
        print(f'  sifirla+uygula: {total["created"]} olusturuldu, {total["updated"]} guncellendi, {total["skippedExisting"]} mevcut atlandi, '
              f'{total["skippedManual"]} manuel atlandi, {total["skippedPast"]} gecmis atlandi')


def hide_other_hotels(admin, demo_org=None):
    """Hide every public hotel that is not one of OUR demo hotels (same name in another org = old seed copy)."""
    demo = {h[0] for h in DEMO_HOTELS}
    now = datetime.datetime.now(datetime.timezone.utc)
    hidden = legacy = protected = 0
    for h in call('GET', '/api/hotels', token=admin)[1].get('data') or []:
        name = h.get('name') or ''
        if name in demo and str(h.get('organizationId') or '') == str(demo_org or ''):
            continue
        if h.get('publicationStatus') != 'PUBLISHED':
            continue
        created = None
        if h.get('createdAt'):
            try:
                created = datetime.datetime.fromisoformat(h['createdAt'].replace('Z', '+00:00'))
            except ValueError:
                pass
        if name.startswith(PROTECTED_PREFIXES) or (created and now - created < datetime.timedelta(hours=2)):
            protected += 1
            continue
        if not h.get('organizationId'):
            legacy += 1  # publication-status API records decisions against the org; legacy hotels are locked
            continue
        if DRY:
            hidden += 1
            continue
        s, _ = call('POST', f'/api/admin/hotels/{h["id"]}/publication-status',
                    {'decision': 'SUSPEND', 'reason': 'demo seed: test verisi gizlendi'}, admin)
        hidden += 1 if s == 200 else 0
    print(f'cleanup: {"[dry] " if DRY else ""}{hidden} otel gizlendi, {legacy} organizasyonsuz atlandi, {protected} korumali/yeni atlandi')


def seed_cars():
    # Never generate a password here: the car partner may already exist with another password (a new one cannot log
    # in and seed-car-partner.py exits 401). Without SEED_CAR_PARTNER_PASSWORD the step is skipped with a hint.
    if not env('SEED_CAR_PARTNER_PASSWORD'):
        print('cars   : atlandi (SEED_CAR_PARTNER_PASSWORD yok; mevcut filo varsa dokunulmaz)')
        return
    if DRY:
        print('cars   : [dry] seed-car-partner.py calistirilacak (idempotent)')
        return
    r = subprocess.run([sys.executable, str(ROOT / 'scripts' / 'seed-car-partner.py')], capture_output=True, text=True)
    print('cars   :', 'tamam' if r.returncode == 0 else 'HATA ' + r.stdout[-200:] + r.stderr[-200:])


def flight_seller_org():
    """Existing seller org: the .env id when it exists in auth_db, else the org named FLIGHT_ORG."""
    wanted = env('FLIGHT_SELLER_ORGANIZATION_ID')
    if wanted and psql('auth_db', f"SELECT id FROM organizations WHERE id = '{q(wanted)}'"):
        return wanted
    return psql('auth_db', f"SELECT id FROM organizations WHERE name = '{FLIGHT_ORG}' LIMIT 1") or None


WEEKDAYS = ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')


def schedule_differs(old, body):
    """Compare only the fields the response echoes; a missing field never counts as a difference."""
    for k, v in body.items():
        if k == 'cabins' or k not in old:
            continue
        a = sorted(old[k]) if k == 'weekdays' else old[k]
        b = sorted(v) if k == 'weekdays' else v
        if k == 'departureTime':
            a = str(a)[:5]
        if a != b:
            return True
    oc = {c.get('seatType'): c for c in old.get('cabins') or []}
    for c in body['cabins']:
        o = oc.get(c['seatType'])
        if not o or any(o.get(k) != c[k] for k in ('fromRow', 'toRow', 'letters')) or num(o.get('price')) != num(c['price']):
            return True
    return False


def seed_flights(admin):
    org = flight_seller_org()
    if not org:
        sys.exit(f'HATA: ucus satici organizasyonu yok ("{FLIGHT_ORG}"). Once scripts/test-flight-order.py calistirin.')
    rates = call('GET', '/api/admin/commission-rates', token=admin)[1].get('data') or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == org for r in rates):
        print(f'  FLIGHT komisyon orani {FLIGHT_COMMISSION} {"eklenecek" if DRY else "eklendi"}')
        if not DRY:
            must('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': org, 'rate': FLIGHT_COMMISSION}, admin)
    have = {(a.get('iataCode') or '').upper() for a in (call('GET', '/api/airports', token=admin)[1].get('data') or [])}
    for code, name, city, country, tz, lat, lon in AIRPORTS:
        if code not in have:
            print(f'  havalimani {"eklenecek" if DRY else "ekleniyor"}: {code}')
            if not DRY:
                must('POST', '/api/airports', {'iataCode': code, 'name': name, 'city': city, 'country': country,
                                               'timezone': tz, 'latitude': lat, 'longitude': lon}, admin)
    today = datetime.datetime.now(TZ).date()
    old_last = {}  # route digit -> last local departure date of old-style flights WD<route><day-of-year>
    all_flights, fpage = [], 0
    while True:  # /api/flights is paged (max size 200)
        chunk = as_list(call('GET', f'/api/flights?size=200&page={fpage}', token=admin)[1].get('data'))
        all_flights += chunk
        if len(chunk) < 200:
            break
        fpage += 1
    for f in all_flights:
        m = re.fullmatch(r'WD(\d)(\d{3})', f.get('flightNumber') or '')
        if m and f.get('departureTime'):
            try:
                d = datetime.datetime.fromisoformat(f['departureTime'].replace('Z', '+00:00')).astimezone(TZ).date()
            except ValueError:
                continue
            old_last[int(m.group(1))] = max(old_last.get(int(m.group(1)), d), d)
    scheds = {x.get('code'): x for x in as_list(call('GET', '/api/flights/schedules', token=admin)[1].get('data'))}
    created = updated = 0
    for r, frm, to, hh, mm, dur, eco, biz in ROUTES:
        code = f'WD{r}'
        after_old = old_last[r] + datetime.timedelta(days=1) if r in old_last else None
        body = {'code': code, 'airline': 'Waylo Air', 'departureAirportCode': frm, 'arrivalAirportCode': to,
                'weekdays': list(WEEKDAYS), 'departureTime': f'{hh:02d}:{mm:02d}', 'durationMinutes': dur,
                'validFrom': max(today, after_old or today).isoformat(), 'validTo': None, 'horizonDays': FLIGHT_DAYS,
                'cabins': [{'seatType': 'BUSINESS', 'price': biz, 'fromRow': 1, 'toRow': 2, 'letters': 'AC'},
                           {'seatType': 'ECONOMY', 'price': eco, 'fromRow': 3, 'toRow': 7, 'letters': 'ABCD'}]}
        old = scheds.get(code)
        if not old:
            created += 1
            if not DRY:
                must('POST', '/api/flights/schedules', body, admin)
            continue
        if old.get('validFrom') and not (after_old and old['validFrom'] < after_old.isoformat()):
            body['validFrom'] = old['validFrom']  # keep the start of an existing schedule (stable re-runs)
        if schedule_differs(old, body):
            updated += 1
            if old.get('version') is not None:
                body['expectedVersion'] = old['version']
            if not DRY:
                must('PUT', f'/api/flights/schedules/{old["id"]}', body, admin)
    summ = {} if DRY else (must('POST', '/api/flights/schedules/apply', None, admin) or {})
    print(f'flights: {"[dry] " if DRY else ""}+{created} ucus plani, {updated} plan guncellendi ({len(ROUTES)} hat, her gun, {FLIGHT_DAYS} gun ileri)')
    if not DRY:
        print(f'  uygula: {summ.get("created", 0)} ucus olusturuldu, {summ.get("skippedExisting", 0)} mevcut atlandi, {summ.get("skippedPast", 0)} gecmis atlandi')
    same = env('FLIGHT_SELLER_ORGANIZATION_ID') == org
    print('  .env FLIGHT_SELLER_ORGANIZATION_ID ' + ('dogru' if same else 'FARKLI/EKSIK: .env guncellenip package-booking-service yeniden baslatilmali'))


def main():
    global DRY
    ap = argparse.ArgumentParser(description='Rolling demo seed')
    ap.add_argument('--dry-run', action='store_true', help='print what would be created, no writes')
    ap.add_argument('--only', choices=('hotels', 'flights', 'cars'))
    ap.add_argument('--hide-others', action='store_true', help='SUSPEND other published hotels (opt-in)')
    args = ap.parse_args()
    DRY = args.dry_run
    todo = {args.only} if args.only else {'hotels', 'cars', 'flights'}
    email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not (email and pw):
        sys.exit('HATA: admin bilgisi gerekli (SEED_ADMIN_* veya WAYLO_BOOTSTRAP_ADMIN_*)')
    admin = must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']
    if 'hotels' in todo:
        seed_hotels(admin)
        admin = must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']
    if args.hide_others:
        demo_org = psql('auth_db', f"SELECT id FROM organizations WHERE name = '{HOTEL_ORG}' LIMIT 1") or None
        hide_other_hotels(admin, demo_org)
    if 'cars' in todo:
        seed_cars()
    if 'flights' in todo:
        seed_flights(admin)


if __name__ == '__main__':
    main()
