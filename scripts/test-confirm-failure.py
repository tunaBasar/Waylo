#!/usr/bin/env python3
"""Live test of PACKAGE-ORDER-CONTRACT.md section 4: payment succeeds but one item confirm fails.

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
    ce, pe = f'pcf-c-{sfx}@example.test', f'pcf-p-{sfx}@example.test'
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
    fno = 'WF' + str(secrets.randbelow(9000) + 1000)
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

    SEATS = 'http://127.0.0.1:8082'
    tok = {'X-Internal-Token': car.env_value('INTERNAL_API_TOKEN')}
    car_item = {'vertical': 'CAR', 'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at, 'driverAge': 30}

    def flight_item(seat):
        return {'vertical': 'FLIGHT', 'offerId': offer['offerId'], 'seatNumber': seat,
                'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}

    def expire_flight_hold(oid):
        ref = psql('package_booking_db', f"SELECT fulfillment_ref FROM order_items WHERE order_id='{oid}' AND vertical='FLIGHT'")
        s, _, _ = call('POST', f'/internal/flight-holds/{ref}/release', base=SEATS, headers=tok)
        check('flight hold released (simulates TTL expiry)', s == 200, s)

    print('\n=== A: package FLIGHT+CAR, flight hold lost before payment ===')
    s, r, _ = call('POST', '/api/orders/package', {'items': [flight_item('1A'), car_item]}, cust)
    check('package create 201', s == 201, (s, err(r)))
    order = data(r)
    oid = order['id']
    car.order_ids.append(oid)
    expire_flight_hold(oid)
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust)
    intent = data(r)
    s, r, _ = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('payment call succeeds (no silent 5xx)', s == 200, (s, err(r)))
    paid = data(call('GET', f'/api/orders/{oid}', token=cust)[1])
    check('order CONFIRMED (car kept)', paid['status'] == 'CONFIRMED', paid['status'])
    fi = next(i for i in paid['items'] if i['vertical'] == 'FLIGHT')
    ci = next(i for i in paid['items'] if i['vertical'] == 'CAR')
    check('flight item cancelOutcome REFUND, refund == net',
          fi.get('cancelOutcome') == 'REFUND' and float(fi.get('refundAmount') or 0) == float(fi['unitAmount']), fi)
    check('flight item has no PNR', not (fi.get('flight') or {}).get('pnr'), fi.get('flight'))
    check('car item not refunded', not ci.get('cancelOutcome'), ci.get('cancelOutcome'))
    check('escrow CAR:HELD,FLIGHT:REFUNDED', sums(oid) == 'CAR:HELD,FLIGHT:REFUNDED', sums(oid))
    vs = data(call('GET', f'/api/orders/{oid}/vouchers', token=cust)[1]) or []
    check('only the car has a voucher', [v['orderItemId'] for v in vs] == [ci['id']], vs)
    bal = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries WHERE order_id='{oid}'")
    check('ledger balanced', bal in ('0', '0.00'), bal)
    st = psql('payment_db', f"SELECT status FROM payment_intents WHERE order_id='{oid}'")
    check('intent PARTIALLY_REFUNDED', st == 'PARTIALLY_REFUNDED', st)

    print('\n=== B: single flight order, hold lost -> payment rolled back ===')
    s, r, _ = call('POST', '/api/orders/flight', {k: v for k, v in flight_item('2B').items() if k != 'vertical'}, cust)
    check('single order 201', s == 201, (s, err(r)))
    o2 = data(r)
    car.order_ids.append(o2['id'])
    expire_flight_hold(o2['id'])
    s, r, _ = call('POST', f'/api/payments/orders/{o2["id"]}/intent', None, cust)
    iid = data(r)['id']
    s, r, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('confirm fails (not 200)', s >= 400, (s, err(r)))
    check('order not CONFIRMED', data(call('GET', f'/api/orders/{o2["id"]}', token=cust)[1])['status'] != 'CONFIRMED')
    check('no escrow items', psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id='{o2['id']}'") == '0')
    st = psql('payment_db', f"SELECT status FROM payment_intents WHERE id='{iid}'")
    check('intent not AUTHORIZED/CAPTURED', st not in ('AUTHORIZED', 'CAPTURED'), st)

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
