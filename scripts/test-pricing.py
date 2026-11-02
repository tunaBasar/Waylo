#!/usr/bin/env python3
"""Live acceptance test for the pricing-service commercial layer (PRICING-CONTRACT.md) through car orders.

Checks:
 1. Setup: sellable CAR_RENTAL org (reuses test-car-order.py helpers), admin creates PLATFORM campaign + code (scoped to the org).
 2. Baseline car order without code: gross == net, no discount.
 3. Car order with code: discount applied, net = gross - discount, unit_amount = net.
 4. Pay both: escrow commission on GROSS, supplier share unchanged vs no-code, ledger balanced, PLATFORM_DISCOUNT_COST on release.
 5. Cancel a coded order releases the code usage (uses_count back).
 6. Global usage limit under 10 parallel orders: never more successes than the limit.
 7. Unknown code -> 422 PROMO_CODE_INVALID.
 8. pricing-service stopped: no code -> order still works, with code -> 503 PRICING_UNAVAILABLE.

Credentials never printed. Unique emails per run. Only deletes own fixture data. pricing-service is restarted at the end.
"""
import concurrent.futures
import datetime
import importlib.util
import pathlib
import secrets
import subprocess
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('car_order_helpers', HERE / 'test-car-order.py')
car = importlib.util.module_from_spec(spec)
spec.loader.exec_module(car)

call, check, data, err, psql, login, register = car.call, car.check, car.data, car.err, car.psql, car.login, car.register
campaign_ids = []
INTERNAL_TOKEN = car.env_value('INTERNAL_API_TOKEN')
PAYMENT_INTERNAL = 'http://localhost:8087'
LIMIT = 3


def istanbul_now():
    return datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=3))).replace(tzinfo=None)


def iso(dt):
    return dt.strftime('%Y-%m-%dT%H:%M:%S')


def make_campaign(admin_token, org_id, name, code, pct, max_global=None):
    now = istanbul_now()
    body = {'name': name, 'vertical': 'ARAÇ', 'scopeOrgId': org_id, 'discountType': 'PERCENTAGE', 'discountValue': pct,
            'startAt': iso(now - datetime.timedelta(hours=1)), 'endAt': iso(now + datetime.timedelta(days=2)),
            'fundedBy': 'PLATFORM', 'description': 'test-pricing'}
    if max_global:
        body['maxUsesGlobal'] = max_global
    s, resp, _ = call('POST', '/api/admin/campaigns', body, token=admin_token)
    check(f'admin creates campaign {name}', s in (200, 201), (s, resp))
    cid = (data(resp) or resp or {}).get('id') if isinstance(resp, dict) else None
    if cid:
        campaign_ids.append(cid)
    s, resp, _ = call('POST', '/api/admin/promo-codes', {
        'campaignId': cid, 'codes': [{'code': code, 'validFrom': body['startAt'], 'validUntil': body['endAt']}]}, token=admin_token)
    check(f'admin creates code {code}', s in (200, 201), (s, resp))
    return cid


def car_payload(branch, pickup, ret, day, code=None):
    shift = datetime.timedelta(days=day * 3)
    p = {'pickupBranchId': branch, 'returnBranchId': branch, 'category': 'ECONOMY', 'transmission': 'MANUAL',
         'pickupAt': iso(pickup + shift), 'returnAt': iso(ret + shift), 'driverAge': 30}
    if code:
        p['promoCode'] = code
    return p


def money(v):
    return round(float(v), 2)


def pay(order_id, token):
    s, r, _ = call('POST', f'/api/payments/orders/{order_id}/intent', None, token)
    iid = (data(r) or {}).get('id')
    s2, _, _ = call('POST', f'/api/payments/intents/{iid}/confirm', {'paymentToken': 'TEST_OK'}, token)
    return s in (200, 201) and s2 == 200


def release_escrow(order_item_id):
    s, _, _ = call('POST', f'/internal/escrow-items/{order_item_id}/release', None, base=PAYMENT_INTERNAL,
                   headers={'X-Internal-Token': INTERNAL_TOKEN})
    return s == 200


def ledger_sum(order_id, account, col):
    v = psql('payment_db', f"SELECT COALESCE(SUM({col}),0) FROM ledger_entries WHERE order_id='{order_id}' AND account='{account}'")
    return money(v or 0)


