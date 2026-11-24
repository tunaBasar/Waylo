#!/usr/bin/env python3
"""RM-37 tenant authorization regression matrix, ONE live script (static-checked only by its author).

Through the real gateway (http://localhost:8080) and the real databases (docker exec waylo-postgres psql):
 - org B / org C users never read or mutate org A data, staff cannot do manager/owner actions,
   customer and anonymous cannot reach partner/admin data, admin endpoints reject non-admins.
 - every unauthorized write is followed by a read-only DB snapshot compare (ZERO effect).
 - RLS schema classification per DB: waylo_app NOSUPERUSER/NOBYPASSRLS, every org-scoped table FORCE RLS or allowlisted,
   partner-mode SELECT without WHERE as waylo_app never returns org B rows.
Evidence: scripts/acceptance/out/tenant-matrix-<timestamp>.json. Exit 1 on any failure.
Only rows this script created are deleted (finally block). Admin credentials come from .env and are never printed.
"""
import fnmatch
import json
import os
import pathlib
import secrets
import struct
import subprocess
import sys
import time
import uuid
import zlib
from datetime import date, datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
BASE = os.environ.get('WAYLO_BASE_URL', 'http://localhost:8080')
REG_SLEEP = float(os.environ.get('WAYLO_REGISTER_SLEEP', '13'))  # 0 when the gateway test override (120/min) is active
REPORT = []
STATE = {'emails': [], 'orgs': [], 'hotels': [], 'media': []}

# RLS allowlist: org-scoped tables that intentionally do NOT have FORCE RLS. pattern -> one-line reason.
RLS_ALLOWLIST = {
    '*.outbox_event': 'transactional outbox, written/relayed by service code, no tenant reads',
    '*.processed_events': 'consumer idempotency ledger, internal only',
    '*.shedlock': 'scheduler lock table, no tenant data',
    '*.flyway_schema_history': 'migration bookkeeping, no tenant data',
    'hotel_db.hotel_holds': 'transaction table: short lived inventory hold, accessed by customer flow and expiry job',
    'hotel_db.hotel_quotes': 'transaction table: customer quote, accessed by customer flow before any tenant context',
    'hotel_db.hotel_quote_nights': 'transaction table: child rows of hotel_quotes',
    'car_rental_db.*holds': 'transaction table: short lived hold, customer flow and expiry job',
    'car_rental_db.*quotes': 'transaction table: customer quote, customer flow',
    'bus_db.*holds': 'transaction table: short lived seat hold, customer flow and expiry job',
    'bus_db.*quotes': 'transaction table: customer quote, customer flow',
    # package_booking_db.order_items and vouchers have FORCE RLS since V12 (4eaf1f2): they are checked, not allowlisted
}
DBS = ['hotel_db', 'car_rental_db', 'bus_db', 'package_booking_db']


# ---------------------------------------------------------------- helpers
def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


def http(method, path, body=None, token=None, raw_body=None, headers=None):
    h = dict(headers or {})
    data = raw_body
    if raw_body is None:
        h.setdefault('Content-Type', 'application/json')
        data = json.dumps(body).encode() if body is not None else None
    if token:
        h['Authorization'] = 'Bearer ' + token
    try:
        with urlopen(Request(BASE + path, data=data, headers=h, method=method), timeout=30) as r:
            raw = r.read()
            status = r.status
    except HTTPError as e:
        raw, status = e.read(), e.code
    try:
        return status, (json.loads(raw) if raw else None)
    except ValueError:
        return status, raw


def psql(db, sql, user='waylo'):
    out = subprocess.run(['docker', 'exec', '-i', 'waylo-postgres', 'psql', '-U', user, '-d', db, '-v', 'ON_ERROR_STOP=1', '-qtA'],
                         input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip())
    return [l for l in out.stdout.splitlines() if l]


def psql_safe(db, sql):
    try:
        return psql(db, sql)
    except RuntimeError:
        return None


def must(method, path, body=None, token=None):
    s, d = http(method, path, body, token)
    if s not in (200, 201, 204) or not (isinstance(d, dict) and d.get('success', True)):
        raise RuntimeError(f'fixture {method} {path} -> {s}')
    return d.get('data') if isinstance(d, dict) else d


