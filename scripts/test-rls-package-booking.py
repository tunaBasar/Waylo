#!/usr/bin/env python3
"""Live check for package_booking_db row level security (order_items, vouchers), same model as test-rls-car-bus.py.

Fixtures: two partner organizations (A, B) and one customer. Orders, order items and vouchers are seeded directly
in package_booking_db (creating real hotel holds is out of scope here), then:
1. API: partner A lists its order items and never sees B's (also with B's org id in the path), customer reads of
   own orders and vouchers keep working.
2. DB: raw SQL as waylo_app with app.mode=partner, app.org_ids=A cannot see or change B's order_items/vouchers;
   service and public modes see both, the context is gone after COMMIT.
Needs admin credentials (SEED_ADMIN_EMAIL/PASSWORD or WAYLO_BOOTSTRAP_ADMIN_* from env or .env, never printed).
Removes its seeded rows on exit.
"""
import argparse
import base64
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
DB = 'package_booking_db'
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


def psql(sql, db=DB):
    out = subprocess.run(['docker', 'exec', '-i', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-v',
                          'ON_ERROR_STOP=1', '-qtA'], input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    return [line for line in out.stdout.splitlines() if line]


def as_partner(org, sql):
    return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true), "
                f"set_config('app.org_ids','{org}',true) \\g /dev/null\n{sql}; COMMIT;")


def as_mode(mode, sql, end='COMMIT'):
    return psql(f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','{mode}',true) \\g /dev/null\n{sql}; {end};")


def rejected_by_rls(org, sql):
    try:
        as_partner(org, sql)
        return False
    except RuntimeError as e:
        return 'row-level security' in str(e) or 'violates' in str(e)


def jwt_sub(token):
    p = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4)))['sub']


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


def org_for(token, admin, tag):
    org = data('POST', '/api/organizations', {'name': f'RLS PB {tag} {uuid.uuid4().hex[:8]}',
                                              'businessType': 'HOTEL', 'legalType': 'COMPANY'}, token)['id']
    request('POST', f'/api/admin/organizations/{org}/approve', token=admin)
    return org


def contains(payload, needle):
    return needle in json.dumps(payload)


