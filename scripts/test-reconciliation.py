#!/usr/bin/env python3
"""Live test for RM-27 (timeout, restart, replay and late-payment reconciliation). Needs the stack running with
package-booking-service (PendingOrderExpiryJob) and payment-service (PaymentReconciliationJob). The jobs run on their own
schedule (default 60s), so each scenario waits up to WAIT seconds. Only this test's own rows are changed.

A: hotel order, hold_expires_at moved into the past (beyond the 2 minute grace) -> job sets EXPIRED, the hotel hold is
   released, inventory counters back to 0/0, a late payment does not confirm it.
B: hotel order paid normally, then the test deletes its own escrow_items row (ledger is append-only, it stays) and ages the
   intent -> the reconciliation job sees AUTHORIZED without escrow + order CONFIRMED and rebuilds the escrow, ledger stays
   balanced with exactly one capture, an ESCROW_REPAIRED row is in payment_reconciliations, and the admin report lists it.
Fixture data is removed at the end (same helpers as test-hold-expiry.py).
"""
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

HERE = pathlib.Path(__file__).resolve().parent
WAIT = 180


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


car = load('car_order', 'test-car-order.py')
pk = load('pkg_order', 'test-package-order.py')
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def counters(hotel, nights):
    n = ','.join(f"'{x}'" for x in nights)
    return psql('hotel_db', f"SELECT coalesce(sum(held),0)||'/'||coalesce(sum(confirmed),0) FROM daily_inventory "
                            f"WHERE hotel_id='{hotel}' AND stay_date IN ({n})")


def wait_for(fn, seconds=WAIT, step=5):
    end = time.time() + seconds
    while time.time() < end:
        v = fn()
        if v:
            return v
        time.sleep(step)
    return fn()