def ledger_balance(order_id):
    return money(psql('payment_db', f"SELECT COALESCE(SUM(debit_amount-credit_amount),0) FROM ledger_entries WHERE order_id='{order_id}'") or 0)


def uses(code):
    return int(psql('pricing_db', f"SELECT uses_count FROM promo_codes WHERE code='{code}'") or -1)


def cleanup_pricing():
    if campaign_ids:
        ids = ','.join("'" + c + "'" for c in campaign_ids)
        psql('pricing_db', f'DELETE FROM promo_reservations WHERE promo_code_id IN (SELECT id FROM promo_codes WHERE campaign_id IN ({ids}))')
        psql('pricing_db', f'DELETE FROM promo_codes WHERE campaign_id IN ({ids})')
        psql('pricing_db', f'DELETE FROM campaigns WHERE id IN ({ids})')


def main():
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    cust_email = f'prc-cust-{uuid.uuid4().hex[:10]}@example.test'
    partner_email = f'prc-partner-{uuid.uuid4().hex[:10]}@example.test'
    pw_cust = 'Cust1' + secrets.token_urlsafe(16)
    pw_partner = 'Part1' + secrets.token_urlsafe(16)
    for e, pw in [(cust_email, pw_cust), (partner_email, pw_partner)]:
        register(e, pw)
    for e in [cust_email, partner_email]:
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)
    partner_token = login(partner_email, pw_partner)
    cust_token = login(cust_email, pw_cust)
    admin_token = login(admin_email, admin_pw)

    print('\n=== CHECK 1: setup org + PLATFORM campaign and code ===')
    org_id, branch, _vehicle, pickup_dt, return_dt, _ps, _rs = car._setup_org(admin_token, partner_token, partner_email)
    pickup, ret = pickup_dt.replace(tzinfo=None), return_dt.replace(tzinfo=None)
    tag = uuid.uuid4().hex[:6].upper()
    code = 'PRC' + tag
    make_campaign(admin_token, org_id, 'test-pricing 10% ' + tag, code, 10)

    print('\n=== CHECK 2: baseline order without code ===')
    s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 0), token=cust_token)
    check('no-code order 201', s == 201, (s, r))
    base = data(r)
    car.order_ids.append(base['id'])
    bi = base['items'][0]
    check('baseline gross == net, no discount',
          money(bi['grossAmount']) == money(bi['netAmount']) == money(bi['unitAmount']) and money(bi['discountAmount']) == 0, bi)
    gross = money(bi['grossAmount'])

    print('\n=== CHECK 3: order with code ===')
    before = uses(code)
    s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 1, code), token=cust_token)
    check('coded order 201', s == 201, (s, r))
    coded = data(r)
    car.order_ids.append(coded['id'])
    ci = coded['items'][0]
    check('coded gross equals baseline gross', money(ci['grossAmount']) == gross, ci)
    check('discount is 10% of gross', money(ci['discountAmount']) == round(gross * 0.10, 2), ci)
    check('funded by PLATFORM', ci['discountFundedBy'] == 'PLATFORM', ci)
    check('net = gross - discount and unit_amount = net',
          money(ci['netAmount']) == round(gross - money(ci['discountAmount']), 2) == money(ci['unitAmount']), ci)
    check('order total == net', money(coded['totalAmount']) == money(ci['netAmount']), coded['totalAmount'])
    check('promo code stored', ci.get('promoCode') == code, ci.get('promoCode'))
    check('code usage reserved (+1)', uses(code) == before + 1, (before, uses(code)))

    print('\n=== CHECK 4: pay, commission on gross, supplier unchanged, ledger balanced ===')
    check('baseline paid', pay(base['id'], cust_token))
    check('coded paid', pay(coded['id'], cust_token))
    row = lambda oid: psql('payment_db', f"SELECT gross_amount,charged_amount,commission_amount,supplier_amount,discount_amount FROM escrow_items WHERE order_id='{oid}'").split('|')
    b, c = row(base['id']), row(coded['id'])
    check('escrow charged = net for coded', len(c) == 5 and money(c[1]) == money(ci['netAmount']), c)
    check('commission on gross identical to no-code', len(c) == 5 and money(c[2]) == money(b[2]), (b, c))
    check('supplier amount unchanged vs no-code', len(c) == 5 and money(c[3]) == money(b[3]), (b, c))
    check('both items released', release_escrow(bi['id']) and release_escrow(ci['id']))
    check('supplier payable unchanged in ledger',
          ledger_sum(coded['id'], 'SUPPLIER_PAYABLE', 'credit_amount') == ledger_sum(base['id'], 'SUPPLIER_PAYABLE', 'credit_amount'))
    check('platform bears discount (PLATFORM_DISCOUNT_COST)',
          ledger_sum(coded['id'], 'PLATFORM_DISCOUNT_COST', 'debit_amount') == money(ci['discountAmount']))
    check('no discount cost on baseline', ledger_sum(base['id'], 'PLATFORM_DISCOUNT_COST', 'debit_amount') == 0)
    check('baseline ledger balanced', ledger_balance(base['id']) == 0)
    check('coded ledger balanced', ledger_balance(coded['id']) == 0)

    print('\n=== CHECK 5: cancel releases code usage ===')
    before = uses(code)
    s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 2, code), token=cust_token)
    check('second coded order 201', s == 201, (s, r))
    o3 = data(r)
    car.order_ids.append(o3['id'])
    check('usage +1 after order', uses(code) == before + 1, (before, uses(code)))
    s, _, _ = call('POST', f"/api/orders/{o3['id']}/cancel", None, token=cust_token)
    check('cancel ok', s == 200, s)
    check('usage back after cancel', uses(code) == before, (before, uses(code)))

    print('\n=== CHECK 6: global usage limit under 10 parallel orders ===')
    limit_code = 'LIM' + tag
    make_campaign(admin_token, org_id, 'test-pricing limit ' + tag, limit_code, 5, max_global=LIMIT)

    def one(day):
        s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, day, limit_code), token=cust_token)
        return s, r
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:
        outcomes = list(ex.map(one, range(3, 13)))
    ok = [r for s, r in outcomes if s == 201]
    for r in ok:
        car.order_ids.append(data(r)['id'])
    rejected = [err(r) for s, r in outcomes if s != 201]
    print(f'   parallel: {len(ok)} created, rejected codes {sorted(set(map(str, rejected)))}')
    check(f'global limit {LIMIT} never exceeded', len(ok) <= LIMIT, len(ok))
    check(f'exactly {LIMIT} succeeded', len(ok) == LIMIT, len(ok))
    check('others rejected with PROMO_CODE_MAX_USES_REACHED', all(e == 'PROMO_CODE_MAX_USES_REACHED' for e in rejected), rejected)
    check('uses_count == successes', uses(limit_code) == len(ok), uses(limit_code))
    for r in ok:
        call('POST', f"/api/orders/{data(r)['id']}/cancel", None, token=cust_token)
    check('cancelling all frees the code', uses(limit_code) == 0, uses(limit_code))

    print('\n=== CHECK 7: unknown code -> 422 ===')
    s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 13, 'NOSUCH' + tag), token=cust_token)
    check('unknown code 422', s == 422, (s, r))
    check('error PROMO_CODE_INVALID', err(r) == 'PROMO_CODE_INVALID', err(r))
    call('POST', f"/api/orders/{base['id']}/cancel", None, token=cust_token)

    print('\n=== CHECK 8: pricing-service stopped ===')
    subprocess.run(['docker', 'stop', 'waylo-pricing-service'], capture_output=True)
    try:
        time.sleep(3)
        s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 14), token=cust_token)
        check('no code: order still works (201)', s == 201, (s, r))
        if s == 201:
            car.order_ids.append(data(r)['id'])
            it = data(r)['items'][0]
            check('no code: gross == net', money(it['grossAmount']) == money(it['netAmount']) == gross, it)
        s, r, _ = call('POST', '/api/orders/car', car_payload(branch, pickup, ret, 15, code), token=cust_token)
        check('with code: 503', s == 503, (s, r))
        check('error PRICING_UNAVAILABLE', err(r) == 'PRICING_UNAVAILABLE', err(r))
    finally:
        subprocess.run(['docker', 'start', 'waylo-pricing-service'], capture_output=True)

    failed = [n for n, ok_ in car.results if not ok_]
    print(f'\n{len(car.results) - len(failed)}/{len(car.results)} checks passed')
    if failed:
        print('FAILED: ' + '; '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        try:
            car.cleanup()
        finally:
            cleanup_pricing()
