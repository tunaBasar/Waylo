#!/usr/bin/env python3
"""Live test of the crm loyalty path (CRM-CONTRACT.md) with loyalty ENABLED.

Needs crm-service started with scripts/docker-compose.loyalty.yml (1 point per TRY, silver 3100, gold 3300; the car item is 3000 TRY):
  docker compose -f docker-compose.yml -f scripts/docker-compose.loyalty.yml up -d --no-deps --no-build crm-service
and restored afterwards with: docker compose up -d --no-deps --no-build crm-service

Checks: /me shows loyaltyEnabled; no points on a confirmed but unredeemed order; points = floor(gross) after voucher
redeem; duplicate ORDER_ITEM_REDEEMED (same orderItemId, injected on Kafka) is idempotent; tiers move up with more
points; non-TRY events ignored; cancelling/refunding an order does NOT revoke points (contract: OPEN decision, no
reversal implemented). Fixture data is removed at the end.
"""
import importlib.util
import json
import pathlib
import secrets
import subprocess
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('car_order', HERE / 'test-car-order.py')
car = importlib.util.module_from_spec(spec)
spec.loader.exec_module(car)
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def publish(cust_id, item_id, amount, currency='TRY'):
    ev = {'eventId': str(uuid.uuid4()), 'eventType': 'ORDER_ITEM_REDEEMED', 'source': 'loyalty-test',
          'orderId': str(uuid.uuid4()), 'orderItemId': item_id, 'customerId': cust_id, 'currency': currency, 'amount': amount}
    line = '__TypeId__:orderItemRedeemed\t' + json.dumps(ev)
    subprocess.run(['docker', 'exec', '-i', 'waylo-kafka', 'kafka-console-producer', '--bootstrap-server', 'kafka:29092',
                    '--topic', 'order-events', '--property', 'parse.headers=true'],
                   input=line + '\n', text=True, capture_output=True)


def crm(cust_id):
    r = psql('crm_db', f"SELECT loyalty_points||'|'||tier FROM customers WHERE id='{cust_id}'")
    p, t = r.split('|') if r else ('-1', '?')
    return int(p), t


def wait_points(cust_id, expected, secs=30):
    for _ in range(secs):
        if crm(cust_id)[0] == expected:
            return True
        time.sleep(1)
    return False


def main():
    admin_email, admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    ce, pe = f'loy-c-{uuid.uuid4().hex[:10]}@example.test', f'loy-p-{uuid.uuid4().hex[:10]}@example.test'
    pc, pp = 'Cust1' + secrets.token_urlsafe(16), 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(ce, pc), (pe, pp)]:
        car.register(e, pw)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    cust, partner, admin = car.login(ce, pc), car.login(pe, pp), car.login(admin_email, admin_pw)
    cust_id = psql('auth_db', f"SELECT id FROM users WHERE email='{ce}'")
    for _ in range(20):
        if psql('crm_db', f"SELECT count(*) FROM customers WHERE id='{cust_id}'") == '1':
            break
        time.sleep(1)

    print('\n=== enabled mode ===')
    s, r, _ = call('GET', '/api/customers/me', token=cust)
    me = data(r) or {}
    check('/me loyaltyEnabled true, 0 points', s == 200 and me.get('loyaltyEnabled') is True and me.get('loyaltyPoints') == 0, (s, me))

    org_id, branch, _v, _p, _r, pickup_at, return_at = car._setup_org(admin, partner, pe)
    car_item = {'vertical': 'CAR', 'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at, 'driverAge': 30}
    s, r, _ = call('POST', '/api/orders/package', {'items': [car_item]}, cust)
    check('order created', s == 201, (s, err(r)))
    order = data(r)
    oid = order['id']
    car.order_ids.append(oid)
    item = order['items'][0]
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust)
    iid = data(r)['id']
    s, r, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('order paid', s == 200, (s, err(r)))
    time.sleep(4)
    check('no points for a paid but unredeemed order', crm(cust_id)[0] == 0, crm(cust_id))

    print('\n=== accrual on redeem ===')
    gross = psql('package_booking_db', f"SELECT coalesce(gross_amount, unit_amount*quantity) FROM order_items WHERE id='{item['id']}'")
    currency = psql('package_booking_db', f"SELECT currency FROM order_items WHERE id='{item['id']}'")
    v = (data(call('GET', f'/api/orders/{oid}/vouchers', token=cust)[1]) or [None])[0]
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{v['voucherId']}'")
    s, r, _ = call('POST', f'/api/partner/organizations/{org_id}/vouchers/redeem', {'payload': v['payload']}, partner)
    check('redeem 200', s == 200, (s, err(r)))
    expected = int(float(gross)) if currency.strip() == 'TRY' else 0
    check(f'points == floor(gross {gross} {currency}) = {expected}', wait_points(cust_id, expected), crm(cust_id))
    base_pts, base_tier = crm(cust_id)
    s, r, _ = call('GET', '/api/customers/me', token=cust)
    check('/me reflects points', data(r).get('loyaltyPoints') == base_pts, data(r))

    print('\n=== idempotency ===')
    publish(cust_id, item['id'], float(gross))
    publish(cust_id, item['id'], float(gross))
    time.sleep(6)
    check('duplicate events for the same item add nothing', crm(cust_id)[0] == base_pts, crm(cust_id))

    print('\n=== currency + tiers ===')
    publish(cust_id, str(uuid.uuid4()), 500.0, 'EUR')
    time.sleep(5)
    check('non-TRY event ignored', crm(cust_id)[0] == base_pts, crm(cust_id))
    publish(cust_id, str(uuid.uuid4()), float(max(3100 - base_pts, 1)))
    time.sleep(6)
    pts, tier = crm(cust_id)
    check('SILVER reached at >= 3100 points', pts >= 3100 and tier == 'SILVER', (pts, tier))
    publish(cust_id, str(uuid.uuid4()), float(max(3300 - pts, 1)))
    time.sleep(6)
    pts, tier = crm(cust_id)
    check('GOLD reached at >= 3300 points', pts >= 3300 and tier == 'GOLD', (pts, tier))
    s, r, _ = call('GET', '/api/customers/me', token=cust)
    check('/me shows GOLD', data(r).get('tier') == 'GOLD', data(r))

    print('\n=== refund does not revoke (no reversal implemented) ===')
    s, r, _ = call('POST', f'/api/orders/{oid}/cancel', None, cust)
    time.sleep(5)
    check('cancel after redeem rejected 422 ESCROW_ALREADY_RELEASED, points + tier unchanged', s == 422 and crm(cust_id) == (pts, 'GOLD'), (s, err(r), crm(cust_id)))

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
