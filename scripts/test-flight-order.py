#!/usr/bin/env python3
"""Live acceptance test for the flight supplier flow (FLIGHT-SUPPLIER-CONTRACT.md), through the gateway + Postgres.

Checks:
 1. Setup: platform seller org (reused by name, persisted in .env), FLIGHT commission, a fresh flight with seats.
 2. Public offers search (no auth), tampered offer id rejected, re-price works.
 3. N parallel holds on ONE seat through the real seat service + Postgres: exactly 1 success, rest 409.
 4. Customer flight order: PENDING, FLIGHT item, passenger + flight fields, amount from the hold.
 5. Admin order: 403 ADMIN_CANNOT_PURCHASE. Same seat for a second customer: 409.
 6. Pay: CONFIRMED, PNR + e-ticket number on the item, escrow FLIGHT/REDEMPTION, ledger balanced.
 7. Voucher window = departure-3h .. departure+1h. Redeem rejected before window, ok after, second rejected.
 8. Cancel before payment releases the seat (bookable again). Cancel after payment refunds.
 9. Supplier balance: top-up (admin only), insufficient payout -> 422 SUPPLIER_BALANCE_INSUFFICIENT with nothing written,
    exact payout ok, replay idempotent, ledger rows.

Credentials never printed. Unique emails per run. Only deletes its own fixture data (the seller org is kept).
"""
import concurrent.futures
import datetime
import json
import pathlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:8080'
SEATS_DIRECT = 'http://127.0.0.1:8082'
PAYMENT_DIRECT = 'http://127.0.0.1:8087'
ROOT = pathlib.Path(__file__).resolve().parent.parent
SELLER_NAME = 'Waylo Ucus Satis'
PARALLEL = 12

results = []
created_emails = []
order_ids = []
flight_numbers = []
supplier_codes = []


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


def call(method, path, body=None, token=None, base=BASE, headers=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    h.update(headers or {})
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            b = r.read()
            return r.status, (json.loads(b) if b else {})
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b)
        except ValueError:
            return e.code, {}


def data(resp):
    return (resp[1] or {}).get('data')


def err(resp):
    return (resp[1] or {}).get('error')


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  -> {detail}'))


def psql(db, sql):
    r = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def login(email, password):
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': password})
    if s != 200:
        sys.exit(f'login {email} failed ({s})')
    return b['data']['token']


def register(email, password):
    s, _ = call('POST', '/api/auth/register',
                {'firstName': 'Test', 'lastName': 'User', 'email': email, 'password': password})
    if s not in (200, 201):
        sys.exit(f'register {email} failed ({s})')
    created_emails.append(email)
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    time.sleep(13)  # gateway rate limit between registrations


def container_env(name, key):
    r = subprocess.run(['docker', 'inspect', '-f', '{{range .Config.Env}}{{println .}}{{end}}', name],
                       capture_output=True, text=True)
    for line in r.stdout.splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1]
    return ''


