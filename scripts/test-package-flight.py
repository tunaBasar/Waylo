#!/usr/bin/env python3
"""Live acceptance test: package order with FLIGHT + CAR paid in one payment (PACKAGE-ORDER-CONTRACT.md).

Checks: one intent, two escrow items, flight PNR/ticket, one voucher+QR payload per item, cancellation preview with
both items, whole-order cancel refunds both escrows and frees the seat, ledger balanced. Reuses helpers of
test-car-order.py / test-flight-order.py. Needs flight-service + flight-seat-inventory-service up and the flight seller
org configured (test-flight-order.py does that once). Fixture data is removed at the end.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


car = load('car_order', 'test-car-order.py')
fl = load('flight_order', 'test-flight-order.py')
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def sums(oid):
    return psql('payment_db', f"SELECT string_agg(vertical||':'||status, ',' ORDER BY vertical) FROM escrow_items WHERE order_id='{oid}'")


def main():
    admin_email, admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    sfx = uuid.uuid4().hex[:10]
    ce, pe = f'pkf-c-{sfx}@example.test', f'pkf-p-{sfx}@example.test'
    pc, pp = 'Cust1' + secrets.token_urlsafe(16), 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(ce, pc), (pe, pp)]:
        car.register(e, pw)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    cust, partner, admin = car.login(ce, pc), car.login(pe, pp), car.login(admin_email, admin_pw)

    print('\n=== setup ===')
    org_id, branch, _v, _p, _r, pickup_at, return_at = car._setup_org(admin, partner, pe)
    seller = psql('auth_db', f"SELECT id FROM organizations WHERE name = '{fl.SELLER_NAME}' LIMIT 1")
    check('flight seller org exists', bool(seller))
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1] or {}).get('data') or []
    rates = rates if isinstance(rates, list) else []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)
    fno = 'WP' + str(secrets.randbelow(9000) + 1000)
    fl.flight_numbers.append(fno)
    dep = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=3)).replace(hour=9, minute=30, second=0, microsecond=0)
    iso = lambda d: d.isoformat().replace('+00:00', 'Z')
    s, _, _ = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                   'airline': 'Waylo Test Air', 'departureTime': iso(dep), 'arrivalTime': iso(dep + datetime.timedelta(hours=1))}, admin)
    check('flight created', s == 201, s)
    s, _, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                   'fromRow': 1, 'toRow': 2, 'letters': 'ABC'}, admin)
    check('seats created', s == 201, s)
    s, r, _ = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={dep:%Y-%m-%d}&adults=1')
    offer = next((o for o in (r.get('data') or []) if o['flightNumber'] == fno), None)
    check('offer found', offer is not None, r)

    print('\n=== package create ===')
    car_item = {'vertical': 'CAR', 'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at, 'driverAge': 30}
    flight_item = {'vertical': 'FLIGHT', 'offerId': offer['offerId'], 'seatNumber': '1A',
                   'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}
    s, r, _ = call('POST', '/api/orders/package', {'items': [flight_item, car_item]}, cust)
    check('package create 201', s == 201, (s, err(r)))
    order = data(r)
    oid = order['id']
    car.order_ids.append(oid)
    items = order['items']
    check('items FLIGHT+CAR', [i['vertical'] for i in items] == ['FLIGHT', 'CAR'], items)
    check('total == sum of items', abs(float(order['totalAmount']) - sum(float(i['unitAmount']) for i in items)) < 0.005)
    check('flight item 150.00 from hold', float(items[0]['unitAmount']) == 150.0, items[0]['unitAmount'])
    check('no PNR before payment', (items[0].get('flight') or {}).get('pnr') is None)

    print('\n=== cancellation preview ===')
    s, r, _ = call('GET', f'/api/orders/{oid}/cancellation-preview', token=cust)
    pv = data(r)
    check('preview lists both items', s == 200 and len(pv['items']) == 2, (s, pv))

    print('\n=== one payment ===')
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust)
    intent = data(r)
    check('one intent for total', s in (200, 201) and float(intent['amount']) == float(order['totalAmount']), (s, err(r)))
    s, r, _ = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('payment AUTHORIZED', s == 200 and data(r)['status'] == 'AUTHORIZED', (s, err(r)))
    paid = data(call('GET', f'/api/orders/{oid}', token=cust)[1])
    check('order CONFIRMED', paid['status'] == 'CONFIRMED', paid['status'])
    f = next(i for i in paid['items'] if i['vertical'] == 'FLIGHT').get('flight') or {}
    check('PNR (6) + e-ticket', len(f.get('pnr') or '') == 6 and (f.get('ticketNumber') or '').startswith('WL'), f)
    check('escrow CAR:HELD,FLIGHT:HELD', sums(oid) == 'CAR:HELD,FLIGHT:HELD', sums(oid))
    check('one intent row', psql('payment_db', f"SELECT count(*) FROM payment_intents WHERE order_id='{oid}'") == '1')
    bal = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries WHERE order_id='{oid}'")
    check('ledger balanced', bal in ('0', '0.00'), bal)
    seat = psql('flight_seat_inventory_db', f"SELECT status FROM seat_holds WHERE flight_number='{fno}' AND seat_number='1A' ORDER BY created_at DESC LIMIT 1")
    check('seat hold CONFIRMED', seat == 'CONFIRMED', seat)

    print('\n=== vouchers / QR ===')
    s, r, _ = call('GET', f'/api/orders/{oid}/vouchers', token=cust)
    vs = data(r) or []
    check('2 vouchers each with QR payload', len(vs) == 2 and all(v.get('payload') for v in vs), vs)
    fid = next(i['id'] for i in paid['items'] if i['vertical'] == 'FLIGHT')
    fv = next((v for v in vs if v['orderItemId'] == fid), None)
    check('flight voucher exists', fv is not None)
    if fv:
        dpt = datetime.datetime.fromisoformat(f['departureAt'].replace('Z', '+00:00'))
        vf = datetime.datetime.fromisoformat(fv['validFrom'].replace('Z', '+00:00'))
        check('flight voucher window starts departure-3h', abs((vf - (dpt - datetime.timedelta(hours=3))).total_seconds()) < 2, fv['validFrom'])

    print('\n=== cancel whole order ===')
    s, r, _ = call('POST', f'/api/orders/{oid}/cancel', None, cust)
    check('cancel 200 CANCELLED', s == 200 and data(r)['status'] == 'CANCELLED', (s, err(r)))
    check('both escrows REFUNDED', sums(oid) == 'CAR:REFUNDED,FLIGHT:REFUNDED', sums(oid))
    seat_free = psql('flight_seat_inventory_db', f"SELECT is_available FROM flight_seats WHERE flight_number='{fno}' AND seat_number='1A'")
    check('seat free again', seat_free == 't', seat_free)
    bal = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries WHERE order_id='{oid}'")
    check('ledger balanced after refund', bal in ('0', '0.00'), bal)

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
        fl.cleanup()
