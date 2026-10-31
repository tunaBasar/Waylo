#!/usr/bin/env python3
"""Live acceptance for the payment core (escrow + ledger, TEST provider), through the gateway.

Builds its own fixture (approved org + hotel + inventory/rates, owner, two customers), uses the
bootstrap admin from .env (only WAYLO_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are read, never printed).
Commission rates it creates are scoped to the fixture organization (partner override), so platform
level rates are never touched. Cleans up every row it created.
"""
import json
import pathlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta
from decimal import Decimal

BASE = 'http://localhost:8080'
ROOT = pathlib.Path(__file__).resolve().parent.parent
P = F = 0
other_orgs = []


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print('PASS:', label)
    else:
        F += 1
        print('FAIL:', label)


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip()
    return None


def req(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                               method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=30) as x:
            raw = x.read()
            return x.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, None


def must(method, path, body=None, token=None):
    s, d = req(method, path, body, token)
    if s not in (200, 201, 204):
        raise AssertionError(f'{method} {path} -> {s} {d}')
    return (d or {}).get('data', d)


def psql(db, sql):
    out = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                         capture_output=True, text=True)
    return out.stdout.strip()


def internal(path):
    """Call a service-internal endpoint from inside the docker network with the internal token."""
    token = env_value('INTERNAL_API_TOKEN')
    cmd = ['docker', 'exec', 'waylo-payment-service', 'sh', '-c',
           f'wget -qO- --header="X-Internal-Token: $INTERNAL_API_TOKEN" --post-data="" '
           f'"http://localhost:8087{path}" 2>&1; echo " EXIT=$?"']
    del token  # the container already has it in its env; never pass secrets on argv
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def register(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:10]}@example.test'
    pw = 'Pay1' + secrets.token_urlsafe(16)
    must('POST', '/api/auth/register', {'firstName': 'Pay', 'lastName': 'Test', 'email': email, 'password': pw})
    time.sleep(13)  # gateway register limit 5/min
    tok = must('POST', '/api/auth/login', {'email': email, 'password': pw})['token']
    return email, tok


