#!/usr/bin/env python3
"""Rolling, idempotent demo data. Run it as often as you like (daily is fine): every run only adds what is missing,
so the window always starts TODAY.

  hotels   : DEMO_HOTELS in several cities, published through the normal publication flow. Per hotel two room types,
             one rate plan, daily stock and a nightly price for EVERY occupancy (1..maxOccupancy) for the next 60 days.
  cars     : delegates to seed-car-partner.py (only when SEED_CAR_PARTNER_PASSWORD is set, it is idempotent).
  flights  : airports + one flight per route per day for the next 30 days (WD<route><day-of-year>), seats per flight.
             Also ensures the flight seller organization and a FLIGHT commission rate exist and prints the env value
             package-booking-service needs (FLIGHT_SELLER_ORGANIZATION_ID).
  cleanup  : PUBLISHED hotels that are not demo hotels are SUSPENDED via the admin status API (hidden, nothing deleted).
             Set SEED_KEEP_OTHER_HOTELS=1 to skip this.

Credentials come from the environment or .env (WAYLO_BOOTSTRAP_ADMIN_* or SEED_ADMIN_*), never printed.
SEED_BASE (default http://localhost:8080), SEED_HOTEL_DAYS (60), SEED_FLIGHT_DAYS (30), SEED_FLIGHT_COMMISSION (0.0800).
"""
import datetime
import json
import os
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

BASE = os.environ.get('SEED_BASE', 'http://localhost:8080')
ROOT = pathlib.Path(__file__).resolve().parent.parent
HOTEL_DAYS = int(os.environ.get('SEED_HOTEL_DAYS', '60'))
FLIGHT_DAYS = int(os.environ.get('SEED_FLIGHT_DAYS', '30'))
FLIGHT_COMMISSION = float(os.environ.get('SEED_FLIGHT_COMMISSION', '0.0800'))
HOTEL_ORG = 'Waylo Demo Hotels'
FLIGHT_ORG = 'Waylo Flights'
TZ = datetime.timezone(datetime.timedelta(hours=3))

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
STOCK = {'DEMO_STD': 8, 'DEMO_FAM': 4}
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


def ensure_org(name, admin):
    mine = call('GET', '/api/me/memberships', token=admin)[1].get('data') or []
    for m in mine:
        if m.get('organizationName') == name and m.get('organizationStatus') in ('ACTIVE', 'PENDING'):
            if m['organizationStatus'] == 'PENDING':
                call('POST', f'/api/admin/organizations/{m["organizationId"]}/approve', token=admin)
            return m['organizationId']
    org = must('POST', '/api/organizations', {'name': name}, admin)['id']
    call('POST', f'/api/admin/organizations/{org}/approve', token=admin)
    print(f'  organizasyon olusturuldu: {name}')
    return org


def chunks(items, n):
    for i in range(0, len(items), n):
        yield items[i:i + n]


def publish(hotel_id, admin):
    s, b = call('POST', f'/api/hotels/{hotel_id}/publication-requests', {}, admin)
    if s not in (200, 201):
        print(f'  [!] yayin istegi {hotel_id}: {s}')
        return False
    d = b['data']
    s, _ = call('POST', f'/api/admin/hotel-publications/{d["id"]}/decisions',
                {'decision': 'APPROVE_AND_PUBLISH', 'expectedVersion': d.get('version', 0)}, admin)
    return s == 200