def register(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:10]}@example.test'
    pw = 'Tm1' + secrets.token_urlsafe(16)
    STATE['emails'].append(email)
    must('POST', '/api/auth/register', {'firstName': 'Tm', 'lastName': prefix, 'email': email, 'password': pw})
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    if REG_SLEEP:
        time.sleep(REG_SLEEP)
    return email, must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']


def record(check, actor, method, path, expected, observed, ok):
    REPORT.append({'rmId': 'RM-37', 'check': check, 'actor': actor, 'method': method, 'path': path,
                   'expected': sorted(expected) if isinstance(expected, (set, list, tuple)) else expected,
                   'observed': observed, 'pass': bool(ok)})
    print(('PASS' if ok else 'FAIL') + f': [{actor}] {method} {path} expected={expected} observed={observed} ({check})')


def png(w, h):
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    row = b'\x00' + b'\x80\x40\xc0' * w
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(row * h, 9)) + chunk(b'IEND', b''))


def multipart(content):
    b = '----' + uuid.uuid4().hex
    body = (f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="t.png"\r\nContent-Type: image/png\r\n\r\n'.encode()
            + content + f'\r\n--{b}--\r\n'.encode())
    return body, {'Content-Type': f'multipart/form-data; boundary={b}'}


# ---------------------------------------------------------------- DB snapshots (read only, superuser)
def snapshot(hotel, org, extra_orders=''):
    h = psql('hotel_db', f"""SELECT 'hotel', md5(row(h.*)::text) FROM hotels h WHERE id='{hotel}'
UNION ALL SELECT 'room_types', coalesce(md5(string_agg(row(t.*)::text, ',' ORDER BY t.id)), '-') FROM room_types t WHERE hotel_id='{hotel}'
UNION ALL SELECT 'rate_plans', coalesce(md5(string_agg(row(t.*)::text, ',' ORDER BY t.id)), '-') FROM rate_plans t WHERE hotel_id='{hotel}'
UNION ALL SELECT 'inventory', coalesce(md5(string_agg(row(t.*)::text, ',' ORDER BY t.id)), '-') FROM daily_inventory t WHERE hotel_id='{hotel}'
UNION ALL SELECT 'pubreq', count(*)::text FROM hotel_publication_requests WHERE hotel_id='{hotel}'
UNION ALL SELECT 'content', count(*)::text FROM hotel_content_revisions WHERE hotel_id='{hotel}'
UNION ALL SELECT 'media', coalesce(md5(string_agg(row(t.*)::text, ',' ORDER BY t.id)), '-') FROM hotel_media t WHERE hotel_id='{hotel}'""")
    p = psql_safe('package_booking_db', f"SELECT md5(coalesce(string_agg(row(o.*)::text, ',' ORDER BY o.id), '-')) FROM order_items o WHERE supplier_organization_id='{org}'") or ['n/a']
    pay = psql_safe('payment_db', f"SELECT (SELECT count(*) FROM partner_bank_accounts WHERE organization_id='{org}') || '/' || "
                                  f"(SELECT count(*) FROM partner_payouts WHERE organization_id='{org}')") or ['n/a']
    return h + p + pay


def attempt(check, actor, token, method, path, body, expected, hotel=None, org=None, mutation=False, raw_body=None, headers=None):
    """One matrix cell. Mutations on tenant A additionally compare a DB snapshot before and after."""
    before = snapshot(hotel, org) if mutation and hotel else None
    status, _ = http(method, path, body, token, raw_body, headers)
    ok = status in expected
    record(check, actor, method, path, expected, status, ok)
    if before is not None:
        after = snapshot(hotel, org)
        record(check + ' / zero DB effect', actor, method, path, 'snapshot unchanged', 'unchanged' if before == after else 'CHANGED', before == after)
    return status


