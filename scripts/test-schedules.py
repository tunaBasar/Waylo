#!/usr/bin/env python3
"""Live acceptance test of the inventory automation schedules (contract sections 3 and 4). Self-cleaning.

BUS: weekly timetable -> generated trips (idempotent, cancelled never recreated, deactivate), validation errors,
RLS between organizations, customer search. FLIGHT (admin): recurring schedule -> flights, flight numbers, seats from
the Kafka event, idempotency, admin-only writes, validation errors. Needs the full stack, bus-service with
BUS_SALES_ENABLED=true for the customer search check. Secrets are never printed.

  python3 scripts/test-schedules.py
"""
import datetime
import json
import pathlib
import random
import secrets
import string
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import org_cleanup  # noqa: E402

BASE = 'http://localhost:8080'
ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
IST = datetime.timezone(datetime.timedelta(hours=3))  # Europe/Istanbul has no DST since 2016
DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

results, created_orgs, emails, cities, flight_codes = [], [], [], [], []
state = {'admin': None}


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


def call(method, path, body=None, token=None, headers=None, raw=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    h.update(headers or {})
    payload = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=payload, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            b = r.read()
            return r.status, (json.loads(b) if b and r.headers.get('Content-Type', '').startswith('application/json') else b)
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b)
        except ValueError:
            return e.code, b


def multipart(fields, filename, content):
    boundary = '----t' + uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
             for k, v in fields.items() if v is not None]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                 f'Content-Type: application/octet-stream\r\n\r\n'.encode() + content + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  -> {detail}'))


def data(resp):
    return resp[1].get('data') if isinstance(resp[1], dict) else None


def err(resp):
    return resp[1].get('error') if isinstance(resp[1], dict) else None


def psql(db, sql):
    return org_cleanup.psql(db, sql)


def login(email, password):
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': password})
    if s != 200:
        sys.exit(f'login failed ({s})')
    return b['data']['token']


def now_ist():
    return datetime.datetime.now(IST)


def expected_dates(weekdays, start, horizon, hhmm, lead_hours, valid_to=None):
    """Dates a generator run may create: weekday match, inside the horizon and validity, departure >= now + lead."""
    earliest = now_ist() + datetime.timedelta(hours=lead_hours)
    out = []
    for i in range(horizon):
        d = start + datetime.timedelta(days=i)
        if valid_to and d > valid_to:
            break
        if DAYS[d.weekday()] not in weekdays:
            continue
        dep = datetime.datetime.combine(d, datetime.time(*hhmm), tzinfo=IST)
        if dep >= earliest:
            out.append(d)
    return out


def make_partner(label, pw):
    email = f'sched-{label}-{uuid.uuid4().hex[:10]}@example.test'
    emails.append(email)
    s, _ = call('POST', '/api/auth/register', {'firstName': 'Plan', 'lastName': 'Test', 'email': email, 'password': pw})
    if s not in (200, 201):
        sys.exit(f'register failed ({s})')
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    return email


def upload(P, token, dtype, number, valid, bus_id=None):
    body, ctype = multipart({'type': dtype, 'documentNumber': number, 'validUntil': valid, 'busId': bus_id}, 'belge.pdf', PDF)
    s, b = call('POST', P + '/documents', token=token, raw=body, headers={'Content-Type': ctype})
    return b['data']['id'] if s == 201 else None


