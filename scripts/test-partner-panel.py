#!/usr/bin/env python3
"""Live acceptance test for the partner panel backends: order-items, members/role-grants, earnings.

Org A (hotel, one paid order) vs org B (other partner): B gets 403 on A's orders, members and earnings, a plain customer
gets 403 too. Member list carries only userId/email/name/role/grantId/createdAt. Grant + revoke of a staff member
(staff can read orders/earnings but not the member list), the last ORGANIZATION_OWNER grant cannot be removed, earnings
totals equal the escrow rows of the paid order. Reuses fixtures of test-package-order.py. Fixture data removed at the end.
"""
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


pkg = load('pkg_order', 'test-package-order.py')
car = pkg.car
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def main():
    admin = car.login(car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD'))
    tag = uuid.uuid4().hex[:10]
    emails = {k: f'pp-{k}-{tag}@example.test' for k in ('cust', 'a', 'b', 'staff')}
    pws = {k: k[0].upper() + '1' + secrets.token_urlsafe(16) for k in emails}
    for k, e in emails.items():
        car.register(e, pws[k])
        car.created_emails.append(e)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    tok = {k: car.login(emails[k], pws[k]) for k in emails}
    staff_id = psql('auth_db', f"SELECT id FROM users WHERE email='{emails['staff']}'")
    a_id = psql('auth_db', f"SELECT id FROM users WHERE email='{emails['a']}'")

    print('\n=== setup: hotel org A (paid order) and org B ===')
    hotel, room, plan, nights, check_out = pkg.hotel_fixture(admin, tok['a'])
    org_a = pkg.hotel_state['org']
    org_b = data(call('POST', '/api/organizations', {'name': f'pp-b-{tag}', 'businessType': 'HOTEL',
                                                     'legalType': 'COMPANY'}, tok['b']))['id']
    car.created_orgs.append(org_b)
    q = data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                          'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                          'childrenAges': []}, tok['cust']))
    s, r, _ = call('POST', '/api/orders/package', {'items': [{'vertical': 'HOTEL', 'quoteId': q['quoteId']}]}, tok['cust'])
    check('order created', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    intent = data(call('POST', f'/api/payments/orders/{oid}/intent', None, tok['cust'])[1])
    s, r, _ = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, tok['cust'])
    check('payment AUTHORIZED', s == 200, (s, err(r)))

    print('\n=== orders ===')
    base = f'/api/partner/organizations/{org_a}'
    s, r, _ = call('GET', f'{base}/order-items?status=CONFIRMED', token=tok['a'])
    page = data(r) or {}
    items = page.get('items', [])
    check('A sees exactly its order item', s == 200 and len(items) == 1 and items[0]['orderId'] == oid, (s, page))
    if items:
        check('row has vertical HOTEL and voucher status', items[0]['vertical'] == 'HOTEL' and items[0]['voucherStatus'] == 'ACTIVE', items[0])
    s, r, _ = call('GET', f'{base}/order-items?status=CANCELLED', token=tok['a'])
    check('status filter excludes it', s == 200 and (data(r) or {}).get('total') == 0, (s, r))
    s, r, _ = call('GET', f'/api/partner/organizations/{org_b}/order-items', token=tok['b'])
    check('B sees none of A', s == 200 and (data(r) or {}).get('total') == 0, (s, r))
    for who in ('b', 'cust'):
        s, _, _ = call('GET', f'{base}/order-items', token=tok[who])
        check(f'{who} gets 403 on A orders', s == 403, s)

    print('\n=== members ===')
    s, r, _ = call('GET', f'/api/organizations/{org_a}/members', token=tok['a'])
    members = data(r) or []
    check('owner lists members', s == 200 and any(m['userId'] == a_id for m in members), (s, members))
    owner = next((m for m in members if m['role'] == 'ORGANIZATION_OWNER' and m['userId'] == a_id), None)
    check('row fields only', owner is not None and set(owner) == {'userId', 'email', 'name', 'role', 'scopeType', 'scopeId',
                                                                  'grantId', 'createdAt'}, owner)
    for who in ('b', 'cust'):
        s, _, _ = call('GET', f'/api/organizations/{org_a}/members', token=tok[who])
        check(f'{who} gets 403 on A members', s == 403, s)

    print('\n=== grant + revoke ===')
    s, r, _ = call('POST', f'/api/organizations/{org_a}/role-grants',
                   {'email': 'nobody-' + tag + '@example.test', 'role': 'HOTEL_STAFF', 'hotelId': hotel}, tok['a'])
    check('unknown email 404 kullanici bulunamadi', s == 404 and 'kullanici bulunamadi' in str(r), (s, r))
    s, r, _ = call('POST', f'/api/organizations/{org_a}/role-grants',
                   {'email': emails['staff'], 'role': 'HOTEL_STAFF', 'hotelId': hotel}, tok['a'])
    check('grant staff 201', s == 201, (s, err(r)))
    grant = data(r)['grantId']
    members = data(call('GET', f'/api/organizations/{org_a}/members', token=tok['a'])[1]) or []
    check('staff listed', any(m['grantId'] == grant and m['userId'] == staff_id for m in members), members)
    s, _, _ = call('GET', f'/api/organizations/{org_a}/members', token=tok['staff'])
    check('staff cannot list members (403)', s == 403, s)
    s, _, _ = call('GET', f'{base}/order-items', token=tok['staff'])
    check('staff can read orders', s == 200, s)
    s, _, _ = call('DELETE', f'/api/organizations/{org_a}/role-grants/{grant}', token=tok['a'])
    check('revoke 204', s == 204, s)
    s, _, _ = call('GET', f'{base}/order-items', token=tok['staff'])
    check('revoked staff gets 403', s == 403, s)

    print('\n=== last owner protection ===')
    s, r, _ = call('DELETE', f'/api/organizations/{org_a}/role-grants/{owner["grantId"]}', token=tok['a']) if owner else (0, {}, {})
    check('owner grant cannot be removed (400)', s == 400, (s, r))
    s, _, _ = call('GET', f'/api/organizations/{org_a}/members', token=tok['a'])
    check('owner still has access', s == 200, s)

    print('\n=== earnings ===')
    s, r, _ = call('GET', f'{base}/earnings', token=tok['a'])
    e = data(r) or {}
    rows = e.get('items', [])
    check('earnings 200 with one row', s == 200 and len(rows) == 1 and rows[0]['orderItemId'] == items[0]['orderItemId'], (s, e))
    db = psql('payment_db', f"SELECT gross_amount||'|'||commission_amount||'|'||supplier_amount FROM escrow_items WHERE order_id='{oid}'")
    g, c, n = (float(x) for x in db.split('|'))
    if rows:
        row = rows[0]
        check('row matches escrow', (float(row['gross']), float(row['commission']), float(row['partnerNet'])) == (g, c, n)
              and row['escrowState'] == 'HELD' and row['vertical'] == 'HOTEL', (row, db))
        check('net = gross - commission', abs(g - c - n) < 0.005, db)
    t = e.get('totals', {})
    check('totals: held == net, others 0', float(t.get('held', -1)) == n and float(t.get('released', -1)) == 0
          and float(t.get('refunded', -1)) == 0, t)
    s, r, _ = call('GET', f'{base}/earnings?from=2000-01-01&to=2000-01-02', token=tok['a'])
    check('date filter excludes it', s == 200 and (data(r) or {}).get('items') == [], (s, r))
    for who in ('b', 'cust'):
        s, _, _ = call('GET', f'{base}/earnings', token=tok[who])
        check(f'{who} gets 403 on A earnings', s == 403, s)
    s, _, _ = call('GET', f'{base}/earnings')
    check('anonymous 401', s == 401, s)
    s, r, _ = call('GET', f'/api/partner/organizations/{org_b}/earnings', token=tok['b'])
    check('B earnings empty', s == 200 and (data(r) or {}).get('items') == [], (s, r))

    failed = [n for n, ok in car.results if not ok]
    print(f'\n{len(car.results) - len(failed)}/{len(car.results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        car.cleanup()
        pkg.hotel_cleanup()
