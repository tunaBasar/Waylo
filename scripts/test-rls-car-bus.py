#!/usr/bin/env python3
"""RM-14 live check for car_rental_db and bus_db (row level security, same model as hotel).

1. API: partner A cannot read or update partner B's branch/vehicle (car) or bus/trip (bus).
2. DB: raw SQL as waylo_app with app.mode=partner, app.org_ids=A cannot see or change B's rows; service mode
   sees everything, public mode cannot write, terminals stay global, the context is gone after COMMIT.
Needs admin credentials (SEED_ADMIN_EMAIL/PASSWORD or WAYLO_BOOTSTRAP_ADMIN_* from env or .env, never printed)
to approve the two throwaway organizations. Creates only its own fixtures and deactivates them on exit.
"""
import argparse
import datetime
import json
import os
import pathlib
import secrets
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gateway', default='http://localhost:8080')
args = parser.parse_args()
ROOT = pathlib.Path(__file__).resolve().parent.parent
passed = 0


def check(condition, label):
    global passed
    if not condition:
        raise AssertionError(label)
    passed += 1
    print(f'PASS: {label}')


def env(key):
    if os.environ.get(key):
        return os.environ[key]
    f = ROOT / '.env'
    if f.exists():
        for line in f.read_text().splitlines():
            if line.startswith(key + '='):
                return line.split('=', 1)[1].strip().strip('"').strip("'")
    return ''


def request(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    payload = json.dumps(body).encode() if body is not None else None
    try:
        with urlopen(Request(args.gateway + path, data=payload, headers=headers, method=method), timeout=30) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw) if raw else None
        except ValueError:
            return error.code, None


def data(method, path, body=None, token=None):
    status, result = request(method, path, body, token)
    check(status in (200, 201) and result.get('success'), f'{method} {path} succeeds')
    return result['data']


def psql(sql, db):
    out = subprocess.run(['docker', 'exec', '-i', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-v',
                          'ON_ERROR_STOP=1', '-qtA'], input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    return [line for line in out.stdout.splitlines() if line]


def as_partner(db, org, sql):
    return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true), "
                f"set_config('app.org_ids','{org}',true) \\g /dev/null\n{sql}; COMMIT;", db)


def as_mode(db, mode, sql, end='COMMIT'):
    return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','{mode}',true) \\g /dev/null\n{sql}; {end};", db)


def rejected_by_rls(db, org, sql):
    try:
        as_partner(db, org, sql)
        return False
    except RuntimeError as e:
        return 'row-level security' in str(e) or 'violates' in str(e)


def register(tag):
    email, password = f'rls{tag.lower()}{uuid.uuid4().hex}@example.test', secrets.token_urlsafe(20)
    status, result = request('POST', '/api/auth/register', {
        'firstName': 'Rls', 'lastName': tag, 'email': email, 'password': password})
    check(status == 200 and result['success'], f'register test user {tag}')
    psql(f"UPDATE users SET email_verified = true WHERE email = '{email}'", 'auth_db')
    time.sleep(13)  # gateway register limit
    status, result = request('POST', '/api/auth/login', {'email': email, 'password': password})
    check(status == 200 and result['success'], f'login test user {tag}')
    return result['data']['token']


def org_for(token, admin, tag, business_type):
    org = data('POST', '/api/organizations', {'name': f'RLS {business_type} {tag} {uuid.uuid4().hex[:8]}',
                                              'businessType': business_type, 'legalType': 'COMPANY'}, token)['id']
    request('POST', f'/api/admin/organizations/{org}/approve', token=admin)
    return org