admin_email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
admin_pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
check(bool(admin_email and admin_pw), 'admin credentials available')
admin = data('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})['token']
token_a, token_b, token_c = register('A'), register('B'), register('C')
customer = jwt_sub(token_c)
order, item_a, item_b, vou_a, vou_b = (str(uuid.uuid4()) for _ in range(5))
try:
    org_a, org_b = org_for(token_a, admin, 'A'), org_for(token_b, admin, 'B')
    pa, pb = f'/api/partner/organizations/{org_a}', f'/api/partner/organizations/{org_b}'

    def item(i, org):
        return (f"INSERT INTO order_items (id, order_id, vertical, supplier_organization_id, product_ref, description, "
                f"unit_amount, quantity, currency) VALUES ('{i}', '{order}', 'HOTEL', '{org}', gen_random_uuid(), "
                f"'RLS item', 100, 1, 'TRY');")

    def voucher(v, i):
        return (f"INSERT INTO vouchers (id, order_item_id, code_hash, valid_from, valid_until) VALUES "
                f"('{v}', '{i}', 'h', now(), now() + interval '1 day');")
    psql(f"INSERT INTO orders (id, customer_id, status, currency, total_amount) VALUES ('{order}', '{customer}', 'CONFIRMED', 'TRY', 200);"
         + item(item_a, org_a) + item(item_b, org_b) + voucher(vou_a, item_a) + voucher(vou_b, item_b))

    # ---- API: partners
    listing_a = data('GET', pa + '/order-items?size=100', token=token_a)
    check(contains(listing_a, item_a), 'API: partner A lists its own order item')
    check(not contains(listing_a, item_b), 'API: partner A never sees B order item')
    listing_b = data('GET', pb + '/order-items?size=100', token=token_b)
    check(contains(listing_b, item_b) and not contains(listing_b, item_a), 'API: partner B sees only its own order item')
    status, _ = request('GET', pb + '/order-items', token=token_a)
    check(status in (403, 404), f'API: A cannot list B org order items ({status})')
    status, _ = request('POST', f'{pa}/vouchers/redeem', {'payload': f'WAYLO1:{vou_b}:bogus'}, token_a)
    check(status in (403, 404, 422), f'API: A cannot redeem B voucher ({status})')
    status, _ = request('POST', f'{pb}/order-items/{item_b}/check-out', {'expectedVersion': 0}, token_a)
    check(status in (403, 404, 409, 422), f'API: A cannot check out B item through B org path ({status})')
    status, _ = request('POST', f'{pa}/order-items/{item_b}/check-out', {'expectedVersion': 0}, token_a)
    check(status in (403, 404, 409, 422), f'API: A cannot check out B item through own org path ({status})')

    # ---- API: customer reads keep working
    orders = data('GET', '/api/orders', token=token_c)
    check(contains(orders, order), 'API: customer lists own order')
    check(contains(data('GET', f'/api/orders/{order}', token=token_c), item_a), 'API: customer reads own order with items')
    check(contains(data('GET', f'/api/orders/{order}/vouchers', token=token_c), vou_a), 'API: customer reads own vouchers')
    status, _ = request('GET', f'/api/orders/{order}', token=token_a)
    check(status in (403, 404), f'API: another user cannot read the customer order ({status})')

    # ---- DB: partner mode as waylo_app
    check(as_partner(org_a, f"SELECT id FROM order_items WHERE id IN ('{item_a}','{item_b}')") == [item_a], 'DB: partner A sees only item A')
    check(as_partner(org_a, f"SELECT id FROM order_items WHERE order_id = '{order}'") == [item_a], 'DB: partner A order_items without org filter is still only A')
    check(as_partner(org_a, f"SELECT id FROM vouchers WHERE id IN ('{vou_a}','{vou_b}')") == [vou_a], 'DB: partner A sees only voucher A')
    check(as_partner(org_a, f"SELECT count(*) FROM vouchers WHERE order_item_id = '{item_b}'") == ['0'], 'DB: A sees no voucher of B item')
    check(as_partner(org_a, f"WITH u AS (UPDATE vouchers SET status='VOID' WHERE id='{vou_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'DB: A UPDATE on B voucher affects 0 rows')
    check(as_partner(org_a, f"WITH u AS (UPDATE order_items SET description='pwned' WHERE id='{item_b}' RETURNING 1) SELECT count(*) FROM u") == ['0'],
          'DB: A UPDATE on B order item affects 0 rows')
    check(rejected_by_rls(org_a, f"INSERT INTO order_items (id, order_id, vertical, supplier_organization_id, product_ref, description, unit_amount, quantity, currency) "
                                 f"VALUES (gen_random_uuid(), '{order}', 'HOTEL', '{org_b}', gen_random_uuid(), 'EVIL', 1, 1, 'TRY')"),
          'DB: A INSERT of a B-owned order item rejected by RLS')
    check(as_partner(org_b, f"SELECT count(*) FROM order_items WHERE id = '{item_b}' AND description <> 'pwned'") == ['1'], 'DB: B still sees its own unmodified item')
    check(as_mode('service', f"SELECT count(*) FROM order_items WHERE id IN ('{item_a}','{item_b}')") == ['2'], 'DB: service mode sees both items')
    check(as_mode('service', f"SELECT count(*) FROM vouchers WHERE id IN ('{vou_a}','{vou_b}')") == ['2'], 'DB: service mode sees both vouchers')
    check(as_mode('admin', f"SELECT count(*) FROM order_items WHERE id IN ('{item_a}','{item_b}')") == ['2'], 'DB: admin mode sees both items')
    check(as_mode('public', f"SELECT count(*) FROM order_items WHERE order_id = '{order}'") == ['2'], 'DB: customer (public) mode reads the order items')
    check(psql("SELECT bool_and(relforcerowsecurity) FROM pg_class WHERE relname IN ('order_items','vouchers')") == ['t'],
          'DB: FORCE ROW LEVEL SECURITY on order_items and vouchers')
    check(psql("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = 'waylo_app'") == ['f'], 'DB: waylo_app is neither superuser nor BYPASSRLS')
    check(psql("BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true) \\g /dev/null\nCOMMIT;\n"
               "SELECT current_user || '|' || coalesce(current_setting('app.mode', true), '')") == ['waylo|'],
          'DB: role and app.mode are gone after COMMIT')
finally:
    try:
        psql(f"DELETE FROM orders WHERE id = '{order}'")  # cascades to items and vouchers
    except RuntimeError:
        pass

print(f'\n{passed} checks passed')