def seed_hotels(admin):
    org = ensure_org(HOTEL_ORG, admin)
    today = datetime.date.today()
    days = [today + datetime.timedelta(days=i) for i in range(HOTEL_DAYS)]
    existing = {h['name']: h for h in (call('GET', f'/api/hotels?organizationId={org}', token=admin)[1].get('data') or [])}
    new_h = new_stock = new_rates = 0
    for name, city, country, addr, stars, desc, base in DEMO_HOTELS:
        h = existing.get(name)
        if not h:
            h = must('POST', '/api/hotels', {'name': name, 'city': city, 'country': country, 'address': addr,
                                            'starRating': stars, 'description': desc, 'organizationId': org}, admin)
            new_h += 1
        hid = h['id']
        types = {t['code']: t for t in (call('GET', f'/api/hotels/{hid}/room-types', token=admin)[1].get('data') or [])}
        for code, tname, adults, kids, occ, _mult in ROOM_TYPES:
            if code not in types:
                types[code] = must('POST', f'/api/hotels/{hid}/room-types', {
                    'code': code, 'name': tname, 'maxAdults': adults, 'maxChildren': kids, 'maxOccupancy': occ}, admin)
        plans = {p['code']: p for p in (call('GET', f'/api/hotels/{hid}/rate-plans', token=admin)[1].get('data') or [])}
        if RATE_PLAN[0] not in plans:
            plans[RATE_PLAN[0]] = must('POST', f'/api/hotels/{hid}/rate-plans', {
                'code': RATE_PLAN[0], 'name': RATE_PLAN[1], 'mealPlan': 'BREAKFAST',
                'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, admin)
        plan = plans[RATE_PLAN[0]]['id']
        frm, to = days[0].isoformat(), days[-1].isoformat()
        have_inv = {(i['roomTypeId'], i['stayDate']) for i in
                    (call('GET', f'/api/hotels/{hid}/inventory?from={frm}&to={to}', token=admin)[1].get('data') or [])}
        have_rate = {(r['roomTypeId'], r['stayDate'], r['occupancy']) for r in
                     (call('GET', f'/api/hotels/{hid}/rates?from={frm}&to={to}', token=admin)[1].get('data') or [])}
        inv, rates = [], []
        for code, _n, _a, _k, occ, mult in ROOM_TYPES:
            rt = types[code]['id']
            for d in days:
                ds = d.isoformat()
                weekend = d.weekday() in (4, 5)
                if (rt, ds) not in have_inv:
                    inv.append({'roomTypeId': rt, 'stayDate': ds, 'allotment': STOCK[code], 'expectedVersion': None})
                for o in range(1, occ + 1):
                    if (rt, ds, o) in have_rate:
                        continue
                    amount = round(base * mult * OCC_FACTOR.get(o, 1.45) * (1.15 if weekend else 1.0), -1)
                    rates.append({'roomTypeId': rt, 'ratePlanId': plan, 'stayDate': ds, 'occupancy': o,
                                  'currency': 'TRY', 'amount': amount, 'expectedVersion': None})
        for part in chunks(inv, 200):
            must('PUT', f'/api/hotels/{hid}/inventory', {'items': part, 'reason': 'demo seed'}, admin)
        for part in chunks(rates, 400):
            must('PUT', f'/api/hotels/{hid}/rates', {'items': part}, admin)
        new_stock += len(inv)
        new_rates += len(rates)
        if h.get('publicationStatus') != 'PUBLISHED':
            if h.get('publicationStatus') == 'SUSPENDED':
                call('POST', f'/api/admin/hotels/{hid}/publication-status',
                     {'decision': 'REINSTATE', 'reason': 'demo seed reinstate'}, admin)
            else:
                publish(hid, admin)
    print(f'hotels : +{new_h} otel, +{new_stock} stok gunu, +{new_rates} fiyat satiri ({HOTEL_DAYS} gun)')


def hide_other_hotels(admin):
    if os.environ.get('SEED_KEEP_OTHER_HOTELS') == '1':
        return
    demo = {h[0] for h in DEMO_HOTELS}
    hidden = 0
    for h in call('GET', '/api/hotels', token=admin)[1].get('data') or []:
        if h.get('name') in demo or h.get('publicationStatus') != 'PUBLISHED':
            continue
        s, _ = call('POST', f'/api/admin/hotels/{h["id"]}/publication-status',
                    {'decision': 'SUSPEND', 'reason': 'demo seed: test verisi gizlendi'}, admin)
        hidden += 1 if s == 200 else 0
    print(f'cleanup: {hidden} test oteli gizlendi')


def seed_cars():
    if not env('SEED_CAR_PARTNER_PASSWORD'):
        print('cars   : atlandi (SEED_CAR_PARTNER_PASSWORD yok)')
        return
    r = subprocess.run([sys.executable, str(ROOT / 'scripts' / 'seed-car-partner.py')], capture_output=True, text=True)
    print('cars   :', 'tamam' if r.returncode == 0 else 'HATA ' + r.stdout[-200:] + r.stderr[-200:])


def seed_flights(admin):
    org = ensure_org(FLIGHT_ORG, admin)
    rates = call('GET', '/api/admin/commission-rates', token=admin)[1].get('data') or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == org for r in rates):
        must('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': org, 'rate': FLIGHT_COMMISSION}, admin)
        print(f'  FLIGHT komisyon orani {FLIGHT_COMMISSION} eklendi')
    have = {a['iataCode'] for a in (call('GET', '/api/airports')[1].get('data') or [])}
    for code, name, city, country, tz, lat, lon in AIRPORTS:
        if code not in have:
            must('POST', '/api/airports', {'iataCode': code, 'name': name, 'city': city, 'country': country,
                                           'timezone': tz, 'latitude': lat, 'longitude': lon}, admin)
    now = datetime.datetime.now(TZ)
    new_f = 0
    for off in range(1, FLIGHT_DAYS + 1):
        day = now + datetime.timedelta(days=off)
        doy = day.timetuple().tm_yday
        for r, frm, to, hh, mm, dur, eco, biz in ROUTES:
            no = f'WD{r}{doy:03d}'
            if call('GET', f'/api/flights/number/{no}')[0] != 200:
                dep = day.replace(hour=hh, minute=mm, second=0, microsecond=0).astimezone(datetime.timezone.utc)
                arr = dep + datetime.timedelta(minutes=dur)
                must('POST', '/api/flights', {'flightNumber': no, 'departureAirportCode': frm, 'arrivalAirportCode': to,
                                              'airline': 'Waylo Air', 'departureTime': dep.strftime('%Y-%m-%dT%H:%M:%SZ'),
                                              'arrivalTime': arr.strftime('%Y-%m-%dT%H:%M:%SZ')}, admin)
                new_f += 1
            if (call('GET', f'/api/seat-inventory/seats/{no}')[1].get('data') or []):
                continue
            weekend = 1.1 if day.weekday() in (4, 6) else 1.0
            for cabin, price, rows, letters in (('BUSINESS', biz, (1, 2), 'AC'), ('ECONOMY', eco, (3, 7), 'ABCD')):
                must('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': no, 'seatType': cabin,
                                                                'price': round(price * weekend, -1), 'fromRow': rows[0],
                                                                'toRow': rows[1], 'letters': letters}, admin)
    print(f'flights: +{new_f} ucus ({FLIGHT_DAYS} gun, {len(ROUTES)} hat/gun)')
    print(f'  package-booking icin: FLIGHT_SELLER_ORGANIZATION_ID={org}')


def main():
    email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not (email and pw):
        sys.exit('HATA: admin bilgisi gerekli (SEED_ADMIN_* veya WAYLO_BOOTSTRAP_ADMIN_*)')
    admin = must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']
    seed_hotels(admin)
    admin = must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']
    hide_other_hotels(admin)
    seed_cars()
    seed_flights(admin)


if __name__ == '__main__':
    main()