admin_email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
admin_pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
check(bool(admin_email and admin_pw), 'admin credentials available')
admin = data('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})['token']
token_a, token_b = register('A'), register('B')
cleanup = []
try:
    # ---------------- car rental ----------------
    car_a, car_b = org_for(token_a, admin, 'A', 'CAR_RENTAL'), org_for(token_b, admin, 'B', 'CAR_RENTAL')
    pa, pb = f'/api/partner/organizations/{car_a}', f'/api/partner/organizations/{car_b}'
    branch = lambda tag: {'name': f'RLS {tag}', 'city': 'RLSCity', 'address': 'Test only',
                          'openTime': '08:00:00', 'closeTime': '20:00:00'}
    branch_a = data('POST', pa + '/branches', branch('A'), token_a)['id']
    branch_b = data('POST', pb + '/branches', branch('B'), token_b)['id']

    def vehicle(home, plate):
        return {'homeBranchId': home, 'plateNumber': plate, 'brand': 'Fiat', 'model': 'Egea', 'year': 2023,
                'category': 'ECONOMY', 'transmission': 'MANUAL', 'fuelType': 'DIESEL', 'seatCount': 5}
    plate_a, plate_b = 'RLSA' + uuid.uuid4().hex[:6].upper(), 'RLSB' + uuid.uuid4().hex[:6].upper()
    veh_a = data('POST', pa + '/vehicles', vehicle(branch_a, plate_a), token_a)['id']
    veh_b = data('POST', pb + '/vehicles', vehicle(branch_b, plate_b), token_b)['id']
    cleanup.append(('car', veh_a, veh_b))

    attempts = [
        ('GET', f'{pb}/branches', None), ('PUT', f'{pb}/branches/{branch_b}', dict(branch('X'), name='pwned')),
        ('GET', f'{pb}/vehicles', None), ('PUT', f'{pb}/vehicles/{veh_b}', vehicle(branch_b, plate_b)),
        ('POST', f'{pb}/vehicles/{veh_b}/deactivate', None),
        ('GET', f'{pb}/rates', None), ('GET', f'{pb}/one-way-routes', None), ('GET', f'{pb}/documents', None),
        # A's own org path but B's resource ids
        ('PUT', f'{pa}/branches/{branch_b}', dict(branch('X'), name='pwned')),
        ('PUT', f'{pa}/vehicles/{veh_b}', vehicle(branch_a, plate_b)),
        ('POST', f'{pa}/vehicles/{veh_b}/deactivate', None),
    ]
    for method, path, body in attempts:
        status, _ = request(method, path, body, token_a)
        check(status in (403, 404, 422), f'car: A cannot {method} {path.replace(car_b, "<B>").replace(veh_b, "<vB>").replace(branch_b, "<brB>")} ({status})')
    check(all(b['name'] != 'pwned' for b in data('GET', pb + '/branches', token=token_b)), 'car: B branch untouched')
    check(all(v['active'] for v in data('GET', pb + '/vehicles', token=token_b)), 'car: B vehicle still active')

    db = 'car_rental_db'
    check(as_partner(db, car_a, f"SELECT id FROM branches WHERE id IN ('{branch_a}','{branch_b}')") == [branch_a], 'car DB: partner A sees only branch A')
    check(as_partner(db, car_a, f"SELECT id FROM vehicles WHERE id IN ('{veh_a}','{veh_b}')") == [veh_a], 'car DB: partner A sees only vehicle A')
    for table in ('category_rates', 'one_way_routes', 'documents'):
        check(as_partner(db, car_a, f"SELECT count(*) FROM {table} WHERE organization_id = '{car_b}'") == ['0'], f'car DB: A sees no B {table}')
    check(as_partner(db, car_a, f"WITH u AS (UPDATE vehicles SET brand='pwned' WHERE id='{veh_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'car DB: A UPDATE on B vehicle affects 0 rows')
    check(as_partner(db, car_a, f"WITH u AS (UPDATE branches SET name='pwned' WHERE id='{branch_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'car DB: A UPDATE on B branch affects 0 rows')
    check(rejected_by_rls(db, car_a, f"INSERT INTO branches (id, organization_id, name, city, address, open_time, close_time) "
                                     f"VALUES (gen_random_uuid(), '{car_b}', 'EVIL', 'x', 'x', '08:00', '20:00')"),
          'car DB: A INSERT of a B-owned branch rejected by RLS')
    check(as_partner(db, car_b, f"SELECT count(*) FROM vehicles WHERE id = '{veh_b}' AND brand <> 'pwned'") == ['1'], 'car DB: B still sees its own unmodified vehicle')
    check(as_mode(db, 'service', f"SELECT count(*) FROM vehicles WHERE id IN ('{veh_a}','{veh_b}')") == ['2'], 'car DB: service mode sees both vehicles')
    check(as_mode(db, 'public', f"SELECT count(*) FROM vehicles WHERE id IN ('{veh_a}','{veh_b}')") == ['2'], 'car DB: public mode reads the catalog')
    check(as_mode(db, 'public', "WITH u AS (UPDATE vehicles SET brand = brand RETURNING 1) SELECT count(*) FROM u", 'ROLLBACK') == ['0'],
          'car DB: public mode cannot update vehicles')
    check(psql("SELECT bool_and(relforcerowsecurity) FROM pg_class WHERE relname IN ('branches','vehicles','category_rates','one_way_routes','documents')", db) == ['t'],
          'car DB: FORCE ROW LEVEL SECURITY on all tenant tables')
    check(psql("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = 'waylo_app'", db) == ['f'], 'DB: waylo_app is neither superuser nor BYPASSRLS')
    check(psql("BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true) \\g /dev/null\nCOMMIT;\n"
               "SELECT current_user || '|' || coalesce(current_setting('app.mode', true), '')", db) == ['waylo|'],
          'car DB: role and app.mode are gone after COMMIT')

    # ---------------- bus ----------------
    bus_a, bus_b = org_for(token_a, admin, 'A', 'BUS'), org_for(token_b, admin, 'B', 'BUS')
    ba, bb = f'/api/bus-partner/organizations/{bus_a}', f'/api/bus-partner/organizations/{bus_b}'
    terminals = data('GET', '/api/admin/bus-terminals', token=admin)
    if len(terminals) < 2:
        terminals += [data('POST', '/api/admin/bus-terminals', {'city': f'RLS{i}', 'name': f'RLS terminal {uuid.uuid4().hex[:6]}'}, admin) for i in range(2)]
    t1, t2 = terminals[0]['id'], terminals[1]['id']

    def bus_body(plate):
        return {'plateNumber': plate, 'brand': 'Mercedes', 'model': 'Tourismo', 'year': 2023, 'layout': 'TWO_PLUS_TWO', 'rowCount': 10}
    bplate_a, bplate_b = 'RLSA' + uuid.uuid4().hex[:6].upper(), 'RLSB' + uuid.uuid4().hex[:6].upper()
    bus_id_a = data('POST', ba + '/buses', bus_body(bplate_a), token_a)['id']
    bus_id_b = data('POST', bb + '/buses', bus_body(bplate_b), token_b)['id']
    day = datetime.date.today() + datetime.timedelta(days=30)

    def trip_body(bus, code):
        return {'busId': bus, 'code': code, 'currency': 'TRY',
                'stops': [{'terminalId': t1, 'arrivalAt': f'{day}T10:00:00', 'departureAt': f'{day}T10:00:00'},
                          {'terminalId': t2, 'arrivalAt': f'{day}T14:00:00', 'departureAt': f'{day}T14:10:00'}],
                'fares': [{'fromSeq': 0, 'toSeq': 1, 'price': 100}]}
    trip_a = data('POST', ba + '/trips', trip_body(bus_id_a, 'RLSA' + uuid.uuid4().hex[:4]), token_a)['id']
    trip_b = data('POST', bb + '/trips', trip_body(bus_id_b, 'RLSB' + uuid.uuid4().hex[:4]), token_b)['id']
    cleanup.append(('bus', trip_a, trip_b, bb, bb))

    for method, path, body in [
        ('GET', f'{bb}/buses', None), ('GET', f'{bb}/trips', None), ('GET', f'{bb}/documents', None),
        ('GET', f'{bb}/bookings', None), ('PUT', f'{bb}/buses/{bus_id_b}', bus_body(bplate_b)),
        ('POST', f'{bb}/buses/{bus_id_b}/deactivate', None), ('POST', f'{bb}/trips/{trip_b}/cancel', None),
        ('PUT', f'{ba}/buses/{bus_id_b}', bus_body(bplate_b)),
        ('POST', f'{ba}/buses/{bus_id_b}/deactivate', None), ('POST', f'{ba}/trips/{trip_b}/cancel', None),
    ]:
        status, _ = request(method, path, body, token_a)
        check(status in (403, 404, 422), f'bus: A cannot {method} {path.replace(bus_b, "<B>").replace(bus_id_b, "<busB>").replace(trip_b, "<tripB>")} ({status})')
    check(all(t['status'] != 'CANCELLED' for t in data('GET', bb + '/trips', token=token_b)), 'bus: B trip not cancelled')
    check(all(b['active'] for b in data('GET', bb + '/buses', token=token_b)), 'bus: B bus still active')

    db = 'bus_db'
    check(as_partner(db, bus_a, f"SELECT id FROM buses WHERE id IN ('{bus_id_a}','{bus_id_b}')") == [bus_id_a], 'bus DB: partner A sees only bus A')
    check(as_partner(db, bus_a, f"SELECT id FROM trips WHERE id IN ('{trip_a}','{trip_b}')") == [trip_a], 'bus DB: partner A sees only trip A')
    for table in ('trip_stops', 'trip_fares'):
        check(as_partner(db, bus_a, f"SELECT count(*) FROM {table} WHERE trip_id = '{trip_b}'") == ['0'], f'bus DB: A sees no B {table}')
        check(as_partner(db, bus_a, f"SELECT count(*) FROM {table} WHERE trip_id = '{trip_a}'") != ['0'], f'bus DB: A sees its own {table}')
    check(as_partner(db, bus_a, f"SELECT count(*) FROM documents WHERE organization_id = '{bus_b}'") == ['0'], 'bus DB: A sees no B documents')
    check(as_partner(db, bus_a, f"WITH u AS (UPDATE trips SET status='CANCELLED' WHERE id='{trip_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'bus DB: A UPDATE on B trip affects 0 rows')
    check(as_partner(db, bus_a, f"WITH u AS (UPDATE trip_fares SET price=1 WHERE trip_id='{trip_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'bus DB: A UPDATE on B trip_fares affects 0 rows')
    check(rejected_by_rls(db, bus_a, f"INSERT INTO trip_fares (id, trip_id, from_seq, to_seq, price) VALUES (gen_random_uuid(), '{trip_b}', 0, 2, 5)"),
          'bus DB: A INSERT of a B trip fare rejected by RLS')
    check(as_partner(db, bus_a, "SELECT count(*) FROM terminals") != ['0'], 'bus DB: terminals stay global for partners')
    check(as_mode(db, 'service', f"SELECT count(*) FROM trips WHERE id IN ('{trip_a}','{trip_b}')") == ['2'], 'bus DB: service mode sees both trips')
    check(as_mode(db, 'public', f"SELECT count(*) FROM trips WHERE id IN ('{trip_a}','{trip_b}')") == ['2'], 'bus DB: public mode reads the catalog')
    check(as_mode(db, 'public', "WITH u AS (UPDATE trips SET code = code RETURNING 1) SELECT count(*) FROM u", 'ROLLBACK') == ['0'],
          'bus DB: public mode cannot update trips')
    check(psql("SELECT bool_and(relforcerowsecurity) FROM pg_class WHERE relname IN ('buses','trips','trip_stops','trip_fares','documents')", db) == ['t'],
          'bus DB: FORCE ROW LEVEL SECURITY on all tenant tables')
finally:
    for item in cleanup:
        try:
            if item[0] == 'car':
                for v, tok, org in ((item[1], token_a, car_a), (item[2], token_b, car_b)):
                    request('POST', f'/api/partner/organizations/{org}/vehicles/{v}/deactivate', token=tok)
            else:
                request('POST', f'/api/bus-partner/organizations/{bus_a}/trips/{item[1]}/cancel', token=token_a)
                request('POST', f'/api/bus-partner/organizations/{bus_b}/trips/{item[2]}/cancel', token=token_b)
        except NameError:
            pass

print(f'\n{passed} checks passed')
