#!/usr/bin/env python3
"""Live acceptance test for online check-in (CHECKIN-CONTRACT.md), through the gateway + Postgres.

Reuses helpers and fixtures of test-flight-order.py (seller org, FLIGHT commission, cleanup).
Checks: before window 422 CHECKIN_NOT_OPEN, inside window CHECKED_IN, repeat same result, other user 403,
cancelled order item 409, admin list shows it, /api/checkins/me, internal endpoint needs token, legacy Deprecation header.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import urllib.request
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('tfo', HERE / 'test-flight-order.py')
t = importlib.util.module_from_spec(spec)
sys.modules['tfo'] = t
spec.loader.exec_module(t)
call, data, check, psql = t.call, t.data, t.check, t.psql


def make_flight(admin, fno, dep):
    arr = dep + datetime.timedelta(hours=1, minutes=5)
    iso = lambda d: d.isoformat().replace('+00:00', 'Z')
    s, _ = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                                         'airline': 'Waylo Test Air', 'departureTime': iso(dep), 'arrivalTime': iso(arr)}, admin)
    ok1 = s == 201
    s, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                                                           'fromRow': 1, 'toRow': 2, 'letters': 'ABC'}, admin)
    return ok1 and s == 201


def order_and_pay(token, fno, dep, seat):
    date = dep.strftime('%Y-%m-%d')
    s, b = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={date}&adults=1')
    offers = [o for o in (b.get('data') or []) if o['flightNumber'] == fno]
    if not offers:
        return None, None
    s, b = call('POST', '/api/orders/flight', {'offerId': offers[0]['offerId'], 'seatNumber': seat,
                                               'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}, token)
    if s != 201:
        return None, None
    order = data((s, b))
    t.order_ids.append(order['id'])
    ps, _, intent = t.pay(order['id'], token)
    if ps != 200:
        return None, None
    s, b = call('GET', f"/api/orders/{order['id']}", token=token)
    return order['id'], data((s, b))['items'][0]['id']


def main():
    admin_email, admin_pw = t.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), t.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    internal = t.env_value('INTERNAL_API_TOKEN')
    sfx = uuid.uuid4().hex[:10]
    cust1, cust2, partner = (f'ci-c1-{sfx}@example.test', f'ci-c2-{sfx}@example.test', f'ci-p-{sfx}@example.test')
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in (cust1, cust2, partner)}
    for e in (partner, cust1, cust2):
        t.register(e, pws[e])
    admin = t.login(admin_email, admin_pw)
    t_partner, t1, t2 = t.login(partner, pws[partner]), t.login(cust1, pws[cust1]), t.login(cust2, pws[cust2])
    seller = t.ensure_seller(admin, t_partner)
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1].get('data')) or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)

    now = datetime.datetime.now(datetime.timezone.utc).replace(second=0, microsecond=0)
    f_far, f_in = 'WC' + str(secrets.randbelow(9000) + 1000), 'WD' + str(secrets.randbelow(9000) + 1000)
    t.flight_numbers.extend([f_far, f_in])
    dep_far, dep_in = now + datetime.timedelta(days=3), now + datetime.timedelta(hours=10)
    check('fixture flights created', make_flight(admin, f_far, dep_far) and make_flight(admin, f_in, dep_in))

    print('\n=== before the window ===')
    o_far, item_far = order_and_pay(t1, f_far, dep_far, '1A')
    check('far flight order paid', bool(item_far))
    s, b = call('POST', f'/api/checkins/order-items/{item_far}', None, t1)
    check('check-in before window 422 CHECKIN_NOT_OPEN', s == 422 and b.get('error') == 'CHECKIN_NOT_OPEN', (s, b.get('error')))

    print('\n=== inside the window ===')
    o_in, item_in = order_and_pay(t1, f_in, dep_in, '2B')
    check('near flight order paid', bool(item_in))
    s, b = call('POST', f'/api/checkins/order-items/{item_in}', None, t2)
    check('other user 403', s == 403, (s, b.get('error')))
    s, b = call('POST', f'/api/checkins/order-items/{item_in}', None, admin)
    check('admin cannot check in for a customer (403)', s == 403, s)
    s, b = call('POST', f'/api/checkins/order-items/{item_in}', None, t1)
    c1 = data((s, b)) or {}
    check('check-in -> CHECKED_IN, seat 2B', s == 200 and c1.get('status') == 'CHECKED_IN' and c1.get('seatNumber') == '2B', (s, b.get('error')))
    check('snapshot has pnr, passenger, flight', len(c1.get('pnr') or '') == 6 and c1.get('passengerName') == 'Ayse Demir'
          and c1.get('flightNumber') == f_in and c1.get('checkedInAt'), c1)
    s, b = call('POST', f'/api/checkins/order-items/{item_in}', None, t1)
    c2 = data((s, b)) or {}
    check('repeat returns the same check-in', s == 200 and c2.get('id') == c1.get('id') and c2.get('checkedInAt') == c1.get('checkedInAt'), c2)
    rows = psql('checkin_db', f"SELECT count(*) FROM checkins WHERE order_item_id='{item_in}'")
    check('exactly one row', rows == '1', rows)
    s, b = call('GET', '/api/checkins/me', token=t1)
    check('/me lists it', s == 200 and any(c['orderItemId'] == item_in for c in (data((s, b)) or [])), s)
    s, b = call('GET', '/api/checkins/me', token=t2)
    check('/me of other user is empty', s == 200 and not data((s, b)), s)

    print('\n=== cancelled / admin / internal ===')
    o_c, item_c = order_and_pay(t2, f_in, dep_in, '2C')
    check('third order paid', bool(item_c))
    s, b = call('POST', f'/api/orders/{o_c}/cancel', None, t2)
    check('order cancelled', s == 200, (s, b.get('error')))
    s, b = call('POST', f'/api/checkins/order-items/{item_c}', None, t2)
    check('cancelled item 409 ORDER_ITEM_CANCELLED', s == 409 and b.get('error') == 'ORDER_ITEM_CANCELLED', (s, b.get('error')))
    s, b = call('GET', '/api/admin/checkins?page=0&size=100', token=admin)
    items = (data((s, b)) or {}).get('items', [])
    check('admin list shows the check-in', s == 200 and any(c['orderItemId'] == item_in for c in items), s)
    check('admin list paged', 'total' in (data((s, b)) or {}) and 'page' in (data((s, b)) or {}))
    s, _ = call('GET', '/api/admin/checkins', token=t1)
    check('customer cannot list admin (403)', s == 403, s)
    s, _ = call('GET', f'/internal/orders/items/{item_in}', base='http://127.0.0.1:8085')
    check('internal endpoint needs token (401)', s == 401, s)
    s, b = call('GET', f'/internal/orders/items/{item_in}', base='http://127.0.0.1:8085', headers={'X-Internal-Token': internal})
    check('internal endpoint with token 200', s == 200 and data((s, b)).get('vertical') == 'FLIGHT', s)
    req = urllib.request.Request('http://127.0.0.1:8089/api/checkins', headers={'X-User-Id': str(uuid.uuid4())})
    with urllib.request.urlopen(req, timeout=15) as r:
        check('legacy endpoint has Deprecation: true', r.headers.get('Deprecation') == 'true', dict(r.headers))

    failed = [n for n, ok in t.results if not ok]
    print(f'\n{len(t.results) - len(failed)}/{len(t.results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        t.cleanup()
        if t.order_ids:
            ids = ','.join("'" + o + "'" for o in t.order_ids)
            psql('checkin_db', f'DELETE FROM checkins WHERE order_id IN ({ids})')