def main():
    emails, order_ids, hotel_id, org_id = [], [], None, None
    global other_orgs
    other_orgs = []
    try:
        admin = must('POST', '/api/auth/login', {'email': env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'),
                                                 'password': env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')})['token']
        e, owner = register('pay-owner'); emails.append(e)
        e, cust = register('pay-cust'); emails.append(e)
        e, other = register('pay-other'); emails.append(e)

        org_id = must('POST', '/api/organizations', {'name': f'pay-test-{uuid.uuid4().hex[:6]}'}, owner)['id']
        hotel_id = must('POST', '/api/hotels', {'name': 'Pay test hotel', 'city': f'PT{uuid.uuid4().hex[:6]}',
                                                'country': 'TR', 'address': 'Test only', 'starRating': 3,
                                                'organizationId': org_id}, owner)['id']
        must('POST', f'/api/admin/organizations/{org_id}/approve', token=admin)
        room = must('POST', f'/api/hotels/{hotel_id}/room-types', {'code': 'PT_DBL', 'name': 'PT double',
                    'maxAdults': 2, 'maxChildren': 0, 'maxOccupancy': 2}, owner)
        plan = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'PT_FLEX', 'name': 'PT flex',
                    'mealPlan': 'BREAKFAST', 'cancellationPolicy': 'FREE_CANCELLATION',
                    'freeCancellationDaysBefore': 2}, owner)
        start = date.today() + timedelta(days=40)
        nights = [(start + timedelta(days=i)).isoformat() for i in range(2)]
        must('PUT', f'/api/hotels/{hotel_id}/inventory', {'items': [
            {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 5, 'expectedVersion': None} for n in nights],
            'reason': 'payment acceptance fixture'}, owner)
        must('PUT', f'/api/hotels/{hotel_id}/rates', {'items': [
            {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': n, 'occupancy': 2,
             'currency': 'TRY', 'amount': a} for n, a in zip(nights, [1000.00, 1500.00])]}, owner)

        def new_order(tok):
            q = must('POST', '/api/quotes', {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                             'checkIn': nights[0], 'checkOut': (start + timedelta(days=2)).isoformat(),
                                             'rooms': 1, 'adults': 2, 'childrenAges': []}, tok)
            o = must('POST', '/api/orders/hotel', {'quoteId': q['quoteId']}, tok)
            order_ids.append(o['id'])
            return o

        # --- commission rate missing -> cannot pay ---
        order = new_order(cust)
        s, d = req('POST', f"/api/payments/orders/{order['id']}/intent", None, cust)
        missing = s == 422 and 'COMMISSION_RATE_MISSING' in json.dumps(d)
        has_platform_rate = psql('payment_db', "SELECT count(*) FROM commission_rates WHERE vertical='HOTEL' "
                                               "AND supplier_organization_id IS NULL") != '0'
        ok(missing or has_platform_rate, f'no rate -> 422 COMMISSION_RATE_MISSING (got {s})')

        # --- admin sets a partner-scoped rate (12.5%) ---
        ok(req('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org_id,
                                                       'rate': 0.1250}, cust)[0] == 403, 'customer cannot set rates 403')
        must('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org_id,
                                                     'rate': 0.1250}, admin)

        # --- intent: amount from the order, not the client; ADMIN blocked; stranger blocked ---
        ok(req('POST', f"/api/payments/orders/{order['id']}/intent", None, admin)[0] == 403, 'admin intent 403')
        ok(req('POST', f"/api/payments/orders/{order['id']}/intent", None, other)[0] in (403, 404),
           'other customer intent 403/404')
        intent = must('POST', f"/api/payments/orders/{order['id']}/intent", {'amount': 1}, cust)
        ok(Decimal(str(intent['amount'])) == Decimal('2500.00') and intent['currency'] == 'TRY',
           'intent amount = order total 2500.00 TRY')
        again = must('POST', f"/api/payments/orders/{order['id']}/intent", None, cust)
        ok(again['id'] == intent['id'], 'intent idempotent per order')
        ok(json.dumps(intent).find('TEST') >= 0, 'response labeled TEST provider')

        # --- decline then success ---
        s, dec = req('POST', f"/api/payments/intents/{intent['id']}/confirm", {'paymentToken': 'TEST_DECLINE'}, cust)
        dstat = ((dec or {}).get('data') or {}).get('status')
        ok(s == 200 and dstat == 'FAILED', f'TEST_DECLINE -> intent FAILED, no charge (got {s} {dstat})')
        ok(psql('package_booking_db', f"SELECT status FROM orders WHERE id='{order['id']}'") == 'PENDING',
           'order still PENDING after decline')
        order2 = new_order(cust)
        intent2 = must('POST', f"/api/payments/orders/{order2['id']}/intent", None, cust)
        paid = must('POST', f"/api/payments/intents/{intent2['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
        ok(paid.get('status') == 'AUTHORIZED', f"confirm -> AUTHORIZED (got {paid.get('status')})")
        ok(psql('package_booking_db', f"SELECT status FROM orders WHERE id='{order2['id']}'") == 'CONFIRMED',
           'order CONFIRMED after payment')
        hold = psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{order2['id']}'")
        ok(hold == 'CONFIRMED', f'hotel hold CONFIRMED (got {hold})')
        esc = psql('payment_db', f"SELECT gross_amount||'|'||commission_rate||'|'||commission_amount||'|'||"
                                 f"supplier_amount||'|'||status FROM escrow_items WHERE order_id='{order2['id']}'")
        ok(esc == '2500.00|0.1250|312.50|2187.50|HELD', f'escrow item 2500 / 12.5% / 312.50 / 2187.50 HELD ({esc})')
        bal = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries "
                                 f"WHERE order_id='{order2['id']}'")
        ok(bal in ('0', '0.00'), f'ledger balanced after capture ({bal})')
        view = must('GET', f"/api/payments/orders/{order2['id']}", None, cust)
        ok('ledger' not in json.dumps(view).lower(), 'customer view has no ledger')
        ok(req('GET', f"/api/payments/orders/{order2['id']}", None, other)[0] in (403, 404), 'stranger view 403/404')

        # --- ledger is append-only in the DB ---
        r = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', 'payment_db', '-tAc',
                            f"UPDATE ledger_entries SET debit_amount=0 WHERE order_id='{order2['id']}'"],
                           capture_output=True, text=True)
        ok(r.returncode != 0, 'UPDATE on ledger_entries rejected by trigger')

        # --- release one paid order (QR stand-in), refund via cancel on another ---
        item_id = psql('payment_db', f"SELECT order_item_id FROM escrow_items WHERE order_id='{order2['id']}'")
        out = internal(f'/internal/escrow-items/{item_id}/release')
        ok('EXIT=0' in out, 'internal release 200')
        ok(psql('payment_db', f"SELECT status FROM escrow_items WHERE order_item_id='{item_id}'") == 'RELEASED',
           'escrow RELEASED')
        acc = psql('payment_db', f"SELECT string_agg(account||':'||(credit_amount-debit_amount), ',' ORDER BY account) "
                                 f"FROM ledger_entries WHERE order_id='{order2['id']}' "
                                 f"AND account IN ('PLATFORM_COMMISSION','SUPPLIER_PAYABLE')")
        ok('PLATFORM_COMMISSION:312.50' in acc and 'SUPPLIER_PAYABLE:2187.50' in acc,
           f'ledger commission 312.50 + supplier 2187.50 ({acc})')
        ok(req('POST', f"/api/orders/{order2['id']}/cancel", None, cust)[0] == 422,
           'cancel after release rejected 422')
        ok(req('POST', f'/internal/escrow-items/{item_id}/release')[0] in (401, 403, 404),
           'internal endpoint not reachable via gateway')

        order3 = new_order(cust)
        i3 = must('POST', f"/api/payments/orders/{order3['id']}/intent", None, cust)
        must('POST', f"/api/payments/intents/{i3['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
        must('POST', f"/api/orders/{order3['id']}/cancel", None, cust)
        ok(psql('payment_db', f"SELECT status FROM escrow_items WHERE order_id='{order3['id']}'") == 'REFUNDED',
           'cancel of paid order refunds escrow')
        bal3 = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries "
                                  f"WHERE order_id='{order3['id']}'")
        ok(bal3 in ('0', '0.00'), f'ledger balanced after refund ({bal3})')
        ledger = must('GET', f"/api/admin/ledger?orderId={order3['id']}", None, admin)
        ok(len(ledger.get('entries', [])) >= 4 and Decimal(str(ledger.get('balance'))) == 0,
           'admin ledger lookup returns balanced entries')
        ok(psql('package_booking_db', f"SELECT string_agg(status, ',') FROM vouchers v JOIN order_items i "
                                      f"ON i.id = v.order_item_id WHERE i.order_id = '{order3['id']}'") == 'VOID',
           'cancelled order voucher is VOID')

        # --- QR voucher: issued on payment, owner-only, partner redemption releases escrow ---
        order4 = new_order(cust)
        i4 = must('POST', f"/api/payments/orders/{order4['id']}/intent", None, cust)
        must('POST', f"/api/payments/intents/{i4['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
        vouchers = must('GET', f"/api/orders/{order4['id']}/vouchers", None, cust)
        ok(len(vouchers) == 1 and vouchers[0]['status'] == 'ACTIVE' and vouchers[0]['payload'].startswith('WAYLO1:'),
           'one ACTIVE voucher with WAYLO1 payload after payment')
        payload = vouchers[0]['payload']
        vid = vouchers[0]['voucherId']
        ok(req('GET', f"/api/orders/{order4['id']}/vouchers", None, other)[0] in (403, 404), 'stranger cannot read vouchers')
        stored = psql('package_booking_db', f"SELECT code_hash FROM vouchers WHERE id='{vid}'")
        ok(payload.split(':')[2] not in stored and len(stored) == 64, 'only the SHA-256 of the code is stored')
        redeem = f'/api/partner/organizations/{org_id}/vouchers/redeem'
        s, d = req('POST', redeem, {'payload': payload}, owner)
        ok(s == 422 and 'NOT_YET_VALID' in json.dumps(d), f'redeem before check-in -> NOT_YET_VALID (got {s})')
        ok(req('POST', redeem, {'payload': payload}, cust)[0] == 403, 'non-member cannot redeem 403')
        ok(req('POST', redeem, {'payload': 'WAYLO1:' + vid + ':forged'}, owner)[0] == 422, 'forged code rejected 422')
        e, owner2 = register('pay-owner2'); emails.append(e)
        org2 = must('POST', '/api/organizations', {'name': f'pay-test2-{uuid.uuid4().hex[:6]}'}, owner2)['id']
        other_orgs.append(org2)
        s, d = req('POST', f'/api/partner/organizations/{org2}/vouchers/redeem', {'payload': payload}, owner2)
        ok(s in (403, 422) and ('WRONG_SUPPLIER' in json.dumps(d) or s == 403), f'other org cannot redeem ({s})')
        # move only this test voucher's window to "now" (check-in is 40 days ahead in the fixture)
        psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{vid}'")
        import concurrent.futures
        with concurrent.futures.ThreadPoolExecutor(6) as pool:
            results = list(pool.map(lambda _: req('POST', redeem, {'payload': payload}, owner), range(6)))
        codes = sorted(r[0] for r in results)
        ok(codes.count(200) == 1 and all(c == 409 for c in codes if c != 200),
           f'6 concurrent scans -> exactly one 200, rest 409 ({codes})')
        win = next(r[1]['data'] for r in results if r[0] == 200)
        ok(Decimal(str(win['amountReleased'])) == Decimal('2187.50'), f"redeem releases supplier share 2187.50 ({win['amountReleased']})")
        ok(psql('payment_db', f"SELECT status FROM escrow_items WHERE order_id='{order4['id']}'") == 'RELEASED',
           'escrow RELEASED by QR redemption')
        ok(psql('package_booking_db', f"SELECT status FROM vouchers WHERE id='{vid}'") == 'REDEEMED', 'voucher REDEEMED')
    except AssertionError as exc:
        ok(False, str(exc))
    finally:
        cleanup(hotel_id, org_id, order_ids, emails)
    print(f'{P}/{P + F} PASS')
    return 1 if F else 0


def cleanup(hotel_id, org_id, order_ids, emails):
    ids = ','.join(f"'{i}'" for i in order_ids)
    if ids:
        # ledger rows are append-only by design: the trigger is disabled only for this test cleanup
        psql('payment_db', 'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('payment_db', f'DELETE FROM ledger_entries WHERE order_id IN ({ids})')
        psql('payment_db', 'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('payment_db', f'DELETE FROM escrow_items WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM payment_intents WHERE order_id IN ({ids})')
        psql('package_booking_db', f'DELETE FROM vouchers WHERE order_item_id IN '
                                   f'(SELECT id FROM order_items WHERE order_id IN ({ids}))')
        psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({ids})')
    if org_id:
        psql('payment_db', f"DELETE FROM commission_rates WHERE supplier_organization_id='{org_id}'")
    if hotel_id:
        h = f"'{hotel_id}'"
        for t in ('hotel_holds', 'hotel_quotes', 'inventory_adjustments', 'daily_rates', 'daily_inventory',
                  'rate_plans', 'room_types'):
            psql('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM hotels WHERE id IN ({h})')
    if org_id:
        o = f"'{org_id}'"
        psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({o})')
    for o2 in other_orgs:
        psql('auth_db', f"DELETE FROM role_assignments WHERE organization_id = '{o2}'")
        psql('auth_db', f"DELETE FROM organization_memberships WHERE organization_id = '{o2}'")
        psql('auth_db', f"DELETE FROM organizations WHERE id = '{o2}'")
    if emails:
        q = ','.join(f"'{e}'" for e in emails)
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


if __name__ == '__main__':
    sys.exit(main())
