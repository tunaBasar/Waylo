#!/usr/bin/env python3
"""Live observability check (OBSERVABILITY.md).

1. Gateway: X-Request-Id echoed for a safe client value, replaced for an unsafe/too long one, generated when absent.
2. /actuator/prometheus, /actuator/metrics, /actuator/env are not reachable through the gateway (:8080).
3. Create + pay a package order with one client id; the same id appears in api-gateway, package-booking-service and
   payment-service logs (HTTP, Feign hop payment -> booking, Kafka outbox events).
4. Business metrics on the services' own management ports (via docker exec): waylo_orders_total, waylo_payments_total,
   waylo_outbox_unpublished.

Reuses the fixture of test-package-order.py. Credentials never printed, fixture data removed at the end.
"""
import importlib.util
import pathlib
import secrets
import subprocess
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('pkg_order', HERE / 'test-package-order.py')
pkg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pkg)
car = pkg.car
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def logs(container, since):
    return subprocess.run(['docker', 'logs', '--since', since, container], capture_output=True, text=True).stdout + \
        subprocess.run(['docker', 'logs', '--since', since, container], capture_output=True, text=True).stderr


def scrape(container, port):
    return subprocess.run(['docker', 'exec', container, 'wget', '-qO-', f'http://localhost:{port}/actuator/prometheus'],
                          capture_output=True, text=True).stdout


def header(h, name='X-Request-Id'):
    return h.get(name) if h is not None else None


def main():
    since = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(time.time() - 5))

    print('\n=== gateway request id ===')
    _, _, h = call('GET', '/api/hotels', headers={'X-Request-Id': 'obs-client-1'})
    check('safe client id echoed', header(h) == 'obs-client-1', header(h))
    for bad in ['has space', 'x' * 65, 'semi;colon']:
        _, _, h = call('GET', '/api/hotels', headers={'X-Request-Id': bad})
        check(f'unsafe id replaced ({bad[:12]})', header(h) not in (None, bad) and len(header(h)) <= 64, header(h))
    _, _, h = call('GET', '/api/hotels')
    check('id generated when absent', bool(header(h)), header(h))
    s, _, h = call('GET', '/api/orders')
    check('401 responses carry an id too', s == 401 and bool(header(h)), (s, header(h)))

    print('\n=== actuator not public ===')
    for p in ['/actuator/prometheus', '/actuator/metrics', '/actuator/env']:
        s, _, _ = call('GET', p)
        check(f'{p} via :8080 -> {s}', s in (401, 403, 404), s)

    print('\n=== create + pay with one request id ===')
    before_pay = scrape('waylo-payment-service', 8087)
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    cust_email = f'obs-cust-{uuid.uuid4().hex[:10]}@example.test'
    part_email = f'obs-part-{uuid.uuid4().hex[:10]}@example.test'
    pw_c, pw_p = 'Cust1' + secrets.token_urlsafe(16), 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(cust_email, pw_c), (part_email, pw_p)]:
        car.register(e, pw)
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    cust, partner, admin = car.login(cust_email, pw_c), car.login(part_email, pw_p), car.login(admin_email, admin_pw)
    hotel, room, plan, nights, check_out = pkg.hotel_fixture(admin, partner)
    q = data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                          'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                          'childrenAges': []}, cust))
    rid = 'obs-' + uuid.uuid4().hex
    hdr = {'X-Request-Id': rid}
    s, r, h = call('POST', '/api/orders/package', {'items': [{'vertical': 'HOTEL', 'quoteId': q['quoteId']}]}, cust, headers=hdr)
    check('package create 201 with our id', s == 201 and header(h) == rid, (s, err(r), header(h)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, cust, headers=hdr)
    intent = data(r)
    s, r, h = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, cust, headers=hdr)
    check('confirm 200 with our id', s == 200 and header(h) == rid, (s, err(r), header(h)))
    check('outbox rows carry the id', psql('package_booking_db',
          f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{oid}' AND request_id='{rid}'") not in ('', '0'))
    time.sleep(5)  # outbox relay + consumers

    gw = [l for l in logs('waylo-api-gateway', since).splitlines() if rid in l]
    pb = [l for l in logs('waylo-package-booking-service', since).splitlines() if rid in l]
    pay = [l for l in logs('waylo-payment-service', since).splitlines() if rid in l]
    check(f'api-gateway logs contain id ({len(gw)} lines)', len(gw) >= 6, len(gw))
    check(f'package-booking-service logs contain id ({len(pb)} lines)', len(pb) >= 1, len(pb))
    check(f'payment-service logs contain id ({len(pay)} lines)', len(pay) >= 1, len(pay))
    for name, lines in [('gateway', gw[:1]), ('booking', pb[:2]), ('payment', pay[:2])]:
        for l in lines:
            print(f'  {name}: {l[:220]}')

    print('\n=== metrics ===')
    pb_metrics = scrape('waylo-package-booking-service', 8085)
    pay_metrics = scrape('waylo-payment-service', 8087)
    check('waylo_orders_total{event=created}', 'waylo_orders_total{event="created"}' in pb_metrics)
    check('waylo_orders_total{event=paid}', 'waylo_orders_total{event="paid"}' in pb_metrics)
    check('waylo_outbox_unpublished in booking', 'waylo_outbox_unpublished' in pb_metrics)
    check('waylo_payments_total{outcome=confirmed}', 'waylo_payments_total{outcome="confirmed"}' in pay_metrics,
          [l for l in before_pay.splitlines() if l.startswith('waylo_')])
    check('waylo_outbox_unpublished in payment', 'waylo_outbox_unpublished' in pay_metrics)
    for l in (pb_metrics + pay_metrics).splitlines():
        if l.startswith('waylo_'):
            print('  ' + l)

    failed = [n for n, ok in car.results if not ok]
    print(f'\n{len(car.results) - len(failed)} passed, {len(failed)} failed')
    return not failed


if __name__ == '__main__':
    ok = False
    try:
        ok = main()
    finally:
        car.cleanup()
        pkg.hotel_cleanup()
    sys.exit(0 if ok else 1)
