#!/usr/bin/env python3
"""Live acceptance test for POST /api/orders/package (PACKAGE-ORDER-CONTRACT.md).

Hotel + car in one order -> one payment intent -> two escrow items and two vouchers with their own windows ->
partner redeem of the car releases only the car escrow. Failure path: the car slot is taken, so the second item of a
new package fails, the hotel hold taken first is released and no order exists. Cancel preview lists both items.
Admin orders list shows the order, a customer gets 403. Flight is covered by test-flight-order.py (needs a configured
seller), it is not part of this fixture.

Reuses the car fixture and helpers of test-car-order.py. Credentials never printed, fixture data removed at the end.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('car_order', HERE / 'test-car-order.py')
car = importlib.util.module_from_spec(spec)
spec.loader.exec_module(car)
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql

hotel_state = {'hotel': None, 'org': None}


def hotel_cleanup():
    hid, oid = hotel_state['hotel'], hotel_state['org']
    if oid:
        psql('payment_db', f"DELETE FROM commission_rates WHERE supplier_organization_id='{oid}'")
    if hid:
        for t in ('hotel_holds', 'hotel_quotes', 'inventory_adjustments', 'daily_rates', 'daily_inventory',
                  'rate_plans', 'room_types'):
            psql('hotel_db', f"DELETE FROM {t} WHERE hotel_id='{hid}'")
        psql('hotel_db', f"DELETE FROM hotels WHERE id='{hid}'")
    if oid:
        psql('auth_db', f"DELETE FROM role_assignments WHERE organization_id='{oid}'")
        psql('auth_db', f"DELETE FROM organization_memberships WHERE organization_id='{oid}'")
        psql('auth_db', f"DELETE FROM organizations WHERE id='{oid}'")


def hotel_fixture(admin, partner):
    org = data(call('POST', '/api/organizations', {'name': f'pkg-hotel-{uuid.uuid4().hex[:6]}',
                                                    'businessType': 'HOTEL', 'legalType': 'COMPANY'}, partner))['id']
    hotel_state['org'] = org
    hotel = data(call('POST', '/api/hotels', {'name': 'Package test hotel', 'city': f'PK{uuid.uuid4().hex[:6]}',
                                              'country': 'TR', 'address': 'Test only', 'starRating': 3,
                                              'organizationId': org}, partner))['id']
    hotel_state['hotel'] = hotel
    call('POST', f'/api/admin/organizations/{org}/approve', token=admin)
    room = data(call('POST', f'/api/hotels/{hotel}/room-types', {'code': 'PK_DBL', 'name': 'PK double', 'maxAdults': 2,
                                                                 'maxChildren': 0, 'maxOccupancy': 2}, partner))
    plan = data(call('POST', f'/api/hotels/{hotel}/rate-plans', {'code': 'PK_FLEX', 'name': 'PK flex', 'mealPlan': 'BREAKFAST',
                                                                 'cancellationPolicy': 'FREE_CANCELLATION',
                                                                 'freeCancellationDaysBefore': 2}, partner))
    start = datetime.date.today() + datetime.timedelta(days=45)
    nights = [(start + datetime.timedelta(days=i)).isoformat() for i in range(2)]
    call('PUT', f'/api/hotels/{hotel}/inventory', {'items': [
        {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 5, 'expectedVersion': None} for n in nights],
        'reason': 'package acceptance fixture'}, partner)
    call('PUT', f'/api/hotels/{hotel}/rates', {'items': [
        {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': n, 'occupancy': 2, 'currency': 'TRY', 'amount': a}
        for n, a in zip(nights, [1000.00, 1500.00])]}, partner)
    s, _, _ = call('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org, 'rate': 0.125},
                   admin)
    check('hotel commission rate created', s in (200, 201), s)
    return hotel, room, plan, nights, (start + datetime.timedelta(days=2)).isoformat()


def main():
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    cust_email = f'pkg-cust-{uuid.uuid4().hex[:10]}@example.test'
    part_email = f'pkg-part-{uuid.uuid4().hex[:10]}@example.test'
    pw_c, pw_p = 'Cust1' + secrets.token_urlsafe(16), 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(cust_email, pw_c), (part_email, pw_p)]:
        car.register(e, pw)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    cust, partner, admin = car.login(cust_email, pw_c), car.login(part_email, pw_p), car.login(admin_email, admin_pw)
    cust_id = psql('auth_db', f"SELECT id FROM users WHERE email='{cust_email}'")

    print('\n=== setup: car org + hotel org ===')
    org_id, branch, _veh, pick_dt, ret_dt, pickup_at, return_at = car._setup_org(admin, partner, part_email)
    hotel, room, plan, nights, check_out = hotel_fixture(admin, partner)

    def quote():
        return data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                                  'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                                  'childrenAges': []}, cust))

    car_item = {'vertical': 'CAR', 'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at, 'driverAge': 30}

    print('\n=== package create ===')
    q1 = quote()
    body = {'items': [{'vertical': 'HOTEL', 'quoteId': q1['quoteId']}, car_item]}
    s, r, _ = call('POST', '/api/orders/package', body, cust)
    check('package create 201', s == 201, (s, err(r)))
    order = data(r)
    oid = order['id']
    car.order_ids.append(oid)
    items = order['items']
    check('one order with 2 items HOTEL+CAR', [i['vertical'] for i in items] == ['HOTEL', 'CAR'], items)
    check('total == sum of items', abs(float(order['totalAmount']) - sum(float(i['unitAmount']) for i in items)) < 0.005,
          order['totalAmount'])
    check('order PENDING with holdExpiresAt', order['status'] == 'PENDING' and bool(order.get('holdExpiresAt')), order['status'])
    holds = psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{oid}'")
    check('hotel hold HELD', holds == 'HELD', holds)
    s, r, _ = call('POST', '/api/orders/package', body, car.login(admin_email, admin_pw))
    check('admin package order 403 ADMIN_CANNOT_PURCHASE', s == 403 and err(r) == 'ADMIN_CANNOT_PURCHASE', (s, err(r)))

    print('\n=== cancel preview per item ===')
    s, r, _ = call('GET', f'/api/orders/{oid}/cancellation-preview', token=cust)
    pv = data(r)
    check('preview lists both items', s == 200 and len(pv['items']) == 2, (s, pv))

    print('\n=== one payment ===')
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust)
    intent = data(r)
    check('one intent for the order total', s in (200, 201) and float(intent['amount']) == float(order['totalAmount']), (s, intent))
    s, r, _ = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('payment AUTHORIZED', s == 200 and data(r)['status'] == 'AUTHORIZED', (s, err(r)))
    check('order CONFIRMED', data(call('GET', f'/api/orders/{oid}', token=cust)[1])['status'] == 'CONFIRMED')
    esc = psql('payment_db', f"SELECT string_agg(vertical||':'||status, ',' ORDER BY vertical) FROM escrow_items WHERE order_id='{oid}'")
    check('two escrow items HELD (CAR, HOTEL)', esc == 'CAR:HELD,HOTEL:HELD', esc)
    check('one payment intent row', psql('payment_db', f"SELECT count(*) FROM payment_intents WHERE order_id='{oid}'") == '1')
    check('hotel hold CONFIRMED', psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{oid}'") == 'CONFIRMED')

    print('\n=== vouchers ===')
    s, r, _ = call('GET', f'/api/orders/{oid}/vouchers', token=cust)
    vs = data(r) or []
    check('2 vouchers, own windows', len(vs) == 2 and len({(v['validFrom'], v['validUntil']) for v in vs}) == 2, vs)
    car_item_id = next(i['id'] for i in items if i['vertical'] == 'CAR')
    car_voucher = next(v for v in vs if v['orderItemId'] == car_item_id)

    print('\n=== redeem car releases only the car escrow ===')
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{car_voucher['voucherId']}'")
    s, r, _ = call('POST', f'/api/partner/organizations/{org_id}/vouchers/redeem', {'payload': car_voucher['payload']}, partner)
    check('car redeem 200', s == 200, (s, err(r)))
    esc = psql('payment_db', f"SELECT string_agg(vertical||':'||status, ',' ORDER BY vertical) FROM escrow_items WHERE order_id='{oid}'")
    check('only CAR released, HOTEL still HELD', esc == 'CAR:RELEASED,HOTEL:HELD', esc)

    print('\n=== failure path: second item unavailable ===')
    before = psql('package_booking_db', f"SELECT count(*) FROM orders WHERE customer_id='{cust_id}'")
    q2 = quote()
    s, r, _ = call('POST', '/api/orders/package', {'items': [{'vertical': 'HOTEL', 'quoteId': q2['quoteId']}, car_item]}, cust)
    check('409 NO_VEHICLE_AVAILABLE with itemIndex 1', s == 409 and err(r) == 'NO_VEHICLE_AVAILABLE' and r.get('itemIndex') == 1, (s, r))
    check('no order created', psql('package_booking_db', f"SELECT count(*) FROM orders WHERE customer_id='{cust_id}'") == before)
    st = psql('hotel_db', f"SELECT string_agg(status, ',') FROM hotel_holds WHERE quote_id='{q2['quoteId']}'")
    check('first hold released (compensation)', st == 'RELEASED', st)

    print('\n=== admin orders list ===')
    s, r, _ = call('GET', '/api/admin/orders?vertical=CAR&status=CONFIRMED&size=100', token=admin)
    page = data(r) or {}
    row = next((o for o in page.get('items', []) if o['id'] == oid), None)
    check('admin list contains the order', s == 200 and row is not None, (s, page.get('total')))
    if row:
        check('row has items, totals, payment state', len(row['items']) == 2 and row['paymentState'] == 'PAID'
              and float(row['totalAmount']) == float(order['totalAmount']), row)
    s, _, _ = call('GET', '/api/admin/orders', token=cust)
    check('customer gets 403 on admin orders', s == 403, s)
    s, _, _ = call('GET', '/api/admin/orders')
    check('anonymous gets 401', s == 401, s)

    print('\n=== legacy deprecation header ===')
    s, _, h = call('GET', '/api/bookings', token=admin)
    check('legacy /api/bookings has Deprecation header', h.get('Deprecation') == 'true', (s, h.get('Deprecation')))

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
        hotel_cleanup()
