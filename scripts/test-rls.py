#!/usr/bin/env python3
"""RM-14 live check: PostgreSQL row level security isolates partner catalog data in hotel_db.

1. API: partner A cannot read/update partner B's hotel, room type, rate plan or rates.
2. DB: with the code-level checks bypassed entirely (raw SQL as the application role waylo_app with
   app.mode=partner, app.org_ids=A) B's rows are invisible and un-updatable, legacy NULL-org hotels
   stay readable in public mode, and the context does not survive the transaction.
Creates only its own fixtures (two test users, organizations, hotels) and deactivates its hotels on exit.
"""
import argparse
import json
import secrets
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gateway', default='http://localhost:8080')
args = parser.parse_args()
passed = 0


def check(condition, label):
    global passed
    if not condition:
        raise AssertionError(label)
    passed += 1
    print(f'PASS: {label}')


def request(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urlopen(Request(args.gateway + path, data=data, headers=headers, method=method), timeout=30) as r:
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


def psql(sql, db='hotel_db'):
    out = subprocess.run(['docker', 'exec', '-i', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-v',
                          'ON_ERROR_STOP=1', '-qtA'], input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    return [line for line in out.stdout.splitlines() if line]


def register(tag):
    email, password = f'rls{tag.lower()}{uuid.uuid4().hex}@example.test', secrets.token_urlsafe(20)
    status, result = request('POST', '/api/auth/register', {
        'firstName': 'Rls', 'lastName': tag, 'email': email, 'password': password})
    check(status == 200 and result['success'], f'register test user {tag}')
    psql(f"UPDATE users SET email_verified = true WHERE email = '{email}'", db='auth_db')
    time.sleep(13)  # gateway register limit
    status, result = request('POST', '/api/auth/login', {'email': email, 'password': password})
    check(status == 200 and result['success'], f'login test user {tag}')
    return result['data']['token']


def fixture(token, tag):
    org = data('POST', '/api/organizations', {'name': f'RLS {tag} {uuid.uuid4().hex[:8]}',
                                              'businessType': 'HOTEL', 'legalType': 'COMPANY'}, token)
    hotel = data('POST', '/api/hotels', {'name': f'RLS fixture {tag}', 'city': f'RLS{uuid.uuid4().hex[:8]}',
                                         'country': 'TR', 'address': 'Test only', 'starRating': 3,
                                         'organizationId': org['id']}, token)
    room = data('POST', f"/api/hotels/{hotel['id']}/room-types", {
        'code': f'R{tag}', 'name': 'Test room', 'maxAdults': 2, 'maxChildren': 0, 'maxOccupancy': 2}, token)
    plan = data('POST', f"/api/hotels/{hotel['id']}/rate-plans", {
        'code': f'P{tag}', 'name': 'Test plan', 'mealPlan': 'ROOM_ONLY', 'cancellationPolicy': 'NON_REFUNDABLE'}, token)
    return org['id'], hotel['id'], room['id'], plan['id']


token_a, token_b = register('A'), register('B')
hotels = []
try:
    org_a, hotel_a, room_a, plan_a = fixture(token_a, 'A')
    hotels.append((hotel_a, token_a))
    org_b, hotel_b, room_b, plan_b = fixture(token_b, 'B')
    hotels.append((hotel_b, token_b))

    # --- API level: A against B ---
    hb = f'/api/hotels/{hotel_b}'
    attempts = [
        ('PUT', hb, {'name': 'pwned', 'city': 'X', 'country': 'TR', 'address': 'x', 'starRating': 1, 'organizationId': org_b}),
        ('POST', f'{hb}/deactivate', None),
        ('GET', f'{hb}/room-types?includeInactive=true', None),
        ('PATCH', f'{hb}/room-types/{room_b}', {'name': 'pwned'}),
        ('GET', f'{hb}/rate-plans?includeInactive=true', None),
        ('PATCH', f'{hb}/rate-plans/{plan_b}', {'name': 'pwned'}),
        ('GET', f'{hb}/rates?from=2030-01-01&to=2030-01-03', None),
        ('GET', f'{hb}/inventory?from=2030-01-01&to=2030-01-03', None),
        ('GET', f'/api/hotels?organizationId={org_b}', None),
        ('GET', f'/api/partner/organizations/{org_b}/hotel-reviews', None),
        ('POST', f'{hb}/publication-requests', {}),
        ('GET', f'{hb}/publication-requests', None),
        ('GET', f'{hb}/content', None),
        ('PATCH', f'{hb}/content', {'expectedVersion': 0, 'content': {'description': 'pwned'}}),
        ('POST', f'{hb}/archive', {'reason': 'pwned by A'}),
        ('POST', f'{hb}/publication-requests/{uuid.uuid4()}/withdraw', None),
        ('GET', '/api/admin/hotel-publications', None),
        ('POST', f'/api/admin/hotel-publications/{uuid.uuid4()}/decisions', {'decision': 'APPROVE_AND_PUBLISH', 'expectedVersion': 0}),
        ('POST', f'/api/admin/hotels/{hotel_b}/publication-status', {'decision': 'SUSPEND', 'reason': 'pwned by A'}),
    ]
    for method, path, body in attempts:
        status, result = request(method, path, body, token_a)
        if method == 'GET' and path.startswith('/api/hotels?'):
            check(status == 200 and all(h['id'] != hotel_b for h in result['data']),
                  'A listing B organization does not see unpublished B hotel')
        else:
            check(status in (401, 403, 404, 405, 422), f'A cannot {method} {path.replace(hotel_b, "<B>")} ({status})')
    check(data('GET', f'/api/hotels/{hotel_b}/room-types', token=token_b)[0]['name'] != 'pwned', 'B room type untouched')

    # --- DB level: code checks bypassed, raw SQL as the application role ---
    def as_partner(org, sql):
        return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true), "
                    f"set_config('app.org_ids','{org}',true) \\g /dev/null\n{sql}; COMMIT;")

    ids = f"('{hotel_a}','{hotel_b}')"
    check(as_partner(org_a, f'SELECT id FROM hotels WHERE id IN {ids}') == [hotel_a], 'DB: partner A sees only hotel A')
    check(as_partner(org_a, 'SELECT count(*) FROM hotels WHERE organization_id IS NULL') == ['0'],
          'DB: partner sees no legacy NULL-org hotel')
    for table, row in (('room_types', room_b), ('rate_plans', plan_b)):
        check(as_partner(org_a, f"SELECT count(*) FROM {table} WHERE id = '{row}'") == ['0'], f'DB: A cannot see B {table}')
        check(as_partner(org_a, f"WITH u AS (UPDATE {table} SET name = 'pwned' WHERE id = '{row}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
              f'DB: A UPDATE on B {table} affects 0 rows')
    for table in ('daily_rates', 'daily_inventory', 'rooms', 'inventory_adjustments', 'hotel_reviews'):
        check(as_partner(org_a, f"SELECT count(*) FROM {table} WHERE hotel_id = '{hotel_b}'") == ['0'], f'DB: A sees no B {table}')
    check(as_partner(org_a, f"WITH u AS (UPDATE hotels SET name='pwned' WHERE id='{hotel_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'DB: A UPDATE on B hotel affects 0 rows')
    try:
        as_partner(org_a, f"INSERT INTO room_types (id, hotel_id, code, name, max_adults, max_children, max_occupancy, status, created_at, updated_at) "
                          f"VALUES (gen_random_uuid(), '{hotel_b}', 'EVIL', 'x', 1, 0, 1, 'ACTIVE', now(), now())")
        inserted = True
    except RuntimeError as e:
        inserted = 'row-level security' not in str(e) and 'violates' not in str(e)
    check(not inserted, 'DB: A INSERT into B room_types rejected by RLS')
    # --- DB level: publication tables (rows seeded as service mode, then probed as partner A / public) ---
    def as_mode(mode, org, sql):
        return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','{mode}',true), "
                    f"set_config('app.org_ids','{org or ''}',true) \\g /dev/null\n{sql}; COMMIT;")

    req_a, req_b = str(uuid.uuid4()), str(uuid.uuid4())
    user_id = str(uuid.uuid4())
    for rid, hid, oid in ((req_a, hotel_a, org_a), (req_b, hotel_b, org_b)):
        as_mode('service', None,
                f"INSERT INTO hotel_publication_requests (id, hotel_id, organization_id, kind, status, submitted_by) "
                f"VALUES ('{rid}', '{hid}', '{oid}', 'INITIAL', 'SUBMITTED', '{user_id}'); "
                f"INSERT INTO hotel_publication_decisions (id, hotel_id, organization_id, request_id, decision, from_status, to_status, actor_user_id, actor_role) "
                f"VALUES (gen_random_uuid(), '{hid}', '{oid}', '{rid}', 'SUBMIT', 'DRAFT', 'SUBMITTED', '{user_id}', 'PARTNER')")
    for table, col in (('hotel_publication_requests', 'hotel_id'), ('hotel_publication_decisions', 'hotel_id'),
                       ('hotel_content_revisions', 'hotel_id')):
        check(as_mode('partner', org_a, f"SELECT count(*) FROM {table} WHERE {col} = '{hotel_b}'") == ['0'],
              f'DB: A sees no B {table}')
        check(as_mode('public', None, f"SELECT count(*) FROM {table} WHERE {col} IN {ids}") == ['0'],
              f'DB: public mode sees no {table}')
    check(as_mode('partner', org_a, f"SELECT count(*) FROM hotel_publication_requests WHERE hotel_id = '{hotel_a}'") == ['1'],
          'DB: partner A sees its own publication request')
    check(as_mode('partner', org_a, f"SELECT count(*) FROM hotel_publication_decisions WHERE hotel_id = '{hotel_a}'") == ['1'],
          'DB: partner A sees its own publication decision')
    check(as_mode('admin', None, f"SELECT count(*) FROM hotel_publication_requests WHERE hotel_id IN {ids}") == ['2'],
          'DB: admin mode sees both orgs publication requests')
    check(as_mode('partner', org_a, f"WITH u AS (UPDATE hotel_publication_requests SET submit_note='pwned' WHERE id='{req_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'DB: A UPDATE on B publication request affects 0 rows')
    try:
        as_mode('partner', org_a,
                f"INSERT INTO hotel_publication_requests (id, hotel_id, organization_id, kind, status, submitted_by) "
                f"VALUES (gen_random_uuid(), '{hotel_b}', '{org_b}', 'INITIAL', 'SUBMITTED', '{user_id}')")
        pub_inserted = True
    except RuntimeError as e:
        pub_inserted = 'row-level security' not in str(e) and 'violates' not in str(e)
    check(not pub_inserted, 'DB: A INSERT of publication request for org B rejected by RLS')
    for mode, org in (('partner', org_a), ('admin', None), ('service', None)):
        check(as_mode(mode, org, f"WITH u AS (UPDATE hotel_publication_decisions SET reason='tampered' WHERE hotel_id='{hotel_a}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
              f'DB: {mode} mode cannot UPDATE publication decisions')
        check(as_mode(mode, org, f"WITH d AS (DELETE FROM hotel_publication_decisions WHERE hotel_id='{hotel_a}' RETURNING 1) SELECT count(*) FROM d") == ['0'],
              f'DB: {mode} mode cannot DELETE publication decisions')
    check(as_mode('partner', org_a, f"WITH d AS (DELETE FROM hotel_publication_requests WHERE id='{req_a}' RETURNING 1) SELECT count(*) FROM d") == ['0'],
          'DB: partner cannot DELETE publication requests')
    check(psql("SELECT bool_and(relforcerowsecurity) FROM pg_class WHERE relname IN ('hotel_publication_requests',"
               "'hotel_publication_decisions','hotel_content_revisions')") == ['t'],
          'DB: FORCE ROW LEVEL SECURITY on publication tables')
    check(as_partner(org_b, f"SELECT count(*) FROM room_types WHERE id = '{room_b}' AND name <> 'pwned'") == ['1'],
          'DB: partner B still sees its own unmodified room type')

    public = psql("BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','public',true) \\g /dev/null\n"
                  "SELECT count(*) FROM hotels WHERE organization_id IS NULL; COMMIT;")
    legacy_total = psql('SELECT count(*) FROM hotels WHERE organization_id IS NULL')
    check(public == legacy_total and int(legacy_total[0]) >= 22, f'DB: public mode still reads all {legacy_total[0]} legacy hotels')
    check(psql("BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','public',true) \\g /dev/null\n"
               "WITH u AS (UPDATE hotels SET name = name RETURNING 1) SELECT count(*) FROM u; ROLLBACK;") == ['0'],
          'DB: public mode cannot update any hotel')
    check(psql("BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true) \\g /dev/null\nCOMMIT;\n"
               "SELECT current_user || '|' || coalesce(current_setting('app.mode', true), '')") == ['waylo|'],
          'DB: role and app.mode are gone after COMMIT on the same session')
    check(psql("SELECT bool_and(relforcerowsecurity) FROM pg_class WHERE relname IN ('hotels','room_types','rate_plans',"
               "'daily_inventory','daily_rates','inventory_adjustments','rooms','hotel_reviews')") == ['t'],
          'DB: FORCE ROW LEVEL SECURITY on all covered tables')
    check(psql("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = 'waylo_app'") == ['f'],
          'DB: waylo_app is neither superuser nor BYPASSRLS')
finally:
    for hotel_id, _ in hotels:
        try:  # fixture publication rows (decisions are append only for waylo_app, owner connection cleans up)
            psql(f"DELETE FROM hotel_publication_decisions WHERE hotel_id = '{hotel_id}'; "
                 f"DELETE FROM hotel_publication_requests WHERE hotel_id = '{hotel_id}'")
        except RuntimeError:
            pass
    for hotel_id, token in hotels:
        request('POST', f'/api/hotels/{hotel_id}/deactivate', token=token)

print(f'\n{passed} checks passed')