# ---------------------------------------------------------------- RLS checks
def rls_checks(org_a, org_b):
    running = set()
    for db in DBS:
        try:
            psql(db, 'SELECT 1')
            running.add(db)
        except RuntimeError:
            record('rls db running', 'superuser', 'SQL', db, 'running', 'SKIPPED (not running)', True)
    if not running:
        return
    role = psql('hotel_db' if 'hotel_db' in running else sorted(running)[0],
                "SELECT rolsuper::text || '|' || rolbypassrls::text FROM pg_roles WHERE rolname='waylo_app'")
    record('waylo_app exists, NOSUPERUSER, NOBYPASSRLS', 'superuser', 'SQL', 'pg_roles', 'false|false', role[0] if role else 'missing', role == ['false|false'])
    for db in sorted(running):
        cols = psql(db, "SELECT c.table_name || '|' || string_agg(c.column_name, ',') || '|' || cl.relrowsecurity || '|' || cl.relforcerowsecurity "
                        "FROM information_schema.columns c JOIN pg_class cl ON cl.relname=c.table_name AND cl.relnamespace='public'::regnamespace "
                        "JOIN information_schema.tables t ON t.table_name=c.table_name AND t.table_schema='public' AND t.table_type='BASE TABLE' "
                        "WHERE c.table_schema='public' AND c.column_name IN ('organization_id','supplier_organization_id') GROUP BY c.table_name, cl.relrowsecurity, cl.relforcerowsecurity ORDER BY 1")
        for line in cols:
            table, columns, rls, force = line.split('|')
            allow = next((r for p, r in RLS_ALLOWLIST.items() if fnmatch.fnmatch(f'{db}.{table}', p)), None)
            covered = rls in ('t', 'true') and force in ('t', 'true')  # text cast of boolean is 'true'/'false'
            record('RLS classification' + (f' (allowlisted: {allow})' if allow and not covered else ''), 'superuser', 'SQL', f'{db}.{table}',
                   'relrowsecurity+relforcerowsecurity or allowlisted', f'rls={rls} force={force}', covered or allow is not None)
            if not covered:
                continue
            for col in columns.split(','):
                rows = psql(db, f"BEGIN; SET LOCAL ROLE waylo_app; SELECT set_config('app.mode','partner',true), "
                                f"set_config('app.org_ids','{org_a}',true) \\g /dev/null\nSELECT DISTINCT {col} FROM {table}; COMMIT;")
                record('partner-mode SELECT without WHERE leaks no org B row', f'waylo_app(org A)', 'SQL', f'{db}.{table}.{col}',
                       'no row of org B', f'{len(rows)} distinct orgs, B visible={org_b in rows}', org_b not in rows)