def bus_part():
    admin = state['admin']
    pw = 'Sc1' + secrets.token_urlsafe(16)
    email_a, email_b = make_partner('a', pw), make_partner('b', pw)
    time.sleep(13)
    pa, pb = login(email_a, pw), login(email_b, pw)

    oids = []
    for tok, label in ((pa, 'A'), (pb, 'B')):
        s, b = call('POST', '/api/organizations', {'name': f'Plan Test {label} ' + uuid.uuid4().hex[:5], 'businessType': 'BUS',
                                                   'legalType': 'COMPANY'}, tok)
        check(f'bus org {label} created', s in (200, 201), (s, b))
        oid = b['data']['id']
        created_orgs.append(oid)
        call('POST', f'/api/admin/organizations/{oid}/approve', token=admin)
        oids.append(oid)
    oid, oid_b = oids
    P, PB = f'/api/bus-partner/organizations/{oid}', f'/api/bus-partner/organizations/{oid_b}'
    call('POST', '/api/admin/commission-rates', {'vertical': 'BUS', 'supplierOrganizationId': oid, 'rate': 0.08}, admin)

    tag = uuid.uuid4().hex[:6]
    ca, cb, cc = 'SA' + tag, 'SB' + tag, 'SC' + tag
    cities.extend([ca, cb, cc])
    term = {}
    for c in (ca, cb, cc):
        s, b = call('POST', '/api/admin/bus-terminals', {'city': c, 'name': c + ' Otogar'}, admin)
        term[c] = b['data']['id'] if s == 201 else None
    check('3 terminals created', all(term.values()), term)
    s, b = call('POST', P + '/buses', {'plateNumber': '34 sch ' + str(secrets.randbelow(900) + 100), 'brand': 'Mercedes',
                                       'model': 'Tourismo', 'year': 2023, 'layout': 'TWO_PLUS_TWO', 'rowCount': 10}, pa)
    check('bus created', s == 201, (s, b))
    bus = b['data']['id']
    far = str(datetime.date.today() + datetime.timedelta(days=400))
    docs = [upload(P, pa, 'TRANSPORT_AUTHORIZATION', 'D1-S-1', far), upload(P, pa, 'TAX_CERTIFICATE', 'VL-S-1', None)]
    for ty, n in [('VEHICLE_REGISTRATION', 'RU'), ('VEHICLE_CARD', 'TK'), ('TRAFFIC_INSURANCE', 'TR'), ('SEAT_ACCIDENT_INSURANCE', 'KF')]:
        docs.append(upload(P, pa, ty, n + '-S-1', far, bus))
    check('6 documents uploaded', all(docs), docs)
    for d in docs:
        call('POST', f'/api/admin/bus-documents/{d}/approve', token=admin)

    # ---- create: 3 weekdays, midnight crossing stop ----
    weekdays = ['MONDAY', 'THURSDAY', 'SATURDAY']
    code = 'S' + tag
    today = now_ist().date()
    body = {'busId': bus, 'code': code, 'currency': 'TRY', 'weekdays': weekdays, 'departureTime': '22:30',
            'validFrom': str(today), 'validTo': None, 'horizonDays': 14,
            'stops': [{'terminalId': term[ca], 'arrivalOffsetMinutes': 0, 'departureOffsetMinutes': 0},
                      {'terminalId': term[cb], 'arrivalOffsetMinutes': 150, 'departureOffsetMinutes': 165},
                      {'terminalId': term[cc], 'arrivalOffsetMinutes': 450, 'departureOffsetMinutes': 450}],
            'fares': [{'fromSeq': 0, 'toSeq': 2, 'price': 500}, {'fromSeq': 0, 'toSeq': 1, 'price': 300},
                      {'fromSeq': 1, 'toSeq': 2, 'price': 250}]}
    exp = expected_dates(weekdays, today, 14, (22, 30), 1)
    s, b = call('POST', P + '/trip-schedules', body, pa)
    check('schedule created (201)', s == 201, (s, b))
    res = data((s, b)) or {}
    sch = res.get('schedule') or {}
    summ = res.get('summary') or {}
    sid = sch.get('id')
    check('schedule echoes weekdays, time and horizon', sch.get('weekdays') == weekdays and sch.get('departureTime') == '22:30'
          and sch.get('horizonDays') == 14 and sch.get('active') is True, sch)
    check('create summary: created == expected departures', len(exp) >= 5 and summ.get('created') == len(exp), (summ, len(exp)))

    trips = [t for t in (data(call('GET', P + '/trips', token=pa)) or []) if t['code'] == code]
    got = sorted(t['stops'][0]['departureAt'][:16] for t in trips)
    want = sorted(f'{d}T22:30' for d in exp)
    check('generated trips are the expected Istanbul departures', got == want, (got, want))
    check('every generated trip has scheduleId, PUBLISHED', all(t.get('scheduleId') == sid and t['status'] == 'PUBLISHED' for t in trips), '')
    t0 = trips[0] if trips else {'stops': [], 'fares': []}
    check('stops copied with midnight crossing (+1 day arrival 01:00, departure 01:15, last 06:00)',
          len(t0['stops']) == 3 and t0['stops'][1]['arrivalAt'][11:16] == '01:00' and t0['stops'][1]['departureAt'][11:16] == '01:15'
          and t0['stops'][2]['arrivalAt'][11:16] == '06:00'
          and t0['stops'][1]['arrivalAt'][:10] == str(datetime.date.fromisoformat(t0['stops'][0]['departureAt'][:10]) + datetime.timedelta(days=1)),
          t0['stops'])
    check('fares copied (3 pairs, full route 500)', len(t0['fares']) == 3
          and any(f['fromSeq'] == 0 and f['toSeq'] == 2 and float(f['price']) == 500 for f in t0['fares']), t0['fares'])
    lst = data(call('GET', P + '/trip-schedules', token=pa)) or []
    mine = next((x for x in lst if x['id'] == sid), {})
    check('list shows generatedCount and up to 5 nextDepartures', mine.get('generatedCount') == len(exp)
          and 1 <= len(mine.get('nextDepartures') or []) <= 5, mine)

    # ---- idempotent apply ----
    s, b = call('POST', P + '/trip-schedules/apply', None, pa)
    sm = data((s, b)) or {}
    check('second apply creates 0 and skips all existing', s == 200 and sm.get('created') == 0 and sm.get('skippedExisting') == len(exp), (s, b))

    # ---- cancel one, never recreated ----
    victim = trips[-1]['id'] if trips else None
    s, b = call('POST', f'{P}/trips/{victim}/cancel', None, pa)
    check('one generated trip cancelled', s == 200 and data((s, b))['status'] == 'CANCELLED', (s, b))
    s, b = call('POST', P + '/trip-schedules/apply', None, pa)
    sm = data((s, b)) or {}
    n_after = [t for t in (data(call('GET', P + '/trips', token=pa)) or []) if t['code'] == code]
    check('apply after cancel: created 0, cancelled departure not recreated', s == 200 and sm.get('created') == 0
          and len(n_after) == len(exp), (sm, len(n_after)))

    # ---- customer search ----
    if exp:
        # Search a departure that is still PUBLISHED: the partner list order decides which trip was cancelled above.
        live = sorted(t['stops'][0]['departureAt'][:10] for t in (data(call('GET', P + '/trips', token=pa)) or [])
                      if t['code'] == code and t['status'] == 'PUBLISHED')
        day = live[0] if live else exp[0]
        q = f'/api/bus-trips?fromCity={ca}&toCity={cc}&date={day}'
        offers = data(call('GET', q)) or []
        why = ''
        if not any(o['code'] == code for o in offers):
            # Diagnose the miss from the database: status, departure, org approval, documents (no secrets).
            why = {
                'trips': psql('bus_db', f"SELECT status, departure_at FROM trips WHERE code = '{code}' ORDER BY departure_at LIMIT 3"),
                'docs': psql('bus_db', f"SELECT type, status, valid_until FROM documents WHERE organization_id = '{oid}' AND bus_id IS NULL"),
                'org': psql('auth_db', f"SELECT status, business_type FROM organizations WHERE id = '{oid}'"),
                'query': q,
            }
        check('customer search finds the generated trip (needs BUS_SALES_ENABLED=true)',
              any(o['code'] == code and float(o['price']) == 500 for o in offers), (offers, why))

    # ---- validation errors ----
    r = call('POST', P + '/trip-schedules', dict(body), pa)
    check('duplicate code -> SCHEDULE_CODE_TAKEN', err(r) == 'SCHEDULE_CODE_TAKEN' and r[0] == 422, r)
    r = call('POST', P + '/trip-schedules', dict(body, code='E' + tag, weekdays=[]), pa)
    check('empty weekdays -> INVALID_SCHEDULE', err(r) == 'INVALID_SCHEDULE', r)
    bad_stops = [body['stops'][0], {'terminalId': term[cb], 'arrivalOffsetMinutes': 100, 'departureOffsetMinutes': 165}, body['stops'][2]]
    bad_stops[1]['arrivalOffsetMinutes'] = 0
    r = call('POST', P + '/trip-schedules', dict(body, code='N' + tag, stops=bad_stops), pa)
    check('non-increasing offsets -> INVALID_SCHEDULE', err(r) == 'INVALID_SCHEDULE', r)
    r = call('POST', P + '/trip-schedules', dict(body, code='H' + tag, horizonDays=500), pa)
    check('horizon out of range rejected (INVALID_SCHEDULE or 400)', err(r) in ('INVALID_SCHEDULE', 'VALIDATION_ERROR', 'BAD_REQUEST') or r[0] in (400, 422), r)

    # ---- RLS between organizations ----
    check('other partner cannot use org A path (403)', call('GET', P + '/trip-schedules', token=pb)[0] == 403)
    check('other org list does not contain the schedule', all(x['id'] != sid for x in (data(call('GET', PB + '/trip-schedules', token=pb)) or [])))
    for what, method, suffix, payload in (('update', 'PUT', '', body), ('activate', 'POST', '/activate', None), ('deactivate', 'POST', '/deactivate', None)):
        s, _ = call(method, f'{PB}/trip-schedules/{sid}{suffix}', payload, pb)
        check(f'other org cannot {what} the schedule (404)', s in (403, 404), s)
    check('schedule still active after the attempts', next((x for x in (data(call('GET', P + '/trip-schedules', token=pa)) or []) if x['id'] == sid), {}).get('active') is True)

    # ---- deactivate then apply ----
    s, b = call('POST', f'{P}/trip-schedules/{sid}/deactivate', None, pa)
    check('deactivate', s == 200 and (data((s, b)) or {}).get('schedule', {}).get('active') is False, (s, b))
    s, b = call('POST', P + '/trip-schedules/apply', None, pa)
    sm = data((s, b)) or {}
    check('apply while inactive creates 0', s == 200 and sm.get('created') == 0, (s, b))
    s, b = call('POST', f'{P}/trip-schedules/{sid}/activate', None, pa)
    check('activate again creates 0 (everything exists)', s == 200 and (data((s, b)) or {}).get('summary', {}).get('created') == 0, (s, b))


