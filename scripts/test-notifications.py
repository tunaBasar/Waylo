#!/usr/bin/env python3
"""Live test (NOTIFICATION-CONTRACT.md): order paid -> CONFIRMATION row, paid order cancelled -> REFUND row with the amount,
access rules of /api/notifications through the gateway. Checks the notification API/DB only, no inbox, test users *@example.test.
Reuses helpers from test-flight-order.py. Requires: flight set + notification-service + crm-service + package-booking running.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

here = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('tfo', here / 'test-flight-order.py')
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
check, call, data, psql = t.check, t.call, t.data, t.psql


def wait_row(customer_id, subject_like):
    for _ in range(40):
        row = psql('notification_db', f"SELECT id||'|'||status||'|'||body FROM notifications "
                                      f"WHERE customer_id = '{customer_id}' AND subject LIKE '{subject_like}'")
        if row:
            return row
        time.sleep(1)
    return ''


def main():
    sfx = uuid.uuid4().hex[:10]
    a, b, partner = (f'ntf-{n}-{sfx}@example.test' for n in ('a', 'b', 'p'))
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in (a, b, partner)}
    for e in (partner, a, b):
        t.register(e, pws[e])
    admin = t.login(t.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), t.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD'))
    tp, ta, tb = t.login(partner, pws[partner]), t.login(a, pws[a]), t.login(b, pws[b])
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
    nid = row.split('|')[0] if row else ''
    status = row.split('|')[1] if row else ''
    body = row.split('|', 2)[2] if row else ''
    check('CONFIRMATION row exists, status SUPPRESSED (test domain, nothing delivered)', status == 'SUPPRESSED', row[:60])
    check('confirmation body has item and total', 'Toplam' in body and 'QR kodunuz' in body, body[:80])
    voucher_payloads = psql('package_booking_db', f"SELECT v.payload FROM vouchers v JOIN order_items i ON i.id=v.order_item_id WHERE i.order_id='{order['id']}'")
    check('confirmation body leaks no voucher payload', not voucher_payloads or voucher_payloads.split('\n')[0] not in body)
    check('booking_id is the order id', psql('notification_db', f"SELECT booking_id FROM notifications WHERE id='{nid}'") == order['id'])

    # access rules
    s, bd = call('GET', '/api/notifications/me', token=ta)
    check('A /me lists own confirmation', s == 200 and any(n['id'] == nid for n in data((s, bd)) or []), s)
    s, bd = call('GET', '/api/notifications/me', token=tb)
    check('B /me does not contain A rows', s == 200 and not any(n['id'] == nid for n in data((s, bd)) or []), s)
    s, _ = call('GET', f'/api/notifications/{nid}', token=tb)
    check('B cannot read A notification by id (403/404)', s in (403, 404), s)
    s, _ = call('GET', f'/api/notifications/customer/{customer_id}', token=tb)
    check('B cannot list A by customer id (403)', s == 403, s)
    s, bd = call('GET', f"/api/notifications/booking/{order['id']}", token=tb)
    check('B booking listing is empty', s == 200 and data((s, bd)) == [], s)
    s, _ = call('GET', f'/api/notifications/{nid}', token=ta)
    check('A can read own notification', s == 200, s)
    s, _ = call('GET', '/api/notifications', token=ta)
    check('customer cannot list all (403)', s == 403, s)
    s, _ = call('GET', f'/api/notifications/{nid}', token=admin)
    check('admin reads any notification', s == 200, s)
    s, bd = call('GET', '/api/notifications', token=admin)
    check('admin lists all', s == 200 and any(n['id'] == nid for n in data((s, bd)) or []), s)
    s, _ = call('GET', '/api/notifications')
    check('anonymous -> 401', s == 401, s)
    s, _ = call('POST', f'/api/notifications/{nid}/retry', None, ta)
    check('retry by owner customer -> 403', s == 403, s)
    s, _ = call('POST', f'/api/notifications/{nid}/retry', None, tb)
    check('retry by other customer -> 403', s == 403, s)
    s, bd = call('POST', f'/api/notifications/{nid}/retry', None, admin)
    check('retry by admin -> 200', s == 200 and data((s, bd))['retryCount'] == 1, s)

    # cancel paid order -> refund notification with the amount
    s, bd = call('POST', f"/api/orders/{order['id']}/cancel", None, ta)
    check('paid order cancelled', s == 200, s)
    refund = psql('package_booking_db', f"SELECT coalesce(sum(refund_amount),0) FROM order_items WHERE order_id='{order['id']}'")
    row = wait_row(customer_id, 'Sipari%iptal%')
    rbody = row.split('|', 2)[2] if row else ''
    check('REFUND row exists, status SUPPRESSED (test domain)', row.split('|')[1:2] == ['SUPPRESSED'], row[:60])
    check('refund body carries the refunded amount', refund and f'{float(refund):.2f} TL' in rbody, f'{refund} / {rbody[:100]}')

    failed = [n for n, ok in t.results if not ok]
    print(f'\n{len(t.results) - len(failed)}/{len(t.results)} checks passed')
    psql('notification_db', f"DELETE FROM notifications WHERE customer_id = '{customer_id}'")
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        t.cleanup()
