#!/usr/bin/env python3
"""Live test: push device registry (POST/GET/DELETE /api/me/devices, owner-only delete, 401 anonymous) and an order
confirmation creating a PUSH notification row with status SUPPRESSED (EXPO_PUSH_ENABLED=false).
Reuses helpers from test-flight-order.py. Requires: flight set + notification-service + crm-service + package-booking running.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import urllib.parse
import uuid

here = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('tfo', here / 'test-flight-order.py')
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
check, call, data, psql = t.check, t.call, t.data, t.psql


def wait_row(customer_id, subject_like, ntype='PUSH'):
    for _ in range(40):
        row = psql('notification_db', f"SELECT id||'|'||status||'|'||body FROM notifications "
                                      f"WHERE customer_id = '{customer_id}' AND type = '{ntype}' AND subject LIKE '{subject_like}'")
        if row:
            return row
        time.sleep(1)
    return ''


def main():
    sfx = uuid.uuid4().hex[:10]
    a, b, partner = (f'dev-{n}-{sfx}@example.test' for n in ('a', 'b', 'p'))
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in (a, b, partner)}
    for e in (partner, a, b):
        t.register(e, pws[e])
    admin = t.login(t.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), t.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD'))
    tp, ta, tb = t.login(partner, pws[partner]), t.login(a, pws[a]), t.login(b, pws[b])
    tok = 'ExponentPushToken[' + secrets.token_urlsafe(16) + ']'
    q = urllib.parse.quote(tok, safe='')
    s, _ = call('POST', '/api/me/devices', {'token': tok, 'platform': 'ios'})
    check('anonymous register -> 401', s == 401, s)
    s, _ = call('POST', '/api/me/devices', {'token': tok, 'platform': 'ios'}, ta)
    check('register -> 200', s == 200, s)
    s, _ = call('POST', '/api/me/devices', {'token': tok, 'platform': 'android'}, ta)
    check('register again (upsert) -> 200', s == 200, s)
    s, _ = call('POST', '/api/me/devices', {'token': tok, 'platform': 'blackberry'}, ta)
    check('invalid platform -> 400', s == 400, s)
    s, bd = call('GET', '/api/me/devices', None, ta)
    mine = (bd.get('data') or []) if s == 200 else []
    check('list has exactly one row with updated platform', len(mine) == 1 and mine[0]['platform'] == 'android', bd)
    s, bd = call('GET', '/api/me/devices', None, tb)
    check('other user does not see it', s == 200 and not bd.get('data'), bd)
    s, _ = call('DELETE', f'/api/me/devices/{q}', None, tb)
    check('other user delete -> 404', s == 404, s)
    s, _ = call('DELETE', f'/api/me/devices/{q}')
    check('anonymous delete -> 401', s == 401, s)
    s, bd = call('GET', '/api/me/devices', None, ta)
    check('row still exists after foreign delete attempt', s == 200 and len(bd.get('data') or []) == 1, bd)

    seller = t.ensure_seller(admin, tp)
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1].get('data')) or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)

    fno = 'WN' + str(secrets.randbelow(9000) + 1000)
    t.flight_numbers.append(fno)
    dep = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=4)).replace(hour=9, minute=30, second=0, microsecond=0)
    arr = dep + datetime.timedelta(hours=1, minutes=5)
    s, _ = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                                         'airline': 'Waylo Test Air', 'departureTime': dep.isoformat().replace('+00:00', 'Z'),
                                         'arrivalTime': arr.isoformat().replace('+00:00', 'Z')}, admin)
    check('flight created', s == 201, s)
    s, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                                                           'fromRow': 1, 'toRow': 1, 'letters': 'AB'}, admin)
    check('seats created', s == 201, s)
    date = dep.strftime('%Y-%m-%d')
    s, bd = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={date}&adults=1')
    offer = [o for o in (bd.get('data') or []) if o['flightNumber'] == fno][0]
    s, bd = call('POST', '/api/orders/flight', {'offerId': offer['offerId'], 'seatNumber': '1A',
                                                'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}, ta)
    check('order created', s == 201, s)
    order = data((s, bd))
    t.order_ids.append(order['id'])
    customer_id = order['customerId']

    for _ in range(20):  # crm customer (from auth-events) must exist for the e-mail lookup
        if psql('crm_db', f"SELECT count(*) FROM customers WHERE id = '{customer_id}'") not in ('', '0'):
            break
        time.sleep(1)

    s, _, intent = t.pay(order['id'], ta)
    check('payment AUTHORIZED', s == 200 and intent and intent.get('status') == 'AUTHORIZED', s)

    row = wait_row(customer_id, 'Sipari%onayland%')
    check('PUSH row exists, status SUPPRESSED (push disabled)', row.split('|')[1:2] == ['SUPPRESSED'], row[:60])

    s, _ = call('DELETE', f'/api/me/devices/{q}', None, ta)
    check('owner delete -> 204', s == 204, s)
    s, bd = call('GET', '/api/me/devices', None, ta)
    check('list empty after delete', s == 200 and not bd.get('data'), bd)

    failed = [n for n, ok in t.results if not ok]
    print(f'\n{len(t.results) - len(failed)}/{len(t.results)} checks passed')
    psql('notification_db', f"DELETE FROM notifications WHERE customer_id = '{customer_id}'")
    psql('notification_db', "DELETE FROM device_tokens WHERE token LIKE 'ExponentPushToken[%' AND token = '" + tok + "'")
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        t.cleanup()