def flight_code():
    existing = {x['code'] for x in (data(call('GET', '/api/flights/schedules')) or [])}
    while True:
        c = random.choice(string.ascii_uppercase) + random.choice(string.ascii_uppercase) + str(random.randint(1, 9))
        if c not in existing and c not in flight_codes:
            flight_codes.append(c)
            return c


def fnum(code, d):
    return f'{code}{d.year % 100:02d}{d.timetuple().tm_yday:03d}'


def flight_part(partner_token):
    admin = state['admin']
    have = {a['iataCode'] for a in (data(call('GET', '/api/airports')) or [])}
    for c, name, city in (('IST', 'Istanbul Airport', 'Istanbul'), ('ESB', 'Esenboga', 'Ankara')):
        if c not in have:
            call('POST', '/api/airports', {'iataCode': c, 'name': name, 'city': city, 'country': 'Turkiye',
                                           'timezone': 'Europe/Istanbul', 'latitude': 41.0, 'longitude': 29.0}, admin)
    code = flight_code()
    today = now_ist().date()
    weekdays = ['TUESDAY', 'FRIDAY']
    cabins = [{'seatType': 'BUSINESS', 'price': 900, 'fromRow': 1, 'toRow': 2, 'letters': 'AC'},
              {'seatType': 'ECONOMY', 'price': 300, 'fromRow': 3, 'toRow': 4, 'letters': 'ABCD'}]
    body = {'code': code, 'airline': 'Plan Test Air', 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
            'weekdays': weekdays, 'departureTime': '10:15', 'durationMinutes': 90, 'validFrom': str(today),
            'validTo': str(today + datetime.timedelta(days=13)), 'horizonDays': 14, 'cabins': cabins}
    check('non-admin cannot create flight schedules (403)', call('POST', '/api/flights/schedules', body, partner_token)[0] == 403)
    check('anonymous cannot create flight schedules (401/403)', call('POST', '/api/flights/schedules', body)[0] in (401, 403))

    exp = expected_dates(weekdays, today, 14, (10, 15), 3, today + datetime.timedelta(days=13))
    s, b = call('POST', '/api/flights/schedules', body, admin)
    check('flight schedule created (201)', s == 201, (s, b))
    res = data((s, b)) or {}
    sch, summ = res.get('schedule') or {}, res.get('summary') or {}
    sid = sch.get('id')
    check('flight create summary: created == expected', len(exp) >= 3 and summ.get('created') == len(exp), (summ, len(exp)))
    numbers = [fnum(code, d) for d in exp]
    nxt = sch.get('nextFlights') or []
    check('nextFlights numbers follow code + yy + day of year', len(nxt) > 0 and all(n['flightNumber'] in numbers for n in nxt), (nxt, numbers))
    pub = next((x for x in (data(call('GET', '/api/flights/schedules')) or []) if x['code'] == code), {})
    check('GET /api/flights/schedules is public and lists it', pub.get('id') == sid and len(pub.get('nextFlights') or []) > 0, pub)
    for n in (numbers[:1] + numbers[-1:]):
        s, b = call('GET', f'/api/flights/number/{n}')
        check(f'GET /api/flights/number/{n}', s == 200 and (data((s, b)) or {}).get('flightNumber') == n, (s, b))

    # seats arrive through the Kafka event: poll up to 20 s
    deadline = time.time() + 20
    counts = {}
    for n in (numbers[:1] + numbers[-1:]):
        while time.time() < deadline:
            counts[n] = len(data(call('GET', f'/api/seat-inventory/seats/{n}')) or [])
            if counts[n] == 12:
                break
            time.sleep(1)
    check('seats created from the event: 4 business + 8 economy per flight', counts and all(v == 12 for v in counts.values()), counts)
    seats = data(call('GET', f'/api/seat-inventory/seats/{numbers[0]}')) or []
    kinds = {}
    for x in seats:
        kinds[x.get('seatType')] = kinds.get(x.get('seatType'), 0) + 1
    check('seat types BUSINESS 4 / ECONOMY 8', kinds.get('BUSINESS') == 4 and kinds.get('ECONOMY') == 8, kinds)

    s, b = call('POST', '/api/flights/schedules/apply', None, admin)
    check('second apply creates 0', s == 200 and (data((s, b)) or {}).get('created') == 0, (s, b))
    after = len(data(call('GET', '/api/seat-inventory/seats/' + numbers[0])) or [])
    check('second apply does not duplicate seats', after == 12, after)

    r = call('POST', '/api/flights/schedules', dict(body, code=flight_code(), arrivalAirportCode='IST'), admin)
    check('same airport -> SAME_AIRPORT', err(r) == 'SAME_AIRPORT', r)
    overlap = [{'seatType': 'BUSINESS', 'price': 900, 'fromRow': 1, 'toRow': 3, 'letters': 'AC'},
               {'seatType': 'ECONOMY', 'price': 300, 'fromRow': 3, 'toRow': 4, 'letters': 'ABCD'}]
    r = call('POST', '/api/flights/schedules', dict(body, code=flight_code(), cabins=overlap), admin)
    check('overlapping cabin rows -> INVALID_CABINS', err(r) == 'INVALID_CABINS', r)
    r = call('POST', '/api/flights/schedules', dict(body), admin)
    check('duplicate flight schedule code -> SCHEDULE_CODE_TAKEN', err(r) == 'SCHEDULE_CODE_TAKEN', r)
    r = call('POST', '/api/flights/schedules', dict(body, code=flight_code(), weekdays=[]), admin)
    check('empty weekdays -> INVALID_SCHEDULE', err(r) == 'INVALID_SCHEDULE', r)
    s, b = call('POST', f'/api/flights/schedules/{sid}/deactivate', None, admin)
    check('deactivate flight schedule', s == 200 and (data((s, b)) or {}).get('schedule', {}).get('active') is False, (s, b))


