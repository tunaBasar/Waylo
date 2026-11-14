#!/usr/bin/env python3
"""Live acceptance test for the transactional outbox (RM-15) in package-booking-service.

Kafka is stopped, a car order is created and paid through the gateway (must succeed), the outbox must hold unpublished
ORDER_CREATED + ORDER_PAID rows. Kafka is started again, the relay must publish them and the notification effect
(order confirmation row) must appear. Kafka is always restarted in a finally block.
Reuses helpers of test-car-order.py. Test users *@example.test, fixture data removed at the end.
"""
import importlib.util
import pathlib
import secrets
import subprocess
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
spec = importlib.util.spec_from_file_location('car_order', HERE / 'test-car-order.py')
car = importlib.util.module_from_spec(spec)
spec.loader.exec_module(car)
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def compose(*args):
    return subprocess.run(['docker', 'compose', *args], cwd=ROOT, capture_output=True, text=True)


def wait_for(fn, seconds, step=2):
    end = time.time() + seconds
    while time.time() < end:
        v = fn()
        if v:
            return v
        time.sleep(step)
    return fn()


def main():
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    cust_email = f'outbox-cust-{uuid.uuid4().hex[:10]}@example.test'
    part_email = f'outbox-part-{uuid.uuid4().hex[:10]}@example.test'
    pw_c, pw_p = 'Cust1' + secrets.token_urlsafe(16), 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(cust_email, pw_c), (part_email, pw_p)]:
        car.register(e, pw)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    cust, partner, admin = car.login(cust_email, pw_c), car.login(part_email, pw_p), car.login(admin_email, admin_pw)
    cust_id = psql('auth_db', f"SELECT id FROM users WHERE email='{cust_email}'")
    org_id, branch, _veh, _pd, _rd, pickup_at, return_at = car._setup_org(admin, partner, part_email)
    for _ in range(20):  # crm customer (auth-events) must exist before the confirmation e-mail lookup
        if psql('crm_db', f"SELECT count(*) FROM customers WHERE id = '{cust_id}'") not in ('', '0'):
            break
        time.sleep(1)

    print('\n=== stop kafka ===')
    r = compose('stop', 'kafka')
    check('kafka stopped', r.returncode == 0, r.stderr[-200:])
    time.sleep(3)

    print('\n=== create + pay while kafka is down ===')
    s, r, _ = call('POST', '/api/orders/car', {'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY',
                                               'transmission': 'MANUAL', 'pickupAt': pickup_at, 'returnAt': return_at,
                                               'driverAge': 30}, cust)
    check('order create 201 with kafka down', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust)
    iid = data(r)['id']
    s, r, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, cust)
    check('payment AUTHORIZED with kafka down', s == 200 and data(r)['status'] == 'AUTHORIZED', (s, err(r)))
    check('order CONFIRMED', data(call('GET', f'/api/orders/{oid}', token=cust)[1])['status'] == 'CONFIRMED')
    time.sleep(15)  # first failed send needs max.block/delivery timeout before attempts is recorded
    rows = psql('package_booking_db', f"SELECT string_agg(payload::json->>'eventType' || ':' || (published_at IS NULL)::text, ',' "
                                      f"ORDER BY created_at) FROM outbox_event WHERE aggregate_id='{oid}'")
    check('outbox holds unpublished ORDER_CREATED + ORDER_PAID', rows == 'ORDER_CREATED:true,ORDER_PAID:true', rows)
    attempts = psql('package_booking_db', f"SELECT max(attempts) FROM outbox_event WHERE aggregate_id='{oid}'")
    check('relay attempted and backed off', attempts not in ('', '0'), attempts)
    check('no confirmation notification yet', psql('notification_db', f"SELECT count(*) FROM notifications WHERE booking_id='{oid}'") == '0')

    print('\n=== start kafka ===')
    r = compose('start', 'kafka')
    check('kafka started', r.returncode == 0, r.stderr[-200:])
    pending = wait_for(lambda: psql('package_booking_db', f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{oid}' "
                                                          f"AND published_at IS NULL") == '0', 240, 3)
    check('all outbox rows published after kafka is back', pending, psql('package_booking_db',
          f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{oid}' AND published_at IS NULL"))
    notif = wait_for(lambda: psql('notification_db', f"SELECT count(*) FROM notifications WHERE booking_id='{oid}'") not in ('', '0'), 120, 3)
    check('notification effect appears (order confirmation)', notif)
    time.sleep(5)
    per = psql('notification_db', f"SELECT string_agg(type || ':' || n, ',' ORDER BY type) FROM (SELECT type, count(*) n "
                                  f"FROM notifications WHERE booking_id='{oid}' AND body LIKE 'Sipari%onayland%' GROUP BY type) x")
    check('one confirmation per channel (EMAIL, PUSH), no duplicates', per == 'EMAIL:1,PUSH:1', per)

    failed = [n for n, ok in car.results if not ok]
    print(f'\n{len(car.results) - len(failed)}/{len(car.results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        r = compose('start', 'kafka')
        print('kafka restart in finally:', 'ok' if r.returncode == 0 else r.stderr[-200:])
        car.cleanup()
