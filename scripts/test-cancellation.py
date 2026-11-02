#!/usr/bin/env python3
"""Live acceptance for the cancellation policy (CANCELLATION-CONTRACT.md), through the gateway.

Own fixture: approved org + hotel with a FREE_CANCELLATION and a NON_REFUNDABLE rate plan, 10% partner-scoped commission.
Voucher/escrow times are shifted via psql only for the fixture rows. Cleans up everything it created.
"""
import importlib.util
import json
import pathlib
import subprocess
import sys
import time
import uuid
from datetime import date, timedelta
from decimal import Decimal

ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('tp', ROOT / 'scripts' / 'test-payments.py')
tp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tp)
req, must, psql, register, env_value = tp.req, tp.must, tp.psql, tp.register, tp.env_value
P = F = 0


def ok(cond, label):
    global P, F
    if cond:
        P += 1
        print('PASS:', label)
    else:
        F += 1
        print('FAIL:', label)


def ledger_balance(order_id):
    return psql('payment_db', f"SELECT coalesce(sum(debit_amount)-sum(credit_amount),0) FROM ledger_entries WHERE order_id='{order_id}'")


def ledger_net(order_id, account):
    return Decimal(psql('payment_db', f"SELECT coalesce(sum(credit_amount-debit_amount),0) FROM ledger_entries "
                                      f"WHERE order_id='{order_id}' AND account='{account}'"))


def ledger_count(order_id):
    return int(psql('payment_db', f"SELECT count(*) FROM ledger_entries WHERE order_id='{order_id}'"))