def cleanup():
    if flight_codes:
        q = ','.join("'" + c + "'" for c in flight_codes)
        for sid in psql('flight_db', f'SELECT id FROM flight_schedules WHERE code IN ({q})').split():
            if state['admin']:
                call('POST', f'/api/flights/schedules/{sid}/deactivate', None, state['admin'])
        nums = psql('flight_db', f'SELECT flight_number FROM flights WHERE schedule_id IN (SELECT id FROM flight_schedules WHERE code IN ({q}))').split()
        if nums:
            n = ','.join("'" + x + "'" for x in nums)
            psql('flight_seat_inventory_db', f'DELETE FROM seat_holds WHERE flight_number IN ({n})')
            psql('flight_seat_inventory_db', f'DELETE FROM flight_seats WHERE flight_number IN ({n})')
            psql('flight_db', f'DELETE FROM flights WHERE flight_number IN ({n})')
        psql('flight_db', f'DELETE FROM flight_schedule_cabins WHERE schedule_id IN (SELECT id FROM flight_schedules WHERE code IN ({q}))')
        psql('flight_db', f'DELETE FROM flight_schedules WHERE code IN ({q})')
    if created_orgs:
        ids = ','.join("'" + o + "'" for o in created_orgs)
        # schedules reference buses (and trips reference schedules): remove them before the shared org cleanup
        for k in psql('bus_db', f'SELECT storage_key FROM documents WHERE organization_id IN ({ids})').split():
            if all(c in '0123456789abcdef-' for c in k):
                subprocess.run(['docker', 'exec', 'waylo-bus-service', 'rm', '-f', '/data/bus-documents/' + k], capture_output=True)
        psql('bus_db', f'DELETE FROM trip_schedules WHERE organization_id IN ({ids})')
    org_cleanup.cleanup_test_orgs(created_orgs, emails)
    for c in cities:
        psql('bus_db', f"DELETE FROM terminals WHERE city = '{c}'")


def main():
    admin_email, admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not (admin_email and admin_pw):
        sys.exit('admin credentials missing in .env')
    state['admin'] = login(admin_email, admin_pw)
    pw = 'Sf1' + secrets.token_urlsafe(16)
    plain = make_partner('plain', pw)  # verified by the 13 s wait inside bus_part() (event propagation)
    bus_part()
    flight_part(login(plain, pw))


try:
    main()
finally:
    cleanup()
failed = [n for n, ok in results if not ok]
print(f'\n{len(results) - len(failed)}/{len(results)} PASS')
sys.exit(1 if failed else 0)
