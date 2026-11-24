#!/usr/bin/env python3
"""Live acceptance for partner payouts (PAYOUT-CONTRACT.md sec. 9.2), through the gateway.

Own fixture: partner org A (hotel, approved ACTIVE, owner + a HOTEL_STAFF member, 10% partner commission), a second org B with its own
owner (cross-org), TEST provider. The first escrow rows come through the real flow (order, pay, redeem) so released_at is real.
Further earnings are inserted into payment_db with psql (fixture rows only, same style as test-bus.py). Append-only triggers of the
payout tables are disabled only for fixture cleanup. No real money moves: the admin "transfer" is just the status flow.
Exit code 1 on any FAIL.
"""
import importlib.util
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from decimal import Decimal

ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('tp', ROOT / 'scripts' / 'test-payments.py')
tp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tp)
must, psql, register, env_value = tp.must, tp.psql, tp.register, tp.env_value
BASE = tp.BASE
P = F = 0
RAW = []          # (tag, raw body text) of every response, to prove the plain IBAN never leaks
IBAN_PLAIN = 'TR330006100519786457841326'
IBAN_GROUPED = 'TR33 0006 1005 1978 6457 8413 26'
STATE = {'orders': [], 'intents': [], 'emails': []}


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print('PASS:', label)
    else:
        F += 1
        print('FAIL:', label)


def call(method, path, body=None, token=None, headers=None, tag='api'):
    """Returns (status, parsed json or None, raw text). Records the raw body for the masking sweep."""
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    h.update(headers or {})
    r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=30) as x:
            raw = x.read().decode()
            status = x.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        status = e.code
    RAW.append((tag + ' ' + method + ' ' + path, raw))
    try:
        return status, (json.loads(raw) if raw else None), raw
    except ValueError:
        return status, None, raw


def data(d):
    return (d or {}).get('data', d) if isinstance(d, dict) else d


def code(d):
    return (d or {}).get('error') if isinstance(d, dict) else None


def make_iban(tail21):
    """Valid TR IBAN from 21 digits (mod-97 check digits computed)."""
    bban = tail21 + '0'
    chk = 98 - int(bban + '292700') % 97
    return f'TR{chk:02d}{bban}'


def fresh_iban():
    return make_iban('00061' + ''.join(str(int(c, 16) % 10) for c in uuid.uuid4().hex[:16]))


def idem():
    return {'Idempotency-Key': str(uuid.uuid4())}


def d2(v):
    return Decimal(str(v)).quantize(Decimal('0.01'))


def balance(org, token, currency='TRY'):
    s, d, _ = call('GET', f'/api/partner/organizations/{org}/payouts/balance', token=token)
    assert s == 200, (s, d)
    for b in data(d)['balances']:
        if b['currency'] == currency:
            return b
    return {'currency': currency, 'payable': '0.00', 'reserved': '0.00', 'paidTotal': '0.00', 'owed': '0.00', 'held': '0.00',
            'canRequest': False, 'blockedReason': None}


def add_released(org_id, supplier, currency='TRY'):
    """Fixture earnings: one RELEASED escrow item (commission 0) plus its SUPPLIER_PAYABLE ledger credit, real FK rows."""
    intent, order, item = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    psql('payment_db', "INSERT INTO payment_intents (id, order_id, customer_id, amount, currency, status, provider, idempotency_key) "
                       f"VALUES ('{intent}','{order}','{uuid.uuid4()}',{supplier},'{currency}','RELEASED','TEST','payout-live-{intent}')")
    psql('payment_db', "INSERT INTO escrow_items (id, payment_intent_id, order_id, order_item_id, vertical, supplier_organization_id, gross_amount, charged_amount, "
                       "discount_amount, commission_rate, commission_amount, supplier_amount, currency, status, release_trigger, settlement_reason, held_at, released_at) "
                       f"VALUES ('{uuid.uuid4()}','{intent}','{order}','{item}','HOTEL','{org_id}',{supplier},{supplier},0,0,0,{supplier},'{currency}','RELEASED',"
                       "'REDEMPTION','REDEMPTION', now() - interval '1 hour', now() - interval '1 hour')")
    psql('payment_db', "INSERT INTO ledger_entries (entry_id, group_id, occurred_at, order_id, order_item_id, account, credit_amount, currency, correlation_id) "
                       f"VALUES ('{uuid.uuid4()}','{uuid.uuid4()}', now(), '{order}','{item}','SUPPLIER_PAYABLE',{supplier},'{currency}','{uuid.uuid4()}')")
    STATE['orders'].append(str(order))
    STATE['intents'].append(str(intent))