def main():
    emails, order_ids, hotel_id, org_id = [], [], None, None
    tp.other_orgs = []
    try:
        admin = must('POST', '/api/auth/login', {'email': env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'),
                                                 'password': env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')})['token']
        e, owner = register('can-owner'); emails.append(e)
        e, cust = register('can-cust'); emails.append(e)
        e, other = register('can-other'); emails.append(e)
        org_id = must('POST', '/api/organizations', {'name': f'can-test-{uuid.uuid4().hex[:6]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, owner)['id']
        hotel_id = must('POST', '/api/hotels', {'name': 'Cancel test hotel', 'city': f'CT{uuid.uuid4().hex[:6]}', 'country': 'TR',
                                                'address': 'Test only', 'starRating': 3, 'organizationId': org_id}, owner)['id']
        must('POST', f'/api/admin/organizations/{org_id}/approve', token=admin)
        room = must('POST', f'/api/hotels/{hotel_id}/room-types', {'code': 'CT_DBL', 'name': 'CT double', 'maxAdults': 2,
                                                                  'maxChildren': 0, 'maxOccupancy': 2}, owner)
        flex = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'CT_FLEX', 'name': 'CT flex', 'mealPlan': 'BREAKFAST',
                                                                  'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, owner)
        nr = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'CT_NR', 'name': 'CT non refundable', 'mealPlan': 'BREAKFAST',
                                                                'cancellationPolicy': 'NON_REFUNDABLE'}, owner)
        start = date.today() + timedelta(days=40)
        nights = [(start + timedelta(days=i)).isoformat() for i in range(2)]
        must('PUT', f'/api/hotels/{hotel_id}/inventory', {'items': [
            {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 8, 'expectedVersion': None} for n in nights],
            'reason': 'cancellation acceptance fixture'}, owner)
        must('PUT', f'/api/hotels/{hotel_id}/rates', {'items': [
            {'roomTypeId': room['id'], 'ratePlanId': p['id'], 'stayDate': n, 'occupancy': 2, 'currency': 'TRY', 'amount': a}
            for p in (flex, nr) for n, a in zip(nights, [1000.00, 1500.00])]}, owner)
        must('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org_id, 'rate': 0.1000}, admin)

        def paid_order(plan):
            q = must('POST', '/api/quotes', {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                             'checkIn': nights[0], 'checkOut': (start + timedelta(days=2)).isoformat(),
                                             'rooms': 1, 'adults': 2, 'childrenAges': []}, cust)
            o = must('POST', '/api/orders/hotel', {'quoteId': q['quoteId']}, cust)
            order_ids.append(o['id'])
            i = must('POST', f"/api/payments/orders/{o['id']}/intent", None, cust)
            must('POST', f"/api/payments/intents/{i['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
            return o['id']

        def item_of(oid):
            return must('GET', f'/api/orders/{oid}', None, cust)['items'][0]

        # --- A: free-cancel hotel item, preview == actual, double cancel idempotent ---
        a = paid_order(flex)
        ok(item_of(a)['cancellationPolicy'] == 'FREE_CANCELLATION', 'flex item snapshot FREE_CANCELLATION')
        pa = must('GET', f'/api/orders/{a}/cancellation-preview', None, cust)
        ok(pa['cancellable'] and Decimal(str(pa['totalRefund'])) == Decimal('2500.00'), f"preview flex refund 2500 ({pa['totalRefund']})")
        ok(req('GET', f'/api/orders/{a}/cancellation-preview', None, other)[0] in (403, 404), 'stranger cannot preview')
        ok(ledger_count(a) == 2, 'preview wrote no ledger entries')
        must('POST', f'/api/orders/{a}/cancel', None, cust)
        ia = item_of(a)
        ok(ia['cancelOutcome'] == 'REFUND' and Decimal(str(ia['refundAmount'])) == Decimal(str(pa['totalRefund'])), 'actual refund equals preview')
        ok(psql('payment_db', f"SELECT status||'|'||settlement_reason FROM escrow_items WHERE order_id='{a}'") == 'REFUNDED|CANCEL_REFUND', 'escrow REFUNDED')
        ok(ledger_net(a, 'CUSTOMER_REFUND') == Decimal('2500.00') and ledger_balance(a) in ('0', '0.00'), 'ledger refund 2500, balanced')
        n = ledger_count(a)
        s, d = req('POST', f'/api/orders/{a}/cancel', None, cust)
        ok(s == 422 and 'ALREADY_CANCELLED' in json.dumps(d), f'double cancel -> 422 ALREADY_CANCELLED ({s})')
        ok(ledger_count(a) == n, 'double cancel wrote no ledger entries')
        ok(must('GET', f'/api/orders/{a}/cancellation-preview', None, cust)['blockedReason'] == 'ALREADY_CANCELLED', 'preview after cancel is blocked')

        # --- B: non-refundable -> 0 refund, partner payable released ---
        b = paid_order(nr)
        ok(item_of(b)['cancellationPolicy'] == 'NON_REFUNDABLE', 'nr item snapshot NON_REFUNDABLE')
        pb = must('GET', f'/api/orders/{b}/cancellation-preview', None, cust)
        ok(Decimal(str(pb['totalRefund'])) == 0 and pb['items'][0]['outcome'] == 'NO_REFUND', 'preview nr refund 0')
        must('POST', f'/api/orders/{b}/cancel', None, cust)
        ib = item_of(b)
        ok(ib['cancelOutcome'] == 'NO_REFUND' and Decimal(str(ib['refundAmount'])) == 0, 'actual nr refund 0 equals preview')
        ok(psql('payment_db', f"SELECT status||'|'||settlement_reason FROM escrow_items WHERE order_id='{b}'") == 'RELEASED|CANCEL_NO_REFUND', 'escrow RELEASED to partner')
        ok(ledger_net(b, 'SUPPLIER_PAYABLE') == Decimal('2250.00') and ledger_net(b, 'PLATFORM_COMMISSION') == Decimal('250.00'),
           'supplier payable 2250 + commission 250')
        ok(ledger_net(b, 'CUSTOMER_REFUND') == 0 and ledger_balance(b) in ('0', '0.00'), 'no customer refund, balanced')
        ok(psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id='{b}'") in ('RELEASED', 'CONFIRMED'), 'hotel hold handled')
        n = ledger_count(b)
        ok(req('POST', f'/api/orders/{b}/cancel', None, cust)[0] == 422 and ledger_count(b) == n, 'nr double cancel idempotent')

        # --- C: redeemed item cannot be cancelled ---
        c = paid_order(flex)
        v = must('GET', f'/api/orders/{c}/vouchers', None, cust)[0]
        psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{v['voucherId']}'")
        s, _ = req('POST', f'/api/partner/organizations/{org_id}/vouchers/redeem', {'payload': v['payload']}, owner)
        ok(s == 200, f'redeem ok ({s})')
        pc = must('GET', f'/api/orders/{c}/cancellation-preview', None, cust)
        ok(pc['cancellable'] is False and pc['blockedReason'] == 'ALREADY_USED', 'preview of redeemed order not cancellable')
        n = ledger_count(c)
        s, d = req('POST', f'/api/orders/{c}/cancel', None, cust)
        ok(s == 422 and 'ESCROW_ALREADY_RELEASED' in json.dumps(d), f'cancel redeemed -> 422 ({s})')
        ok(ledger_count(c) == n and psql('package_booking_db', f"SELECT status FROM orders WHERE id='{c}'") == 'CONFIRMED', 'order unchanged, no ledger change')

        # --- D: no-show job releases escrow after the voucher window (flex and nr) ---
        d1, d2 = paid_order(flex), paid_order(nr)
        for oid in (d1, d2):
            psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '2 hour', valid_until = now() - interval '1 minute' "
                                       f"WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id='{oid}')")
        deadline = time.time() + 150
        while time.time() < deadline:
            if psql('payment_db', f"SELECT count(*) FROM escrow_items WHERE order_id IN ('{d1}','{d2}') AND status='RELEASED'") == '2':
                break
            time.sleep(5)
        for oid, name in ((d1, 'flex'), (d2, 'nr')):
            ok(psql('payment_db', f"SELECT status||'|'||settlement_reason FROM escrow_items WHERE order_id='{oid}'") == 'RELEASED|NO_SHOW',
               f'no-show {name}: escrow RELEASED (NO_SHOW)')
            ok(ledger_net(oid, 'SUPPLIER_PAYABLE') == Decimal('2250.00') and ledger_balance(oid) in ('0', '0.00'), f'no-show {name}: payable 2250, balanced')
            ok(psql('package_booking_db', f"SELECT v.status FROM vouchers v JOIN order_items i ON i.id=v.order_item_id WHERE i.order_id='{oid}'") == 'NO_SHOW',
               f'no-show {name}: voucher NO_SHOW')
            ok(item_of(oid)['cancelOutcome'] == 'NO_SHOW', f'no-show {name}: item outcome NO_SHOW')
        n = ledger_count(d1)
        time.sleep(70)  # at least one more scheduler run
        ok(ledger_count(d1) == n, 'no-show job idempotent across runs')
        s, _ = req('POST', f'/api/orders/{d1}/cancel', None, cust)
        ok(s == 422, f'cancel after no-show rejected ({s})')
    except AssertionError as exc:
        ok(False, str(exc))
    finally:
        tp.cleanup(hotel_id, org_id, order_ids, emails)
    print(f'{P}/{P + F} PASS')
    return 1 if F else 0


if __name__ == '__main__':
    sys.exit(main())
