#!/usr/bin/env python3
"""Live test: flight cancellation -> notification-service notifies the customer of a CONFIRMED flight order item.

Flow: order (flight) -> pay -> admin cancels the flight (flight-service publishes FLIGHT_CANCELLED on Kafka)
-> notification-service looks up package-booking /internal/orders/flight-items and writes a notifications row.
Also checks the internal endpoint directly (token required, item listed, cancelled items excluded).
Reuses helpers from test-flight-order.py. Requires: flight set + notification-service + crm-service running.
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
PB = 'http://127.0.0.1:8085'


def main():
    internal = t.env_value('INTERNAL_API_TOKEN')
    sfx = uuid.uuid4().hex[:10]
    cust, partner = f'fl-ntf-c-{sfx}@example.test', f'fl-ntf-p-{sfx}@example.test'
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in (cust, partner)}
    for e in (partner, cust):
        t.register(e, pws[e])
    admin = t.login(t.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), t.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD'))
    tp, tc = t.login(partner, pws[partner]), t.login(cust, pws[cust])
    seller = t.ensure_seller(admin, tp)
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1].get('data')) or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)

    fno = 'WN' + str(secrets.randbelow(9000) + 1000)
    t.flight_numbers.append(fno)
    dep = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=4)).replace(hour=9, minute=30, second=0, microsecond=0)
    arr = dep + datetime.timedelta(hours=1, minutes=5)
    s, b = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                                         'airline': 'Waylo Test Air', 'departureTime': dep.isoformat().replace('+00:00', 'Z'),
                                         'arrivalTime': arr.isoformat().replace('+00:00', 'Z')}, admin)
    check('flight created', s == 201, s)
    flight_id = data((s, b))['id']
    s, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                                                           'fromRow': 1, 'toRow': 1, 'letters': 'AB'}, admin)
    check('seats created', s == 201, s)
    date = dep.strftime('%Y-%m-%d')
    s, b = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={date}&adults=1')
    offer = [o for o in (b.get('data') or []) if o['flightNumber'] == fno][0]
    s, b = call('POST', '/api/orders/flight', {'offerId': offer['offerId'], 'seatNumber': '1A',
                                                'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}, tc)
    check('order created', s == 201, s)
    order = data((s, b))
    t.order_ids.append(order['id'])
    item_id = order['items'][0]['id']
    s, b, intent = t.pay(order['id'], tc)
    check('payment AUTHORIZED', s == 200 and intent and intent.get('status') == 'AUTHORIZED', s)
    customer_id = data(call('GET', f"/api/orders/{order['id']}", token=tc))['customerId']

    hdr = {'X-Internal-Token': internal}
    q = f'/internal/orders/flight-items?flightNumber={fno}&departureDate={date}'
    s, _ = call('GET', q, base=PB)
    check('flight-items without token -> 401', s == 401, s)
    s, b = call('GET', q, base=PB, headers=hdr)
    ids = [i['orderItemId'] for i in (data((s, b)) or [])]
    check('flight-items lists the confirmed item', s == 200 and ids == [item_id], (s, ids))
    s, b = call('GET', f'/internal/orders/flight-items?flightNumber={fno}&departureDate=2000-01-01', base=PB, headers=hdr)
    check('other departure date -> empty', s == 200 and data((s, b)) == [], (s, b))

    # crm customer (from auth-events) must exist for the email lookup
    for _ in range(20):
        if psql('crm_db', f"SELECT count(*) FROM customers WHERE id = '{customer_id}'") not in ('', '0'):
            break
        time.sleep(1)

    s, _ = call('POST', f'/api/flights/{flight_id}/cancel', None, admin)
    check('admin cancelled flight', s == 200, s)
    row = ''
    for _ in range(30):
        row = psql('notification_db', f"SELECT status||'|'||booking_id||'|'||recipient FROM notifications "
                                      f"WHERE customer_id = '{customer_id}' AND subject LIKE 'Ucusunuz%'")
        if row:
            break
        time.sleep(1)
    check('notification stored for the customer (SUPPRESSED test domain, item id)', row.startswith('SUPPRESSED|' + item_id), 'none' if not row else 'mismatch')
    check('recipient is the customer email', row.endswith('|' + cust), 'mismatch')

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