def main():
    emails, order_ids, hotel_id, org_a, org_b = STATE['emails'], STATE['orders'], None, None, None
    tp.other_orgs = []
    payout_ids = []
    try:
        admin = must('POST', '/api/auth/login', {'email': env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'),
                                                 'password': env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')})['token']
        e, owner = register('po-owner'); emails.append(e)
        e, cust = register('po-cust'); emails.append(e)
        e, owner_b = register('po-ownerb'); emails.append(e)
        staff_email, staff = register('po-staff'); emails.append(staff_email)

        org_a = must('POST', '/api/organizations', {'name': f'po-test-{uuid.uuid4().hex[:6]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, owner)['id']
        org_b = must('POST', '/api/organizations', {'name': f'po-other-{uuid.uuid4().hex[:6]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, owner_b)['id']
        tp.other_orgs.append(org_b)
        hotel_id = must('POST', '/api/hotels', {'name': 'Payout test hotel', 'city': f'PO{uuid.uuid4().hex[:6]}', 'country': 'TR',
                                                'address': 'Test only', 'starRating': 3, 'organizationId': org_a}, owner)['id']
        must('POST', f'/api/admin/organizations/{org_a}/approve', token=admin)
        must('POST', f'/api/admin/organizations/{org_b}/approve', token=admin)
        room = must('POST', f'/api/hotels/{hotel_id}/room-types', {'code': 'PO_DBL', 'name': 'PO double', 'maxAdults': 2, 'maxChildren': 0, 'maxOccupancy': 2}, owner)
        plan = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'PO_FLEX', 'name': 'PO flex', 'mealPlan': 'BREAKFAST',
                                                                  'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, owner)
        start = date.today() + timedelta(days=40)
        nights = [(start + timedelta(days=i)).isoformat() for i in range(3)]
        must('PUT', f'/api/hotels/{hotel_id}/inventory', {'items': [
            {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 5, 'expectedVersion': None} for n in nights],
            'reason': 'payout acceptance fixture'}, owner)
        must('PUT', f'/api/hotels/{hotel_id}/rates', {'items': [
            {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': n, 'occupancy': 2, 'currency': 'TRY', 'amount': a}
            for n, a in zip(nights, [1000.00, 1500.00, 1500.00])]}, owner)
        tp.publish_hotel(hotel_id, owner, admin)
        must('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org_a, 'rate': 0.1000}, admin)
        must('POST', f'/api/organizations/{org_a}/role-grants', {'email': staff_email, 'role': 'HOTEL_STAFF', 'hotelId': hotel_id}, owner)

        def paid_order(night_index):
            q = must('POST', '/api/quotes', {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                             'checkIn': nights[night_index], 'checkOut': (date.fromisoformat(nights[night_index]) + timedelta(days=1)).isoformat(),
                                             'rooms': 1, 'adults': 2, 'childrenAges': []}, cust)
            o = must('POST', '/api/orders/hotel', {'quoteId': q['quoteId']}, cust)
            order_ids.append(o['id'])
            i = must('POST', f"/api/payments/orders/{o['id']}/intent", None, cust)
            must('POST', f"/api/payments/intents/{i['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
            return o['id']

        base = f'/api/partner/organizations/{org_a}'
        adm = '/api/admin'

        # ---- 1. balance: gross 1000 at 10% -> payable 900.00, a second unredeemed item is held (estimate), earnings agree
        redeemed = paid_order(0)
        held_order = paid_order(1)
        v = must('GET', f'/api/orders/{redeemed}/vouchers', None, cust)[0]
        psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{v['voucherId']}'")
        s, _, _ = call('POST', f'{base}/vouchers/redeem', {'payload': v['payload']}, owner)
        ok(s == 200, f'1 redeem ok ({s})')
        b = balance(org_a, owner)
        ok(d2(b['payable']) == Decimal('900.00'), f"1 payable == 900.00 ({b['payable']})")
        ok(d2(b['held']) == Decimal('1350.00'), f"1 held (estimated) shown 1350.00 and not in payable ({b['held']})")
        s, d, _ = call('GET', f'{base}/earnings', token=owner)
        totals = {t['currency']: t for t in data(d).get('totalsByCurrency', [])}
        ok(s == 200 and d2(totals['TRY']['released']) == Decimal('900.00'), f"1 earnings released agrees (900.00)")
        ok(psql('payment_db', f"SELECT coalesce(sum(credit_amount),0) FROM ledger_entries l JOIN escrow_items e ON e.order_item_id=l.order_item_id "
                              f"WHERE e.supplier_organization_id='{org_a}' AND l.account='SUPPLIER_PAYABLE'") in ('900.00', '900'), '1 ledger SUPPLIER_PAYABLE credits equal released')

        # ---- 3. bank account
        s, d, _ = call('PUT', f'{base}/bank-account', {'accountHolderName': 'Acme Otel AS', 'iban': 'TR33 0006 1005 1978 6457 8413 27'}, owner)
        ok(s == 400 and code(d) == 'INVALID_IBAN', f'3 invalid IBAN checksum -> 400 INVALID_IBAN ({s})')
        s, d, _ = call('PUT', f'{base}/bank-account', {'accountHolderName': 'Acme Otel AS', 'iban': 'DE89 3704 0044 0532 0130 00'}, owner)
        ok(s == 400 and code(d) == 'INVALID_IBAN', f'3 non TR IBAN -> 400 ({s})')
        s, d, raw = call('PUT', f'{base}/bank-account', {'accountHolderName': 'Acme Otel AS', 'iban': IBAN_GROUPED}, owner)
        acct = data(d) or {}
        ok(s == 200 and acct.get('status') == 'PENDING_VERIFICATION' and acct.get('maskedIban', '').endswith('26'), f'3 PUT bank account 200 pending, masked ({s})')
        ok(IBAN_PLAIN not in raw and '0006 1005' not in raw and '00061005' not in raw, '3 PUT response never contains the IBAN')
        s, d, raw = call('GET', f'{base}/bank-account', token=owner)
        ok(s == 200 and '00061005' not in raw and data(d)['maskedIban'] == 'TR** **** **** **** **** **26', '3 GET bank account masked')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 500.00}, owner, idem())
        ok(s == 422 and code(d) == 'BANK_ACCOUNT_NOT_VERIFIED', f'3 request before verification -> 422 BANK_ACCOUNT_NOT_VERIFIED ({s})')
        s, _, _ = call('POST', f"{adm}/bank-accounts/{acct['id']}/verify", {}, owner)
        ok(s == 403, f'3 non admin verify -> 403 ({s})')
        s, d, _ = call('GET', f'{adm}/bank-accounts?status=PENDING_VERIFICATION', token=admin)
        ok(s == 200 and any(x['id'] == acct['id'] for x in data(d)), '3 admin verification queue lists the account (masked)')
        s, d, _ = call('POST', f"{adm}/bank-accounts/{acct['id']}/verify", {}, admin)
        ok(s == 200 and data(d)['status'] == 'VERIFIED', f'3 admin verify -> VERIFIED ({s})')
        ok(balance(org_a, owner)['canRequest'] is True, '3 canRequest true after verification')

        # ---- 2. multi-currency
        add_released(org_a, '50.00', 'EUR')
        s, d, _ = call('GET', f'{base}/payouts/balance', token=owner)
        currencies = sorted(x['currency'] for x in data(d)['balances'])
        ok(currencies == ['EUR', 'TRY'], f'2 balance lists TRY and EUR separately ({currencies})')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'EUR', 'amount': 50.00}, owner, idem())
        ok(s == 422 and code(d) == 'PAYOUT_CURRENCY_NOT_SUPPORTED', f'2 EUR request -> 422 PAYOUT_CURRENCY_NOT_SUPPORTED ({s})')

        # ---- 5. lifecycle
        key = str(uuid.uuid4())
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 900.00}, owner, {'Idempotency-Key': key})
        p1 = data(d) or {}
        ok(s == 201 and p1.get('status') == 'REQUESTED' and d2(p1.get('amount')) == Decimal('900.00'), f'5 request 900.00 -> 201 REQUESTED ({s})')
        payout_ids.append(p1['id'])
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 900.00}, owner, {'Idempotency-Key': key})
        ok(s == 200 and data(d)['id'] == p1['id'], f'5 same key + body replay -> 200 same id ({s})')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 800.00}, owner, {'Idempotency-Key': key})
        ok(s == 409 and code(d) == 'IDEMPOTENCY_KEY_REUSED', f'5 same key different amount -> 409 IDEMPOTENCY_KEY_REUSED ({s})')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 100.00}, owner, idem())
        ok(s == 409 and code(d) == 'OPEN_PAYOUT_EXISTS', f'5 second open request -> 409 OPEN_PAYOUT_EXISTS ({s})')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 100.00}, owner)
        ok(s == 400 and code(d) == 'VALIDATION_ERROR', f'5 missing Idempotency-Key -> 400 VALIDATION_ERROR ({s})')
        b = balance(org_a, owner)
        ok(d2(b['payable']) == Decimal('0.00') and d2(b['reserved']) == Decimal('900.00'), '5 payable 0.00 and reserved 900.00')
        s, _, _ = call('GET', f"{adm}/payouts/{p1['id']}/bank-details", token=admin)
        ok(s == 409, f'5 bank-details of a REQUESTED payout -> 409 ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/processing", {}, admin)
        ok(s == 409 and code(d) == 'INVALID_STATE_TRANSITION', f'5 REQUESTED -> PROCESSING illegal -> 409 ({s})')
        s, _, _ = call('POST', f"{adm}/payouts/{p1['id']}/approve", {}, owner)
        ok(s == 403, f'5 partner cannot approve -> 403 ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/approve", {}, admin)
        ok(s == 200 and data(d)['status'] == 'APPROVED', f'5 approve ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/approve", {}, admin)
        ok(s == 200 and data(d)['status'] == 'APPROVED', f'5 approve twice is idempotent ({s})')
        s, d, raw = call('GET', f"{adm}/payouts/{p1['id']}/bank-details", token=admin, tag='REVEAL')
        ok(s == 200 and data(d)['iban'] == IBAN_PLAIN, f'5 bank-details of an APPROVED payout returns the IBAN ({s})')
        s, _, _ = call('GET', f"{adm}/payouts/{p1['id']}/bank-details", token=owner)
        ok(s == 403, f'5 partner cannot read bank-details -> 403 ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/processing", {}, admin)
        ok(s == 200 and data(d)['status'] == 'PROCESSING', f'5 processing ({s})')
        ref = 'TRF-' + uuid.uuid4().hex[:12].upper()
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/paid", {'reference': 'ab'}, admin)
        ok(s == 400, f'5 paid with a too short reference -> 400 ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/paid", {'reference': ref}, admin)
        ok(s == 200 and data(d)['status'] == 'PAID' and data(d)['paymentReference'] == ref, f'5 paid with reference -> PAID ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/paid", {'reference': ref}, admin)
        ok(s == 200 and data(d)['status'] == 'PAID', f'5 paid twice is idempotent ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p1['id']}/failed", {'reason': 'too late to fail'}, admin)
        ok(s == 409 and code(d) == 'INVALID_STATE_TRANSITION', f'5 PAID -> FAILED illegal -> 409 ({s})')
        s, _, _ = call('GET', f"{adm}/payouts/{p1['id']}/bank-details", token=admin)
        ok(s == 409, f'5 bank-details of a PAID payout -> 409 ({s})')
        rows = psql('payment_db', f"SELECT entry_type||':'||payable_delta||':'||coalesce(account,'-')||':'||debit_amount||':'||credit_amount FROM payout_ledger WHERE payout_id='{p1['id']}' ORDER BY entry_type, account")
        ok('RESERVE:-900.00' in rows, '5 ledger RESERVE -900')
        ok('PAID:0.00:SUPPLIER_PAYABLE:900.00:0.00' in rows and 'PAID:0.00:SUPPLIER_PAID:0.00:900.00' in rows, '5 ledger PAID legs DR SUPPLIER_PAYABLE 900 / CR SUPPLIER_PAID 900')
        ok(psql('payment_db', f"SELECT sum(debit_amount)=sum(credit_amount) FROM payout_ledger WHERE payout_id='{p1['id']}' AND entry_type='PAID'") == 't', '5 PAID group balanced')
        ok(psql('payment_db', f"SELECT count(*) FROM payout_events WHERE payout_id='{p1['id']}'") == '4', '5 four payout_events rows')
        upd = psql('payment_db', f"UPDATE payout_ledger SET payable_delta = 0 WHERE payout_id='{p1['id']}'")
        ok(psql('payment_db', f"SELECT sum(payable_delta) FROM payout_ledger WHERE payout_id='{p1['id']}'") in ('-900.00', '-900') and not upd.startswith('UPDATE'),
           '5 UPDATE on payout_ledger is rejected by the trigger')
        b = balance(org_a, owner)
        ok(d2(b['paidTotal']) == Decimal('900.00') and d2(b['payable']) == Decimal('0.00'), '5 paidTotal 900.00, payable 0.00')

        # ---- 5b/6. same reference on another payout, then the failure path
        add_released(org_a, '500.00')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 500.00}, owner, idem())
        p2 = data(d)
        payout_ids.append(p2['id'])
        call('POST', f"{adm}/payouts/{p2['id']}/approve", {}, admin)
        call('POST', f"{adm}/payouts/{p2['id']}/processing", {}, admin)
        s, d, _ = call('POST', f"{adm}/payouts/{p2['id']}/paid", {'reference': ref}, admin)
        ok(s == 409 and code(d) == 'PAYMENT_REFERENCE_IN_USE', f'5 same reference on another payout -> 409 PAYMENT_REFERENCE_IN_USE ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{p2['id']}/failed", {'reason': 'bank rejected the transfer'}, admin)
        ok(s == 200 and data(d)['status'] == 'FAILED', f'6 failed with reason ({s})')
        b = balance(org_a, owner)
        ok(d2(b['payable']) == Decimal('500.00') and d2(b['reserved']) == Decimal('0.00'), '6 payable restored to 500.00 after failure')
        ok(psql('payment_db', f"SELECT string_agg(entry_type, ',' ORDER BY entry_type) FROM payout_ledger WHERE payout_id='{p2['id']}'") == 'RESERVE,RESERVE_RELEASE',
           '6 ledger RESERVE + RESERVE_RELEASE, never PAID')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 500.00}, owner, idem())
        ok(s == 201, f'6 a new request is allowed after FAILED ({s})')
        p3 = data(d)
        payout_ids.append(p3['id'])
        s, d, _ = call('POST', f"{base}/payouts/{p3['id']}/cancel", None, owner)
        ok(s == 200 and data(d)['status'] == 'CANCELLED', f'5 partner cancels own REQUESTED payout ({s})')
        ok(d2(balance(org_a, owner)['payable']) == Decimal('500.00'), '5 cancel returns the funds')

        # ---- 7. double payout race
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 100.00}, owner, idem())
        pa = data(d)
        payout_ids.append(pa['id'])
        call('POST', f"{base}/payouts/{pa['id']}/cancel", None, owner)
        add_released(org_a, '400.00')       # payable now exactly 900.00
        ok(d2(balance(org_a, owner)['payable']) == Decimal('900.00'), '7 payable is 900.00 before the race')

        def fire(_):
            return call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 900.00}, owner, idem(), tag='race')[0:2]

        with ThreadPoolExecutor(max_workers=10) as pool:
            results = list(pool.map(fire, range(10)))
        created = [r for r in results if r[0] == 201]
        others = [r for r in results if r[0] != 201]
        ok(len(created) == 1 and len(others) == 9 and all(r[0] in (409, 422) for r in others), f'7 10 parallel requests: one 201, nine 409/422 ({[r[0] for r in results]})')
        ok(psql('payment_db', f"SELECT count(*) FROM partner_payouts WHERE organization_id='{org_a}' AND status IN ('REQUESTED','APPROVED','PROCESSING')") == '1',
           '7 exactly one open payout in the database')
        ok(psql('payment_db', f"SELECT count(*) FROM payout_ledger l JOIN partner_payouts p ON p.id=l.payout_id WHERE l.organization_id='{org_a}' AND l.entry_type='RESERVE' AND p.status='REQUESTED'") == '1',
           '7 exactly one RESERVE for the open payout')
        win = data(created[0][1])
        payout_ids.append(win['id'])
        call('POST', f"{adm}/payouts/{win['id']}/approve", {}, admin)
        call('POST', f"{adm}/payouts/{win['id']}/processing", {}, admin)
        ref2 = 'TRF-' + uuid.uuid4().hex[:12].upper()

        def pay(_):
            return call('POST', f"{adm}/payouts/{win['id']}/paid", {'reference': ref2}, admin, tag='race')[0]

        with ThreadPoolExecutor(max_workers=6) as pool:
            pay_results = list(pool.map(pay, range(6)))
        ok(all(r in (200, 409) for r in pay_results) and 200 in pay_results, f'7 6 parallel paid calls: 200 or 409 only ({pay_results})')
        ok(psql('payment_db', f"SELECT count(*) FROM payout_ledger WHERE payout_id='{win['id']}' AND entry_type='PAID'") == '2', '7 exactly one PAID leg pair')
        ok(psql('payment_db', f"SELECT count(*) FROM outbox_event WHERE aggregate_id='{win['id']}'") == '1', '7 exactly one PAYOUT_PAID outbox row')

        # ---- 8. refund after release (admin adjustments)
        add_released(org_a, '900.00')
        ok(d2(balance(org_a, owner)['payable']) == Decimal('900.00'), '8 payable 900.00')
        k1 = 'adj-' + uuid.uuid4().hex
        adj = {'currency': 'TRY', 'amount': 200.00, 'direction': 'DEBIT', 'reason': 'chargeback case 1', 'idempotencyKey': k1}
        s, d, _ = call('POST', f'{adm}/organizations/{org_a}/payout-adjustments', adj, admin)
        ok(s == 201, f'8 admin DEBIT 200 -> 201 ({s})')
        ok(d2(balance(org_a, owner)['payable']) == Decimal('700.00'), '8 payable 700.00')
        s, _, _ = call('POST', f'{adm}/organizations/{org_a}/payout-adjustments', adj, admin)
        ok(psql('payment_db', f"SELECT count(*) FROM payout_ledger WHERE organization_id='{org_a}' AND idempotency_key='{k1}'") == '1', f'8 adjustment replay writes one row ({s})')
        s, _, _ = call('POST', f'{adm}/organizations/{org_a}/payout-adjustments', adj, owner)
        ok(s == 403, f'8 non admin adjustment -> 403 ({s})')
        s, _, _ = call('POST', f'{adm}/organizations/{org_a}/payout-adjustments', dict(adj, amount=5000.00, idempotencyKey='adj-' + uuid.uuid4().hex), admin)
        b = balance(org_a, owner)
        ok(d2(b['payable']) == Decimal('0.00') and d2(b['owed']) == Decimal('4300.00') and b['canRequest'] is False,
           f"8 payable 0.00, owed 4300.00, canRequest false ({b['payable']}, {b['owed']})")
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 100.00}, owner, idem())
        ok(s == 422, f'8 request while owing -> 422 ({s})')
        s, _, _ = call('POST', f'{adm}/organizations/{org_a}/payout-adjustments', dict(adj, amount=5200.00, direction='CREDIT', idempotencyKey='adj-' + uuid.uuid4().hex), admin)
        ok(d2(balance(org_a, owner)['payable']) == Decimal('900.00'), '8 CREDIT restores payable 900.00')
        s, d, _ = call('GET', f'{adm}/organizations/{org_a}/payout-balance', token=admin)
        try_row = [x for x in data(d)['balances'] if x['currency'] == 'TRY'][0]
        ok(s == 200 and try_row['reconciles'] is True, f"8 admin payout-balance reconciles ({try_row.get('reconciles')})")

        # ---- 10. account change after request
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 900.00}, owner, idem())
        pc = data(d)
        payout_ids.append(pc['id'])
        s, d, _ = call('PUT', f'{base}/bank-account', {'accountHolderName': 'Acme Otel AS', 'iban': fresh_iban()}, owner)
        ok(s == 200 and data(d)['status'] == 'PENDING_VERIFICATION', f'10 replacing the IBAN sends the account back to PENDING_VERIFICATION ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{pc['id']}/approve", {}, admin)
        ok(s == 409 and code(d) == 'BANK_ACCOUNT_CHANGED', f'10 approve after the account changed -> 409 BANK_ACCOUNT_CHANGED ({s})')
        s, d, _ = call('POST', f"{adm}/payouts/{pc['id']}/cancel", {'reason': 'bank account changed'}, admin)
        ok(s == 200 and data(d)['status'] == 'CANCELLED', f'10 admin cancel ({s})')
        ok(d2(balance(org_a, owner)['payable']) == Decimal('900.00'), '10 balance restored after the cancel')
        s, d, _ = call('GET', f'{base}/bank-account', token=owner)
        ok(data(d)['status'] == 'PENDING_VERIFICATION', '10 account is PENDING_VERIFICATION again')

        # ---- 9. cross-org and roles
        s, _, _ = call('GET', f'/api/partner/organizations/{org_a}/payouts/balance', token=owner_b)
        ok(s == 403, f'9 org B owner reads org A balance -> 403 ({s})')
        s, _, _ = call('GET', f'/api/partner/organizations/{org_a}/payouts', token=owner_b)
        ok(s == 403, f'9 org B owner lists org A payouts -> 403 ({s})')
        s, _, _ = call('GET', f'/api/partner/organizations/{org_a}/bank-account', token=owner_b)
        ok(s == 403, f'9 org B owner reads org A bank account -> 403 ({s})')
        s, _, raw = call('GET', f"/api/partner/organizations/{org_b}/payouts/{p1['id']}", token=owner_b)
        ok(s == 404 and org_a not in raw, f"9 org B reads org A's payout id under its own path -> 404 ({s})")
        s, _, _ = call('POST', f"/api/partner/organizations/{org_b}/payouts/{p1['id']}/cancel", None, owner_b)
        ok(s == 404, f"9 org B cancels org A's payout -> 404 ({s})")
        s, _, _ = call('GET', f'{base}/payouts/balance', token=staff)
        ok(s == 200, f'9 non owner member reads the balance -> 200 ({s})')
        s, d, _ = call('POST', f'{base}/payouts', {'currency': 'TRY', 'amount': 100.00}, staff, idem())
        ok(s == 403 and code(d) == 'PAYOUT_OWNER_ONLY', f'9 non owner member requests a payout -> 403 PAYOUT_OWNER_ONLY ({s})')
        s, d, _ = call('PUT', f'{base}/bank-account', {'accountHolderName': 'Staff', 'iban': fresh_iban()}, staff)
        ok(s == 403 and code(d) == 'PAYOUT_OWNER_ONLY', f'9 non owner member changes the bank account -> 403 ({s})')
        s, _, _ = call('GET', f'{base}/payouts/balance')
        ok(s == 401, f'9 anonymous -> 401 ({s})')
        for path in ('/api/admin/payouts', f'/api/admin/organizations/{org_a}/payout-balance', '/api/admin/bank-accounts'):
            s, _, _ = call('GET', path, token=cust)
            ok(s == 403, f'9 customer on {path} -> 403 ({s})')
        s, _, _ = call('GET', '/api/admin/payouts', token=owner)
        ok(s == 403, f'9 partner owner on the admin queue -> 403 ({s})')

        # ---- 4. masking sweep over every recorded response
        leaks = [t for t, raw in RAW if not t.startswith('REVEAL') and (IBAN_PLAIN in raw or '0006 1005' in raw or '00061005' in raw)]
        ok(not leaks, f'4 no response except the audited reveal contains the plain IBAN ({leaks[:3]})')
        ok(any(t.startswith('REVEAL') and IBAN_PLAIN in raw for t, raw in RAW), '4 the audited reveal is the only response with the IBAN')
        ok(psql('payment_db', f"SELECT count(*) FROM partner_bank_account_changes WHERE organization_id='{org_a}' AND change_type='REVEALED'") == '1', '4 the reveal was audited (REVEALED row)')
        ok(psql('payment_db', f"SELECT count(*) FROM partner_bank_account_changes WHERE organization_id='{org_a}' AND (new_last4 ILIKE '%0006%' OR reason LIKE '%{IBAN_PLAIN}%')") == '0',
           '4 partner_bank_account_changes holds no full IBAN')
        ok(psql('payment_db', f"SELECT count(*) FROM outbox_event WHERE payload LIKE '%{IBAN_PLAIN}%' OR payload LIKE '%0006 1005%'") == '0', '4 outbox payloads hold no full IBAN')
        ok(psql('payment_db', f"SELECT count(*) FROM partner_bank_accounts WHERE organization_id='{org_a}' AND iban_ciphertext LIKE '%{IBAN_PLAIN}%'") == '0', '4 iban_ciphertext is not the plain IBAN')

        # ---- 11. notification (PAYOUT_PAID mail row), through the outbox relay and Kafka
        deadline = time.time() + 90
        row = ''
        while time.time() < deadline:
            row = psql('notification_db', f"SELECT body FROM notifications WHERE booking_id='{win['id']}' LIMIT 1")
            if row:
                break
            time.sleep(3)
        if not row:
            print('SKIP: 11 no notification row appeared within 90s (kafka or notification-service not running?)')
        else:
            ok(ref2 in row and 'TR** **** **** **** **** **26' in row and IBAN_PLAIN not in row and '00061005' not in row,
               '11 PAYOUT_PAID email row has the reference and the masked IBAN, never the full IBAN')
            ok(psql('notification_db', f"SELECT count(*) FROM notifications WHERE booking_id='{win['id']}'") == '1', '11 exactly one notification for the payout')
    except AssertionError as exc:
        ok(False, str(exc))
    finally:
        cleanup(hotel_id, [org_a, org_b], payout_ids, emails)
    print(f'{P}/{P + F} PASS')
    return 1 if F else 0


def cleanup(hotel_id, orgs, payout_ids, emails):
    orgs = [o for o in orgs if o]
    if orgs:
        o_in = ','.join(f"'{o}'" for o in orgs)
        # append-only payout tables: triggers are disabled only for this fixture cleanup
        for t, trg in (('payout_ledger', 'trg_payout_ledger_row'), ('payout_events', 'trg_payout_events_row'), ('partner_bank_account_changes', 'trg_bank_changes_row')):
            psql('payment_db', f'ALTER TABLE {t} DISABLE TRIGGER {trg}')
        psql('payment_db', f"DELETE FROM outbox_event WHERE aggregate_id IN (SELECT id::text FROM partner_payouts WHERE organization_id IN ({o_in}))")
        psql('payment_db', f'DELETE FROM payout_ledger WHERE organization_id IN ({o_in})')
        psql('payment_db', f'DELETE FROM payout_events WHERE payout_id IN (SELECT id FROM partner_payouts WHERE organization_id IN ({o_in}))')
        psql('payment_db', f'DELETE FROM partner_payouts WHERE organization_id IN ({o_in})')
        psql('payment_db', f'DELETE FROM partner_bank_account_changes WHERE organization_id IN ({o_in})')
        psql('payment_db', f'DELETE FROM partner_bank_accounts WHERE organization_id IN ({o_in})')
        psql('payment_db', f'DELETE FROM payout_wallets WHERE organization_id IN ({o_in})')
        for t, trg in (('payout_ledger', 'trg_payout_ledger_row'), ('payout_events', 'trg_payout_events_row'), ('partner_bank_account_changes', 'trg_bank_changes_row')):
            psql('payment_db', f'ALTER TABLE {t} ENABLE TRIGGER {trg}')
        psql('notification_db', f"DELETE FROM notifications WHERE booking_id IN ({','.join(repr(p) for p in payout_ids)})" if payout_ids else 'SELECT 1')
    # escrow and ledger rows of the fixture (psql inserted ones are in STATE['orders'], the real-flow ones too)
    tp.cleanup(hotel_id, orgs[0] if orgs else None, STATE['orders'], emails)


if __name__ == '__main__':
    sys.exit(main())
