#!/usr/bin/env python3
"""Live test: hotel hold lost between order creation and payment (PACKAGE-ORDER-CONTRACT.md section 4) and a flight
seat race (two customers, one seat, exactly one order).

A: single hotel order, hold expires (expires_at in the past) -> payment is rejected, no escrow, inventory counters 0/0.
B: package HOTEL+CAR, hotel hold released -> payment OK, hotel item REFUND (net), car kept, no hotel voucher,
   inventory held/confirmed both 0 for the hotel nights.
C: two customers create a flight order for the same seat concurrently -> exactly one 201, the other 409.
Reuses helpers of test-package-order.py / test-flight-order.py. Fixture data removed at the end.
"""
import concurrent.futures
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
pk = load('pkg_order', 'test-package-order.py')
fl = load('flight_order', 'test-flight-order.py')
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql
HOTEL = 'http://127.0.0.1:8083'


def counters(hotel, nights):
    n = ','.join(f"'{x}'" for x in nights)
    return psql('hotel_db', f"SELECT coalesce(sum(held),0)||'/'||coalesce(sum(confirmed),0) FROM daily_inventory "
                            f"WHERE hotel_id='{hotel}' AND stay_date IN ({n})")


def main():
    admin_email, admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    sfx = uuid.uuid4().hex[:10]
    emails = {k: f'hx-{k}-{sfx}@example.test' for k in ('c1', 'c2', 'p')}
    pws = {k: k.upper() + '1' + secrets.token_urlsafe(16) for k in emails}
    for k, e in emails.items():
        car.register(e, pws[k])
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        car.created_emails.append(e)
        time.sleep(13)
    c1, c2, partner = (car.login(emails[k], pws[k]) for k in ('c1', 'c2', 'p'))
    admin = car.login(admin_email, admin_pw)

    print('\n=== setup ===')
    org_id, branch, _v, _p, _r, pickup_at, return_at = car._setup_org(admin, partner, emails['p'])
    hotel, room, plan, nights, check_out = pk.hotel_fixture(admin, partner)

    def quote():
        return data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                                  'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                                  'childrenAges': []}, c1))

    check('baseline counters 0/0', counters(hotel, nights) == '0/0', counters(hotel, nights))
    car_item = {'vertical': 'CAR', 'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at, 'driverAge': 30}

    def pay(oid, token=c1):
        s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, token)
        if s not in (200, 201):
            return s, r, None
        iid = data(r)['id']
        s, r, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, token)
        return s, r, iid

    print('\n=== A: single hotel order, hold expires before payment ===')
    s, r, _ = call('POST', '/api/orders/hotel', {'quoteId': quote()['quoteId']}, c1)
    check('hotel order 201', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    check('counters 1/0 while held', counters(hotel, nights) == '2/0', counters(hotel, nights))
    psql('hotel_db', f"UPDATE hotel_holds SET expires_at = now() - interval '1 minute' WHERE order_correlation_id='{oid}'")
    s, r, iid = pay(oid)
    check('payment not successful', s >= 400, (s, err(r)))
    check('order not CONFIRMED', data(call('GET', f'/api/orders/{oid}', token=c1)[1])['status'] != 'CONFIRMED')
    check('no escrow items', psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id='{oid}'") == '0')
    if iid:
        st = psql('payment_db', f"SELECT status FROM payment_intents WHERE id='{iid}'")
        check('intent not AUTHORIZED/CAPTURED', st not in ('AUTHORIZED', 'CAPTURED'), st)
    hs = psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{oid}'")
    check('hold EXPIRED or RELEASED', hs in ('EXPIRED', 'RELEASED'), hs)
    check('counters back to 0/0 (nothing leaked)', counters(hotel, nights) == '0/0', counters(hotel, nights))

    print('\n=== B: package HOTEL+CAR, hotel hold released before payment ===')
    s, r, _ = call('POST', '/api/orders/package', {'items': [{'vertical': 'HOTEL', 'quoteId': quote()['quoteId']}, car_item]}, c1)
    check('package 201', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    ref = psql('package_booking_db', f"SELECT fulfillment_ref FROM order_items WHERE order_id='{oid}' AND vertical='HOTEL'")
    tok = {'X-Internal-Token': car.env_value('INTERNAL_API_TOKEN')}
    s, _, _ = call('POST', f'/internal/hotel-holds/{ref}/release', base=HOTEL, headers=tok)
    check('hotel hold released', s == 200, s)
    s, r, _ = pay(oid)
    check('payment call 200 (partial refund, no silent 5xx)', s == 200, (s, err(r)))
    paid = data(call('GET', f'/api/orders/{oid}', token=c1)[1])
    hi = next(i for i in paid['items'] if i['vertical'] == 'HOTEL')
    ci = next(i for i in paid['items'] if i['vertical'] == 'CAR')
    check('order CONFIRMED', paid['status'] == 'CONFIRMED', paid['status'])
    check('hotel item REFUND == net', hi.get('cancelOutcome') == 'REFUND'
          and float(hi.get('refundAmount') or 0) == float(hi['unitAmount']), hi)
    check('car item kept', not ci.get('cancelOutcome'), ci.get('cancelOutcome'))
    esc = psql('payment_db', f"SELECT string_agg(vertical||':'||status, ',' ORDER BY vertical) FROM escrow_items WHERE order_id='{oid}'")
    check('escrow CAR:HELD,HOTEL:REFUNDED', esc == 'CAR:HELD,HOTEL:REFUNDED', esc)
    vs = data(call('GET', f'/api/orders/{oid}/vouchers', token=c1)[1]) or []
    check('only car has voucher', [v['orderItemId'] for v in vs] == [ci['id']], vs)
    check('hotel counters 0/0 (no confirmed room leaked)', counters(hotel, nights) == '0/0', counters(hotel, nights))

    print('\n=== C: flight seat race, two customers one seat ===')
    seller = psql('auth_db', f"SELECT id FROM organizations WHERE name = '{fl.SELLER_NAME}' LIMIT 1")
    check('flight seller org exists', bool(seller))
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1] or {}).get('data') or []
    if not any(x.get('vertical') == 'FLIGHT' and str(x.get('supplierOrganizationId')) == seller for x in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)
    fno = 'WH' + str(secrets.randbelow(9000) + 1000)
    fl.flight_numbers.append(fno)
    dep = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=3)).replace(hour=9, minute=30, second=0, microsecond=0)
    iso = lambda d: d.isoformat().replace('+00:00', 'Z')
    s, _, _ = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                   'airline': 'Waylo Test Air', 'departureTime': iso(dep), 'arrivalTime': iso(dep + datetime.timedelta(hours=1))}, admin)
    check('flight created', s == 201, s)
    s, _, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                   'fromRow': 1, 'toRow': 1, 'letters': 'AB'}, admin)
    check('seats created', s == 201, s)
    s, r, _ = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={dep:%Y-%m-%d}&adults=1')
    offer = next((o for o in (r.get('data') or []) if o['flightNumber'] == fno), None)
    check('offer found', offer is not None, r)
    body = {'offerId': offer['offerId'], 'seatNumber': '1A', 'passengerFirstName': 'Race', 'passengerLastName': 'Test'}
    start = time.time() + 1.5

    def buy(tok):
        while time.time() < start:
            time.sleep(0.003)
        return call('POST', '/api/orders/flight', body, tok)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:
        res = list(ex.map(buy, [c1, c2]))
    for s, r, _ in res:
        if s == 201:
            car.order_ids.append(data(r)['id'])
    codes = sorted(s for s, _, _ in res)
    check('exactly one 201 and one 409', codes == [201, 409], [(s, err(r)) for s, r, _ in res])
    win_tok = c1 if res[0][0] == 201 else c2
    s, r, _ = pay(data(next(r for s, r, _ in res if s == 201))['id'], win_tok)
    check('winner pays OK', s == 200, (s, err(r)))
    live = psql('flight_seat_inventory_db',
                f"SELECT count(*) FROM seat_holds WHERE flight_number='{fno}' AND seat_number='1A' AND status IN ('HELD','CONFIRMED')")
    check('exactly one live hold on the seat', live == '1', live)

    failed = [n for n, ok in car.results if not ok]
    print(f'\n{len(car.results) - len(failed)}/{len(car.results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        try:
            pk.hotel_cleanup()
        finally:
            try:
                car.cleanup()
            finally:
                fl.cleanup()