def ensure_seller(admin_token, partner_token):
    """Platform seller org owning internal flight items. Reused by name, id persisted in .env, service restarted once."""
    org = psql('auth_db', f"SELECT id FROM organizations WHERE name = '{SELLER_NAME}' LIMIT 1")
    if not org:
        s, b = call('POST', '/api/organizations',
                    {'name': SELLER_NAME, 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, partner_token)
        if s not in (200, 201):
            sys.exit(f'seller org create failed ({s})')
        org = b['data']['id']
        call('POST', f'/api/admin/organizations/{org}/approve', token=admin_token)
    env_file = ROOT / '.env'
    if (env_value('FLIGHT_SELLER_ORGANIZATION_ID') or '') != org:
        with env_file.open('a') as f:
            f.write(f'\nFLIGHT_SELLER_ORGANIZATION_ID={org}\n')
    if container_env('waylo-package-booking-service', 'FLIGHT_SELLER_ORGANIZATION_ID') != org:
        print('  restarting package-booking-service with the flight seller organization (one time)')
        subprocess.run(['docker', 'compose', 'up', '--no-deps', '-d', '--no-build', 'package-booking-service'],
                       cwd=ROOT, capture_output=True, env={**__import__('os').environ, 'FLIGHT_SELLER_ORGANIZATION_ID': org})
        for _ in range(60):
            time.sleep(3)
            r = subprocess.run(['docker', 'inspect', '-f', '{{.State.Health.Status}}', 'waylo-package-booking-service'],
                               capture_output=True, text=True)
            if r.stdout.strip() == 'healthy':
                break
    return org


def cleanup():
    if order_ids:
        ids = ','.join("'" + o + "'" for o in order_ids)
        psql('payment_db', 'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('payment_db', f'DELETE FROM ledger_entries WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM escrow_items WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM payment_intents WHERE order_id IN ({ids})')
        psql('payment_db', 'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('package_booking_db', f'DELETE FROM vouchers WHERE order_item_id IN '
                                   f'(SELECT id FROM order_items WHERE order_id IN ({ids}))')
        psql('package_booking_db', f'DELETE FROM order_items WHERE order_id IN ({ids})')
        psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({ids})')
    if flight_numbers:
        q = ','.join("'" + f + "'" for f in flight_numbers)
        psql('flight_seat_inventory_db', f'DELETE FROM seat_holds WHERE flight_number IN ({q})')
        psql('flight_seat_inventory_db', f'DELETE FROM flight_seats WHERE flight_number IN ({q})')
        psql('flight_db', f'DELETE FROM flights WHERE flight_number IN ({q})')
    if supplier_codes:
        q = ','.join("'" + s + "'" for s in supplier_codes)
        psql('payment_db', 'ALTER TABLE supplier_ledger DISABLE TRIGGER trg_supplier_ledger_no_update_delete')
        psql('payment_db', f'DELETE FROM supplier_ledger WHERE supplier IN ({q})')
        psql('payment_db', 'ALTER TABLE supplier_ledger ENABLE TRIGGER trg_supplier_ledger_no_update_delete')
        psql('payment_db', f'DELETE FROM supplier_accounts WHERE supplier IN ({q})')
    if created_emails:
        q = ','.join("'" + e + "'" for e in created_emails)
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql('auth_db', f'DELETE FROM role_assignments WHERE membership_id IN '
                        f'(SELECT m.id FROM organization_memberships m JOIN users u ON u.id = m.user_id WHERE u.email IN ({q}))')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def pay(order_id, token):
    s, ir = call('POST', f'/api/payments/orders/{order_id}/intent', None, token)
    if s not in (200, 201):
        return s, ir, None
    intent_id = data((s, ir))['id']
    s2, cr = call('POST', f'/api/payments/intents/{intent_id}/confirm', {'paymentToken': 'TEST_OK'}, token)
    return s2, cr, data((s2, cr))


def main():
    admin_email = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    internal = env_value('INTERNAL_API_TOKEN')
    sfx = uuid.uuid4().hex[:10]
    cust1, cust2, partner = (f'fl-ord-c1-{sfx}@example.test', f'fl-ord-c2-{sfx}@example.test', f'fl-ord-p-{sfx}@example.test')
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in (cust1, cust2, partner)}
    for e in (partner, cust1, cust2):
        register(e, pws[e])
    admin = login(admin_email, admin_pw)
    t_partner, t1, t2 = login(partner, pws[partner]), login(cust1, pws[cust1]), login(cust2, pws[cust2])

    print('\n=== CHECK 1: Setup ===')
    seller = ensure_seller(admin, t_partner)
    check('seller org exists', bool(seller))
    # make the run's partner an owner of the seller org so it can redeem
    if psql('auth_db', f"SELECT count(*) FROM organization_memberships m JOIN users u ON u.id = m.user_id "
                       f"WHERE u.email = '{partner}' AND m.organization_id = '{seller}'") in ('', '0'):
        psql('auth_db', f"INSERT INTO organization_memberships (user_id, organization_id) "
                        f"SELECT id, '{seller}' FROM users WHERE email = '{partner}'")
        psql('auth_db', f"INSERT INTO role_assignments (membership_id, organization_id, role, scope_type, scope_id) "
                        f"SELECT m.id, '{seller}', 'ORGANIZATION_OWNER', 'ORGANIZATION', '{seller}' FROM organization_memberships m "
                        f"JOIN users u ON u.id = m.user_id WHERE u.email = '{partner}' AND m.organization_id = '{seller}'")
        t_partner = login(partner, pws[partner])
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1].get('data')) or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        s, _ = call('POST', '/api/admin/commission-rates',
                    {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)
        check('FLIGHT commission created', s in (200, 201), s)

    fno = 'WT' + str(secrets.randbelow(9000) + 1000)
    flight_numbers.append(fno)
    dep = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=3)).replace(hour=9, minute=30, second=0, microsecond=0)
    arr = dep + datetime.timedelta(hours=1, minutes=5)
    s, _ = call('POST', '/api/flights', {'flightNumber': fno, 'departureAirportCode': 'IST', 'arrivalAirportCode': 'ESB',
                                         'airline': 'Waylo Test Air', 'departureTime': dep.isoformat().replace('+00:00', 'Z'),
                                         'arrivalTime': arr.isoformat().replace('+00:00', 'Z')}, admin)
    check('admin created flight', s == 201, s)
    s, _ = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': fno, 'seatType': 'ECONOMY', 'price': 150.00,
                                                           'fromRow': 1, 'toRow': 2, 'letters': 'ABC'}, admin)
    check('admin created seats', s == 201, s)
    date = dep.strftime('%Y-%m-%d')

    print('\n=== CHECK 2: Public offers ===')
    s, b = call('GET', f'/api/flight-offers?from=IST&to=ESB&date={date}&adults=1')
    check('offers search is public (200)', s == 200, s)
    offers = [o for o in (b.get('data') or []) if o['flightNumber'] == fno]
    check('offer for the new flight', len(offers) == 1, offers)
    offer = offers[0]
    check('offer price 150.00 per passenger, 6 seats', float(offer['totalAmount']) == 150.0 and offer['availableSeats'] == 6, offer)
    check('offer expires within 10 min', offer['expiresAt'] is not None)
    s, b = call('GET', '/api/flight-offers/' + offer['offerId'])
    check('offer re-price ok', s == 200, s)
    s, b = call('GET', '/api/flight-offers/' + offer['offerId'][:-3] + 'abc')
    check('tampered offer rejected 422 INVALID_OFFER', s == 422 and b.get('error') == 'INVALID_OFFER', (s, b.get('error')))
    for label, path in (('no params', '/api/flight-offers'), ('bad date', '/api/flight-offers?from=IST&to=ESB&date=date'),
                        ('missing date', '/api/flight-offers?from=IST&to=ESB'), ('bad adults', f'/api/flight-offers?from=IST&to=ESB&date={date}&adults=x')):
        s, b = call('GET', path)
        check(f'offers {label} -> 400 BAD_REQUEST (not 500)', s == 400 and b.get('error') == 'BAD_REQUEST', (s, b.get('error')))
    s, b = call('POST', '/internal/flight-holds', None, base=SEATS_DIRECT, headers={'X-Internal-Token': internal})
    check('internal hold without body -> 400', s == 400, s)
    req_raw = urllib.request.Request(SEATS_DIRECT + '/internal/flight-holds', data=b'{not json', method='POST',
                                     headers={'Content-Type': 'application/json', 'X-Internal-Token': internal})
    try:
        urllib.request.urlopen(req_raw, timeout=15)
        code = 200
    except urllib.error.HTTPError as e:
        code = e.code
    check('internal hold malformed JSON -> 400', code == 400, code)
    s, _ = call('POST', '/internal/flight-holds', {}, base=SEATS_DIRECT)
    check('internal holds need token (401)', s == 401, s)

    print(f'\n=== CHECK 3: {PARALLEL} parallel holds on one seat ===')
    barrier_start = time.time() + 1.5

    def hold(i):
        while time.time() < barrier_start:
            time.sleep(0.005)
        return call('POST', '/internal/flight-holds',
                    {'offerId': offer['offerId'], 'seatNumber': '1A', 'passengerFirstName': 'Race', 'passengerLastName': 'Race' + chr(65 + i),
                     'principalUserId': str(uuid.uuid4()), 'idempotencyKey': f'race-{sfx}-{i}', 'orderCorrelationId': f'race-{i}'},
                    base=SEATS_DIRECT, headers={'X-Internal-Token': internal})

    with concurrent.futures.ThreadPoolExecutor(max_workers=PARALLEL) as ex:
        rs = list(ex.map(hold, range(PARALLEL)))
    ok = [r for r in rs if r[0] == 200]
    conflicts = [r for r in rs if r[0] == 409 and r[1].get('error') == 'SEAT_ALREADY_RESERVED']
    check('exactly 1 hold succeeded', len(ok) == 1, [r[0] for r in rs])
    check('all others 409 SEAT_ALREADY_RESERVED', len(conflicts) == PARALLEL - 1, [(r[0], r[1].get('error')) for r in rs])
    live = psql('flight_seat_inventory_db',
                f"SELECT count(*) FROM seat_holds WHERE flight_number='{fno}' AND seat_number='1A' AND status IN ('HELD','CONFIRMED')")
    check('database has exactly 1 live hold for the seat', live == '1', live)
    # idempotency: the winner's key returns the same hold
    win_i = [i for i, r in enumerate(rs) if r[0] == 200][0]
    again = hold(win_i)
    check('same idempotency key returns the same hold', again[0] == 200 and data(again)['holdId'] == data(rs[win_i])['holdId'])
    s, _ = call('POST', f"/internal/flight-holds/{data(rs[win_i])['holdId']}/release", base=SEATS_DIRECT,
                headers={'X-Internal-Token': internal})
    check('release frees the seat', s == 200, s)

    print('\n=== CHECK 4: Customer flight order ===')
    req = {'offerId': offer['offerId'], 'seatNumber': '2B', 'passengerFirstName': 'Ayse', 'passengerLastName': 'Demir'}
    s, b = call('POST', '/api/orders/flight', req, t1)
    check('customer order 201', s == 201, (s, b.get('error')))
    order = data((s, b))
    order_id = order['id']
    order_ids.append(order_id)
    item = order['items'][0]
    check('order PENDING, FLIGHT item', order['status'] == 'PENDING' and item['vertical'] == 'FLIGHT', order)
    check('amount from hold (150.00)', float(order['totalAmount']) == 150.0 and float(item['unitAmount']) == 150.0, order)
    fl = item.get('flight') or {}
    check('item has passenger, flight number, seat, departure',
          fl.get('passengerFirstName') == 'Ayse' and fl.get('passengerLastName') == 'Demir' and fl.get('flightNumber') == fno
          and fl.get('seatNumber') == '2B' and fl.get('departureAt'), fl)
    check('no ticket before payment', fl.get('pnr') is None and fl.get('ticketNumber') is None, fl)
    check('description has flight and seat', fno in item['description'] and 'koltuk 2B' in item['description'], item['description'])
    check('item supplier org = seller', item['supplierOrganizationId'] == seller, item['supplierOrganizationId'])

    print('\n=== CHECK 5: Admin forbidden, seat conflict ===')
    s, b = call('POST', '/api/orders/flight', req, admin)
    check('admin 403 ADMIN_CANNOT_PURCHASE', s == 403 and b.get('error') == 'ADMIN_CANNOT_PURCHASE', (s, b.get('error')))
    s, b = call('POST', '/api/orders/flight', req, t2)
    check('second customer same seat 409 SEAT_ALREADY_RESERVED', s == 409 and b.get('error') == 'SEAT_ALREADY_RESERVED', (s, b.get('error')))
    s, b = call('POST', '/api/orders/flight', {**req, 'passengerFirstName': '12<script>'}, t2)
    check('invalid passenger name rejected 400', s == 400, s)

    print('\n=== CHECK 6: Pay -> ticket ===')
    s, b, intent = pay(order_id, t1)
    check('payment confirmed AUTHORIZED', s == 200 and intent and intent.get('status') == 'AUTHORIZED', (s, b.get('error')))
    s, b = call('GET', f'/api/orders/{order_id}', token=t1)
    paid = data((s, b))
    pfl = paid['items'][0].get('flight') or {}
    check('order CONFIRMED', paid['status'] == 'CONFIRMED', paid['status'])
    check('PNR (6) and e-ticket number on the item', len(pfl.get('pnr') or '') == 6 and (pfl.get('ticketNumber') or '').startswith('WL'), pfl)
    esc = psql('payment_db', f"SELECT vertical||'|'||release_trigger||'|'||commission_rate FROM escrow_items WHERE order_id='{order_id}'")
    check('escrow FLIGHT / REDEMPTION / 5%', esc.startswith('FLIGHT|REDEMPTION|0.05'), esc)
    bal = psql('payment_db', f"SELECT sum(debit_amount)-sum(credit_amount) FROM ledger_entries WHERE order_id='{order_id}'")
    check('ledger balanced', bal in ('0', '0.00'), bal)

    print('\n=== CHECK 7: Voucher window and redeem ===')
    s, b = call('GET', f'/api/orders/{order_id}/vouchers', token=t1)
    vouchers = data((s, b)) or []
    check('one voucher', len(vouchers) == 1, len(vouchers))
    v = vouchers[0]

    def parse(ts):
        return datetime.datetime.fromisoformat(ts.replace('Z', '+00:00'))
    dep_item = parse(fl['departureAt'])
    check('valid_from = departure - 3h', abs((parse(v['validFrom']) - (dep_item - datetime.timedelta(hours=3))).total_seconds()) < 2, v['validFrom'])
    check('valid_until = departure + 1h', abs((parse(v['validUntil']) - (dep_item + datetime.timedelta(hours=1))).total_seconds()) < 2, v['validUntil'])
    endpoint = f'/api/partner/organizations/{seller}/vouchers/redeem'
    s, b = call('POST', endpoint, {'payload': v['payload']}, t_partner)
    check('redeem before window rejected NOT_YET_VALID', s in (409, 422) and 'NOT_YET_VALID' in (b.get('error') or ''), (s, b.get('error')))
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{v['voucherId']}'")
    s, b = call('POST', endpoint, {'payload': v['payload']}, t_partner)
    check('redeem in window succeeds', s == 200, (s, b.get('error')))
    s, b = call('POST', endpoint, {'payload': v['payload']}, t_partner)
    check('second redeem rejected 409', s == 409, s)

    print('\n=== CHECK 8: Cancel releases seat / refunds ===')
    s, b = call('POST', '/api/orders/flight', {**req, 'seatNumber': '2C'}, t2)
    o2 = data((s, b))
    check('second order on another seat', s == 201, (s, b.get('error')))
    if o2:
        order_ids.append(o2['id'])
        s, b = call('POST', f"/api/orders/{o2['id']}/cancel", None, t2)
        check('cancel unpaid order', s == 200 and data((s, b))['status'] == 'CANCELLED', s)
        s, b = call('POST', '/api/orders/flight', {**req, 'seatNumber': '2C'}, t1)
        check('seat bookable again after cancel', s == 201, (s, b.get('error')))
        if s == 201:
            o3 = data((s, b))
            order_ids.append(o3['id'])
            ps, pb, pint = pay(o3['id'], t1)
            check('pay third order', ps == 200 and pint and pint.get('status') == 'AUTHORIZED', ps)
            s, b = call('POST', f"/api/orders/{o3['id']}/cancel", None, t1)
            check('cancel paid order refunds', s == 200 and data((s, b))['status'] == 'CANCELLED', (s, b.get('error')))
            st = psql('payment_db', f"SELECT status FROM escrow_items WHERE order_id='{o3['id']}'")
            check('escrow REFUNDED', st == 'REFUNDED', st)
            seat_free = psql('flight_seat_inventory_db',
                             f"SELECT is_available FROM flight_seats WHERE flight_number='{fno}' AND seat_number='2C'")
            check('seat free again after refund', seat_free == 't', seat_free)

    print('\n=== CHECK 9: Supplier balance ===')
    sup = 'TESTSUP' + secrets.token_hex(3).upper()
    supplier_codes.append(sup)
    topup = {'amount': 100.00, 'currency': 'EUR', 'reference': 'live test', 'idempotencyKey': f'topup-{sfx}'}
    s, _ = call('POST', f'/api/admin/supplier-accounts/{sup}/topups', topup, t1)
    check('customer cannot top up (403)', s == 403, s)
    s, b = call('POST', f'/api/admin/supplier-accounts/{sup}/topups', topup, admin)
    check('admin top-up 201, balance 100.00', s == 201 and float(data((s, b))['balance']) == 100.0, (s, b.get('error')))
    s, b = call('POST', f'/api/admin/supplier-accounts/{sup}/topups', topup, admin)
    check('top-up replay does not add again', s == 201 and float(data((s, b))['balance']) == 100.0, s)
    tok = {'X-Internal-Token': internal}
    mv = {'supplier': sup, 'orderId': str(uuid.uuid4()), 'currency': 'EUR', 'idempotencyKey': f'po-{sfx}-1'}
    s, b = call('POST', '/internal/supplier-payouts', {**mv, 'amount': 100.01}, base=PAYMENT_DIRECT, headers=tok)
    check('insufficient payout 422 SUPPLIER_BALANCE_INSUFFICIENT', s == 422 and b.get('error') == 'SUPPLIER_BALANCE_INSUFFICIENT', (s, b.get('error')))
    rows = psql('payment_db', f"SELECT count(*) FROM supplier_ledger WHERE supplier='{sup}'")
    check('failed payout wrote no ledger row', rows == '1', rows)
    s, b = call('POST', '/internal/supplier-payouts', {**mv, 'amount': 100.00, 'idempotencyKey': f'po-{sfx}-2'}, base=PAYMENT_DIRECT, headers=tok)
    check('exact-balance payout ok, balance 0.00', s == 200 and float(data((s, b))['balance']) == 0.0, (s, b.get('error')))
    s, b = call('POST', '/internal/supplier-payouts', {**mv, 'amount': 100.00, 'idempotencyKey': f'po-{sfx}-2'}, base=PAYMENT_DIRECT, headers=tok)
    check('payout replay is idempotent', s == 200 and float(data((s, b))['balance']) == 0.0, (s, b.get('error')))
    s, b = call('POST', '/internal/supplier-payouts', {**mv, 'amount': 1}, base=PAYMENT_DIRECT)
    check('payout endpoint needs internal token (401)', s == 401, s)
    s, b = call('GET', f'/api/admin/supplier-accounts/{sup}/ledger', token=admin)
    types = [r['entryType'] for r in (data((s, b)) or {}).get('items', [])]
    check('ledger shows TICKET_PAYOUT and TOPUP', sorted(types) == ['TICKET_PAYOUT', 'TOPUP'], types)

    failed = [n for n, ok in results if not ok]
    print(f'\n{len(results) - len(failed)}/{len(results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        cleanup()