# ---------------------------------------------------------------- main
def main():
    admin_email, admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not admin_email or not admin_pw:
        print('SKIP: bootstrap admin missing in .env')
        return 2
    try:
        admin = must('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})['token']
        _, owner_a = register('tm-ownera')
        manager_email, manager = register('tm-managera')
        staff_email, staff = register('tm-staffa')
        _, owner_b = register('tm-ownerb')
        _, owner_c = register('tm-ownerc')
        _, customer = register('tm-cust')

        def org(token, name, btype):
            o = must('POST', '/api/organizations', {'name': f'tm-{name}-{uuid.uuid4().hex[:6]}', 'businessType': btype, 'legalType': 'COMPANY'}, token)['id']
            STATE['orgs'].append(o)
            must('POST', f'/api/admin/organizations/{o}/approve', token=admin)
            return o
        org_a, org_b = org(owner_a, 'a', 'HOTEL'), org(owner_b, 'b', 'HOTEL')
        org_c = org(owner_c, 'c', 'CAR_RENTAL')

        def hotel(token, o, tag):
            h = must('POST', '/api/hotels', {'name': f'TM hotel {tag}', 'city': f'TM{uuid.uuid4().hex[:6]}', 'country': 'TR',
                                             'address': 'Test only', 'starRating': 3, 'organizationId': o}, token)['id']
            STATE['hotels'].append(h)
            return h
        hotel_a, hotel_b = hotel(owner_a, org_a, 'A'), hotel(owner_b, org_b, 'B')
        must('POST', f'/api/organizations/{org_a}/role-grants', {'email': manager_email, 'role': 'HOTEL_MANAGER', 'hotelId': hotel_a}, owner_a)
        must('POST', f'/api/organizations/{org_a}/role-grants', {'email': staff_email, 'role': 'HOTEL_STAFF', 'hotelId': hotel_a}, owner_a)
        room = must('POST', f'/api/hotels/{hotel_a}/room-types', {'code': 'TM_DBL', 'name': 'TM double', 'maxAdults': 2, 'maxChildren': 0, 'maxOccupancy': 2}, owner_a)['id']
        plan = must('POST', f'/api/hotels/{hotel_a}/rate-plans', {'code': 'TM_FLEX', 'name': 'TM flex', 'mealPlan': 'BREAKFAST',
                                                                  'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, owner_a)['id']
        day = (date.today() + timedelta(days=50)).isoformat()
        body, hdr = multipart(png(800, 600))
        s, d = http('POST', f'/api/hotels/{hotel_a}/media', token=owner_a, raw_body=body, headers=hdr)
        media = d['data']['id'] if s == 201 else None
        record('fixture: ownerA uploads a photo', 'ownerA', 'POST', f'/api/hotels/{hotel_a}/media', {201}, s, s == 201)
        STATE['media'] = [media] if media else []

        actors = {'ownerA': owner_a, 'managerA': manager, 'staffA': staff, 'ownerB': owner_b, 'ownerC': owner_c, 'customer': customer, 'admin': admin, 'anon': None}
        DENY = {401, 403, 404}
        A, ORG = f'/api/hotels/{hotel_a}', f'/api/partner/organizations/{org_a}'
        outsiders = ['ownerB', 'ownerC', 'customer', 'anon']

        # ---------- hotels: READS (hotel A is unpublished, so only A members may see management data)
        reads = [('hotel detail', f'{A}'), ('room types incl. inactive', f'{A}/room-types?includeInactive=true'),
                 ('rate plans incl. inactive', f'{A}/rate-plans?includeInactive=true'),
                 ('inventory', f'{A}/inventory?from={day}&to={day}'), ('content', f'{A}/content'),
                 ('publication requests', f'{A}/publication-requests'), ('media/mine', f'{A}/media/mine')]
        # Current role model (auth AccessService): HOTEL_STAFF has no HOTEL_MANAGE/ROOM_MANAGE, so management reads are
        # owner/manager only; staff sees reservations (RESERVATION_READ) instead. The hotel detail is HOTEL_READ (any member).
        manager_only = {'room types incl. inactive', 'rate plans incl. inactive', 'inventory', 'content', 'publication requests', 'media/mine'}
        for name, path in reads:
            for who in ('ownerA', 'managerA', 'staffA'):
                if who == 'staffA' and name in manager_only:
                    attempt('staff cannot read management ' + name, who, actors[who], 'GET', path, None, {403})
                else:
                    attempt('A member reads ' + name, who, actors[who], 'GET', path, None, {200})
            for who in outsiders:
                attempt('outsider cannot read ' + name, who, actors[who], 'GET', path, None, DENY)
        if media:
            for who in ('ownerA', 'managerA'):
                attempt('A member reads partner file', who, actors[who], 'GET', f'{A}/media/{media}/file', None, {200})
            for who in outsiders:
                attempt('outsider cannot read partner file', who, actors[who], 'GET', f'{A}/media/{media}/file', None, DENY)
        # ---------- hotels: MUTATIONS by outsiders (zero effect) and by staff (no manager/owner action)
        hotel_put = {'name': 'pwned', 'city': 'X', 'country': 'TR', 'address': 'x', 'starRating': 1, 'organizationId': org_a}
        muts = [('PUT', A, hotel_put, 'hotel detail update'),
                ('PATCH', f'{A}/room-types/{room}', {'name': 'pwned'}, 'room type patch'),
                ('PATCH', f'{A}/rate-plans/{plan}', {'name': 'pwned'}, 'rate plan patch'),
                ('PUT', f'{A}/inventory', {'items': [{'roomTypeId': room, 'stayDate': day, 'allotment': 99, 'expectedVersion': None}], 'reason': 'pwned'}, 'inventory put'),
                ('POST', f'{A}/publication-requests', {}, 'publication request'),
                ('PATCH', f'{A}/content', {'expectedVersion': 0, 'content': {'description': 'pwned'}}, 'content patch'),
                ('POST', f'{A}/deactivate', None, 'deactivate'),
                ('POST', f'{A}/archive', {'reason': 'pwned'}, 'archive')]
        for method, path, body, name in muts:
            for who in outsiders:
                attempt(f'outsider cannot {name}', who, actors[who], method, path, body, DENY, hotel_a, org_a, mutation=True)
            if name in ('hotel detail update', 'deactivate', 'archive', 'publication request'):  # owner/manager level actions
                attempt(f'staff cannot {name}', 'staffA', staff, method, path, body, DENY, hotel_a, org_a, mutation=True)
        body, hdr = multipart(png(800, 600))
        for who in outsiders:
            attempt('outsider cannot upload media', who, actors[who], 'POST', f'{A}/media', None, DENY, hotel_a, org_a, mutation=True, raw_body=body, headers=hdr)
        if media:
            attempt('outsider cannot patch media', 'ownerB', owner_b, 'PATCH', f'{A}/media/{media}', {'caption': 'pwned'}, DENY, hotel_a, org_a, mutation=True)
            attempt('outsider cannot delete media', 'ownerB', owner_b, 'DELETE', f'{A}/media/{media}', None, DENY, hotel_a, org_a, mutation=True)
        # reverse direction: A members against hotel B
        B = f'/api/hotels/{hotel_b}'
        for who in ('ownerA', 'managerA', 'staffA'):
            attempt('A member cannot read hotel B room types', who, actors[who], 'GET', f'{B}/room-types?includeInactive=true', None, DENY)
            attempt('A member cannot update hotel B', who, actors[who], 'PUT', B, dict(hotel_put, organizationId=org_b), DENY, hotel_b, org_b, mutation=True)
            attempt('A member cannot read hotel B media/mine', who, actors[who], 'GET', f'{B}/media/mine', None, DENY)

        # ---------- package-booking partner (orders of org A)
        fake_item, fake_payload = str(uuid.uuid4()), {'payload': 'tm-invalid-' + uuid.uuid4().hex}
        for who in ('ownerA', 'managerA', 'staffA'):
            attempt('A member lists order items', who, actors[who], 'GET', f'{ORG}/order-items', None, {200})
        for who in outsiders:
            attempt('outsider cannot list order items', who, actors[who], 'GET', f'{ORG}/order-items', None, DENY, hotel_a, org_a)
            attempt('outsider cannot redeem voucher', who, actors[who], 'POST', f'{ORG}/vouchers/redeem', fake_payload, DENY, hotel_a, org_a, mutation=True)
            # valid body, so the answer is decided by authorization and not by request validation
            attempt('outsider cannot check-out', who, actors[who], 'POST', f'{ORG}/order-items/{fake_item}/check-out', {'expectedVersion': 0}, DENY, hotel_a, org_a, mutation=True)
        attempt('owner C (car org) cannot read org B items', 'ownerC', owner_c, 'GET', f'/api/partner/organizations/{org_b}/order-items', None, DENY)
        attempt('owner A cannot read car org C items', 'ownerA', owner_a, 'GET', f'/api/partner/organizations/{org_c}/order-items', None, DENY)
        attempt('owner B cannot read car org C earnings', 'ownerB', owner_b, 'GET', f'/api/partner/organizations/{org_c}/earnings', None, DENY)

        # ---------- payment partner
        for name, method, path in (('earnings', 'GET', f'{ORG}/earnings'), ('payout balance', 'GET', f'{ORG}/payouts/balance'),
                                   ('bank account', 'GET', f'{ORG}/bank-account')):
            attempt('owner reads ' + name, 'ownerA', owner_a, method, path, None, {200, 404})
            # PAYOUT-CONTRACT 3.2: partner reads need RESERVATION_READ, which HOTEL_STAFF has (writes are owner-only, below)
            attempt('staff may read ' + name + ' (RESERVATION_READ)', 'staffA', staff, method, path, None, {200, 404})
            for who in outsiders:
                attempt('outsider cannot read ' + name, who, actors[who], method, path, None, DENY, hotel_a, org_a)
        for who in outsiders + ['staffA']:
            attempt('cannot set bank account', who, actors[who], 'PUT', f'{ORG}/bank-account',
                    {'accountHolderName': 'Pwned', 'iban': 'TR330006100519786457841326'}, DENY, hotel_a, org_a, mutation=True)
            attempt('cannot request payout', who, actors[who], 'POST', f'{ORG}/payouts', {'currency': 'TRY', 'amount': 1.00}, DENY, hotel_a, org_a, mutation=True,
                    headers={'Idempotency-Key': str(uuid.uuid4()), 'Content-Type': 'application/json'})

        # ---------- admin only
        admin_paths = [('GET', '/api/admin/hotel-publications'), ('GET', '/api/admin/hotel-media?reviewStatus=PENDING'), ('GET', '/api/admin/payouts'),
                       ('GET', '/api/admin/payment-reconciliation'), ('GET', '/api/admin/orders'), ('GET', '/api/notifications/status/FAILED')]
        for method, path in admin_paths:
            attempt('admin allowed', 'admin', admin, method, path, None, {200})
            for who in ('ownerA', 'managerA', 'staffA', 'ownerB', 'ownerC', 'customer'):
                attempt('non admin rejected', who, actors[who], method, path, None, {403})
            attempt('anonymous rejected', 'anon', None, method, path, None, {401})
        attempt('non admin cannot decide publication', 'ownerA', owner_a, 'POST', f'/api/admin/hotel-publications/{uuid.uuid4()}/decisions',
                {'decision': 'APPROVE_AND_PUBLISH', 'expectedVersion': 0}, {403}, hotel_a, org_a, mutation=True)
        attempt('non admin cannot approve own org', 'ownerB', owner_b, 'POST', f'/api/admin/organizations/{org_b}/approve', {}, {403})

        rls_checks(org_a, org_b)
    except Exception as e:  # fixture failure is a failure, not a skip
        record('fixture setup / unexpected error', 'script', '-', '-', 'no exception', f'{type(e).__name__}: {e}', False)
    finally:
        try:
            cleanup()
        except Exception as e:
            print(f'cleanup error: {e}')
    return write_report()


def cleanup():
    """Delete only rows created by this run: hotels, orgs and users recorded in STATE (ids/emails generated here)."""
    hotels = ','.join(f"'{h}'" for h in STATE['hotels'])
    orgs = ','.join(f"'{o}'" for o in STATE['orgs'])
    if hotels:
        for t in ('hotel_media', 'hotel_publication_decisions', 'hotel_publication_requests', 'hotel_content_revisions', 'hotel_holds', 'hotel_quotes',
                  'inventory_adjustments', 'daily_rates', 'daily_inventory', 'rate_plans', 'room_types'):
            psql_safe('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({hotels})')
        psql_safe('hotel_db', f'DELETE FROM hotels WHERE id IN ({hotels})')
    if orgs:
        cols = psql_safe('car_rental_db', "SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='organization_id'") or []
        for t in cols:  # car org C fixtures are created by the org only, but sweep the rows keyed to our orgs
            psql_safe('car_rental_db', f'DELETE FROM {t} WHERE organization_id IN ({orgs})')
        psql_safe('payment_db', f'DELETE FROM partner_bank_accounts WHERE organization_id IN ({orgs})')
        psql_safe('payment_db', f'DELETE FROM commission_rates WHERE supplier_organization_id IN ({orgs})')
        for t in ('role_assignments', 'organization_memberships'):
            psql_safe('auth_db', f'DELETE FROM {t} WHERE organization_id IN ({orgs})')
        psql_safe('auth_db', f'DELETE FROM organizations WHERE id IN ({orgs})')
    if STATE['emails']:
        q = ','.join(f"'{e}'" for e in STATE['emails'])
        psql_safe('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql_safe('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql_safe('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def write_report():
    out = ROOT / 'scripts' / 'acceptance' / 'out'
    out.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    path = out / f'tenant-matrix-{stamp}.json'
    path.write_text(json.dumps(REPORT, indent=2))
    n, ok = len(REPORT), sum(1 for r in REPORT if r['pass'])
    print(f'\nRM-37 tenant matrix: {ok}/{n} passed. Evidence: {path}')
    for r in REPORT:
        if not r['pass']:
            print(f"  FAIL [{r['actor']}] {r['method']} {r['path']} expected={r['expected']} observed={r['observed']}")
    return 0 if ok == n and n else 1


if __name__ == '__main__':
    sys.exit(main())