def main():
    admin_email, admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    sfx = uuid.uuid4().hex[:10]
    emails = {k: f'rc-{k}-{sfx}@example.test' for k in ('c1', 'p')}
    pws = {k: k.upper() + '1' + secrets.token_urlsafe(16) for k in emails}
    for k, e in emails.items():
        car.register(e, pws[k])
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        car.created_emails.append(e)
        time.sleep(13)
    c1, partner = (car.login(emails[k], pws[k]) for k in ('c1', 'p'))
    admin = car.login(admin_email, admin_pw)

    print('\n=== setup ===')
    hotel, room, plan, nights, check_out = pk.hotel_fixture(admin, partner)

    def quote():
        return data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                                  'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                                  'childrenAges': []}, c1))

    def order_status(oid):
        return psql('package_booking_db', f"SELECT status FROM orders WHERE id='{oid}'")

    check('baseline counters 0/0', counters(hotel, nights) == '0/0', counters(hotel, nights))

    print('\n=== A: unpaid order, holds expire, PendingOrderExpiryJob closes it ===')
    s, r, _ = call('POST', '/api/orders/hotel', {'quoteId': quote()['quoteId']}, c1)
    check('hotel order 201', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    check('counters held while PENDING', counters(hotel, nights) == '2/0', counters(hotel, nights))
    psql('package_booking_db', f"UPDATE orders SET hold_expires_at = now() - interval '10 minutes' WHERE id='{oid}'")
    status = wait_for(lambda: order_status(oid) == 'EXPIRED' and 'EXPIRED')
    check('order EXPIRED by the job', status == 'EXPIRED', order_status(oid))
    check('inventory released (0/0)', wait_for(lambda: counters(hotel, nights) == '0/0') is True, counters(hotel, nights))
    hs = psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{oid}'")
    check('hotel hold RELEASED or EXPIRED', hs in ('RELEASED', 'EXPIRED'), hs)
    ob = psql('package_booking_db', f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{oid}' AND payload LIKE '%ORDER_EXPIRED%'")
    check('ORDER_EXPIRED outbox event written once', ob == '1', ob)
    time.sleep(65)  # one more job cycle: idempotent
    ob2 = psql('package_booking_db', f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{oid}' AND payload LIKE '%ORDER_EXPIRED%'")
    check('still exactly one ORDER_EXPIRED event after another cycle', ob2 == '1', ob2)
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, c1)
    if s in (200, 201):
        s, r, _ = call('POST', f'/api/payments/intents/{data(r)["id"]}/confirm', {'paymentToken': 'TEST_OK'}, c1)
    check('late payment rejected', s >= 400, (s, err(r)))
    check('order stays EXPIRED', order_status(oid) == 'EXPIRED', order_status(oid))
    check('no escrow for expired order', psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id='{oid}'") == '0')

    print('\n=== B: ambiguous payment, escrow lost, PaymentReconciliationJob repairs it ===')
    s, r, _ = call('POST', '/api/orders/hotel', {'quoteId': quote()['quoteId']}, c1)
    check('second hotel order 201', s == 201, (s, err(r)))
    oid = data(r)['id']
    car.order_ids.append(oid)
    s, r, _ = call('POST', f'/api/payments/orders/{oid}/intent', None, c1)
    iid = data(r)['id']
    s, r, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, c1)
    check('payment 200', s == 200, (s, err(r)))
    check('order CONFIRMED', order_status(oid) == 'CONFIRMED', order_status(oid))
    before = psql('payment_db', f"SELECT count(*)||'/'||coalesce(sum(charged_amount),0)||'/'||coalesce(sum(supplier_amount),0) "
                                f"FROM escrow_items WHERE order_id='{oid}'")
    cap_before = psql('payment_db', f"SELECT count(*) FROM ledger_entries WHERE order_id='{oid}'")
    check('escrow exists after payment', before.startswith('1/'), before)
    psql('payment_db', f"DELETE FROM escrow_items WHERE order_id='{oid}'")  # simulate the lost escrow (ledger is append-only)
    psql('payment_db', f"UPDATE payment_intents SET updated_at = now() - interval '10 minutes' WHERE id='{iid}'")
    check('escrow gone', psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id='{oid}'") == '0')
    repaired = wait_for(lambda: psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id='{oid}'") == '1' and 'ok')
    check('escrow rebuilt by the job', repaired == 'ok')
    after = psql('payment_db', f"SELECT count(*)||'/'||coalesce(sum(charged_amount),0)||'/'||coalesce(sum(supplier_amount),0) "
                               f"FROM escrow_items WHERE order_id='{oid}'")
    check('repaired escrow equals the original (count/charged/supplier)', after == before, (before, after))
    check('escrow HELD', psql('payment_db', f"SELECT status FROM escrow_items WHERE order_id='{oid}'") == 'HELD')
    cap_after = psql('payment_db', f"SELECT count(*) FROM ledger_entries WHERE order_id='{oid}'")
    check('ledger not duplicated', cap_after == cap_before, (cap_before, cap_after))
    bal = psql('payment_db', f"SELECT coalesce(sum(debit_amount)-sum(credit_amount),0) FROM ledger_entries WHERE order_id='{oid}'")
    check('ledger balanced', float(bal or 0) == 0.0, bal)
    rec = psql('payment_db', f"SELECT string_agg(status, ',') FROM payment_reconciliations WHERE payment_intent_id='{iid}'")
    check('reconciliation row ESCROW_REPAIRED', rec == 'ESCROW_REPAIRED', rec)
    time.sleep(65)
    rec2 = psql('payment_db', f"SELECT count(*) FROM payment_reconciliations WHERE payment_intent_id='{iid}'")
    check('second cycle adds no row (idempotent)', rec2 == '1', rec2)
    s, r, _ = call('GET', '/api/admin/payment-reconciliation?status=ESCROW_REPAIRED&page=0&size=50', token=admin)
    items = (data(r) or {}).get('items', []) if s == 200 else []
    check('admin report lists the repair', any(str(i.get('orderId')) == oid for i in items), (s, err(r)))
    s, _, _ = call('GET', '/api/admin/payment-reconciliation', token=c1)
    check('customer cannot read the report (403)', s == 403, s)

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
            car.cleanup()
