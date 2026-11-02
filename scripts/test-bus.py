#!/usr/bin/env python3
"""Live test of the bus vertical through the gateway and the real bus-service + Postgres. Self-cleaning.

Needs bus-service running with BUS_SALES_ENABLED=true (default in compose is false):
  BUS_SALES_ENABLED=true docker compose up -d bus-service
Covers partner/org rules, documents and sellability, search, seat map, parallel holds on one seat
(exactly one success), idempotency, segment reuse, gender rule, confirm/release. Secrets are never printed.

  python3 scripts/test-bus.py
"""
import base64
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
BUS = 'http://localhost:8096'
ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
PARALLEL = 12

results, created_orgs, emails, cities, order_ids = [], [], [], [], []


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


INTERNAL = env_value('INTERNAL_API_TOKEN')


def call(method, path, body=None, token=None, base=BASE, headers=None, raw=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    h.update(headers or {})
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(base + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            b = r.read()
            return r.status, (json.loads(b) if b and r.headers.get('Content-Type', '').startswith('application/json') else b)
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b)
        except ValueError:
            return e.code, b


def multipart(fields, filename, content):
    boundary = '----t' + uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
             for k, v in fields.items() if v is not None]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                 f'Content-Type: application/octet-stream\r\n\r\n'.encode() + content + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  -> {detail}'))


def data(resp):
    return resp[1].get('data') if isinstance(resp[1], dict) else None


def err(resp):
    return resp[1].get('error') if isinstance(resp[1], dict) else None


def psql(db, sql):
    r = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def login(email, password):
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': password})
    if s != 200:
        sys.exit(f'login failed ({s})')
    return b['data']['token']


def jwt_sub(token):
    p = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4)))['sub']


def hold(trip, frm, to, passengers, key=None, user=None):
    return call('POST', '/internal/bus-holds', {
        'tripId': trip, 'fromSeq': frm, 'toSeq': to, 'passengers': passengers,
        'principalUserId': user or str(uuid.uuid4()), 'idempotencyKey': key or 'bt-' + uuid.uuid4().hex,
        'orderCorrelationId': 'bus-test'}, base=BUS, headers={'X-Internal-Token': INTERNAL})


def pax(seat, gender='MALE'):
    return {'seatNo': seat, 'name': 'Test Yolcu', 'gender': gender}


def cleanup_orders():
    if not order_ids:
        return
    ids = ','.join("'" + o + "'" for o in order_ids)
    psql('payment_db', 'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete')
    psql('payment_db', f'DELETE FROM ledger_entries WHERE order_id IN ({ids})')
    psql('payment_db', f'DELETE FROM escrow_items WHERE order_id IN ({ids})')
    psql('payment_db', f'DELETE FROM payment_intents WHERE order_id IN ({ids})')
    psql('payment_db', 'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete')
    psql('package_booking_db', f'DELETE FROM vouchers WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id IN ({ids}))')
    psql('package_booking_db', f'DELETE FROM order_items WHERE order_id IN ({ids})')
    psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({ids})')


def cleanup():
    cleanup_orders()
    if created_orgs:
        ids = ','.join("'" + o + "'" for o in created_orgs)
        for k in psql('bus_db', f'SELECT storage_key FROM documents WHERE organization_id IN ({ids})').split():
            if all(c in '0123456789abcdef-' for c in k):
                subprocess.run(['docker', 'exec', 'waylo-bus-service', 'rm', '-f', '/data/bus-documents/' + k], capture_output=True)
        psql('bus_db', f'DELETE FROM tickets WHERE trip_id IN (SELECT id FROM trips WHERE organization_id IN ({ids}))')
        psql('bus_db', f'DELETE FROM bookings WHERE organization_id IN ({ids})')
        for t in ('trip_fares', 'trip_stops'):
            psql('bus_db', f'DELETE FROM {t} WHERE trip_id IN (SELECT id FROM trips WHERE organization_id IN ({ids}))')
        for t in ('trips', 'documents', 'buses'):
            psql('bus_db', f'DELETE FROM {t} WHERE organization_id IN ({ids})')
        psql('auth_db', f'DELETE FROM role_assignments WHERE scope_id IN ({ids})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({ids})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({ids})')
    for c in cities:
        psql('bus_db', f"DELETE FROM terminals WHERE city = '{c}'")
    if emails:
        q = ','.join("'" + e + "'" for e in emails)
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def main():
    admin_email, admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    email = f'bus-test-{uuid.uuid4().hex[:10]}@example.test'
    pw = 'Bt1' + secrets.token_urlsafe(16)
    emails.append(email)
    s, _ = call('POST', '/api/auth/register', {'firstName': 'Otobus', 'lastName': 'Test', 'email': email, 'password': pw})
    if s not in (200, 201):
        sys.exit(f'register failed ({s})')
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    time.sleep(13)
    partner = login(email, pw)
    admin = login(admin_email, admin_pw)
    partner_id = jwt_sub(partner)

    # org rules
    s, b = call('POST', '/api/organizations', {'name': 'Bus Ind', 'businessType': 'BUS', 'legalType': 'INDIVIDUAL'}, partner)
    check('INDIVIDUAL bus org rejected (422)', s == 422, s)
    s, b = call('POST', '/api/organizations', {'name': 'Bus Test ' + uuid.uuid4().hex[:5], 'businessType': 'BUS', 'legalType': 'COMPANY'}, partner)
    check('BUS COMPANY org created', s in (200, 201) and b['data']['businessType'] == 'BUS', (s, b))
    oid = b['data']['id']
    created_orgs.append(oid)
    check('admin approves org', call('POST', f'/api/admin/organizations/{oid}/approve', token=admin)[0] == 200)
    P = f'/api/bus-partner/organizations/{oid}'

    s, _ = call('GET', P + '/buses', token=admin)
    check('admin without BUS_MANAGE denied (403)', s == 403, s)
    check('unauthenticated partner call is 401', call('GET', P + '/buses')[0] == 401)
    st = data(call('GET', P + '/status', token=partner))
    check('status: not sellable, 2 org docs missing, salesEnabled', st and st['sellable'] is False
          and len(st['missingOrgDocuments']) == 2 and st['salesEnabled'] is True, st)

    # terminals (admin) and bus
    tag = uuid.uuid4().hex[:6]
    ca, cb, cc = 'TA' + tag, 'TB' + tag, 'TC' + tag
    cities.extend([ca, cb, cc])
    term = {}
    for c in (ca, cb, cc):
        s, b = call('POST', '/api/admin/bus-terminals', {'city': c, 'name': c + ' Otogar'}, admin)
        term[c] = b['data']['id'] if s == 201 else None
    check('admin creates 3 terminals', all(term.values()), term)
    check('partner cannot create terminals via admin API', call('POST', '/api/admin/bus-terminals', {'city': 'x', 'name': 'y'}, partner)[0] == 403)
    s, b = call('POST', P + '/buses', {'plateNumber': '34 tst ' + str(secrets.randbelow(900) + 100), 'brand': 'Mercedes',
                                       'model': 'Tourismo', 'year': 2023, 'layout': 'TWO_PLUS_TWO', 'rowCount': 10}, partner)
    check('bus created: 40 seats, not sellable, 4 docs missing', s == 201 and b['data']['seatCount'] == 40
          and b['data']['sellable'] is False and len(b['data']['missingDocuments']) == 4, b)
    bus = b['data']['id']

    day = datetime.date.today() + datetime.timedelta(days=10)
    t = lambda h, m=0, d=0: f'{day + datetime.timedelta(days=d)}T{h:02d}:{m:02d}:00'
    trip_req = {'busId': bus, 'code': 'T' + tag, 'currency': 'TRY',
                'stops': [{'terminalId': term[ca], 'arrivalAt': t(22), 'departureAt': t(22)},
                          {'terminalId': term[cb], 'arrivalAt': t(1, 0, 1), 'departureAt': t(1, 15, 1)},
                          {'terminalId': term[cc], 'arrivalAt': t(5, 0, 1), 'departureAt': t(5, 0, 1)}],
                'fares': [{'fromSeq': 0, 'toSeq': 2, 'price': 500}, {'fromSeq': 0, 'toSeq': 1, 'price': 300},
                          {'fromSeq': 1, 'toSeq': 2, 'price': 250}]}
    bad = dict(trip_req, fares=[{'fromSeq': 0, 'toSeq': 1, 'price': 300}])
    check('trip without full-route fare rejected (INVALID_FARES)', err(call('POST', P + '/trips', bad, partner)) == 'INVALID_FARES')
    s, b = call('POST', P + '/trips', trip_req, partner)
    check('trip created', s == 201 and len(b['data']['stops']) == 3, (s, b))
    trip = b['data']['id']

    q = f'/api/bus-trips?fromCity={ca}&toCity={cc}&date={day}'
    check('no offers before documents (public, anonymous)', data(call('GET', q)) == [])

    far = str(datetime.date.today() + datetime.timedelta(days=400))
    past = str(datetime.date.today() - datetime.timedelta(days=1))

    def upload(dtype, number, valid, bus_id=None, content=PDF, name='belge.pdf'):
        body, ctype = multipart({'type': dtype, 'documentNumber': number, 'validUntil': valid, 'busId': bus_id}, name, content)
        return call('POST', P + '/documents', token=partner, raw=body, headers={'Content-Type': ctype})

    check('wrong magic bytes rejected (415)', upload('TAX_CERTIFICATE', 'V1', None, content=b'<html>x</html>')[0] == 415)
    docs = []
    for ty, n, v, bid in [('TRANSPORT_AUTHORIZATION', 'D1-2026-1', far, None), ('TAX_CERTIFICATE', 'VL-1', None, None),
                          ('VEHICLE_REGISTRATION', 'RU-1', far, bus), ('VEHICLE_CARD', 'TK-1', far, bus),
                          ('TRAFFIC_INSURANCE', 'TR-1', far, bus), ('SEAT_ACCIDENT_INSURANCE', 'KF-1', far, bus)]:
        s, b = upload(ty, n, v, bid)
        docs.append(b['data']['id'] if s == 201 else None)
    check('6 documents uploaded PENDING', all(docs), docs)
    check('still no offers while PENDING', data(call('GET', q)) == [])
    check('admin lists pending bus documents', len([d for d in data(call('GET', '/api/admin/bus-documents?status=PENDING', token=admin)) or []
                                                    if d['document']['organizationId'] == oid]) == 6)
    check('admin file download works', call('GET', f'/api/admin/bus-documents/{docs[0]}/file', token=admin)[0] == 200)
    for d in docs[:-1]:
        call('POST', f'/api/admin/bus-documents/{d}/approve', token=admin)
    check('no offers while one bus document is PENDING', data(call('GET', q)) == [])
    check('admin approves last document', call('POST', f'/api/admin/bus-documents/{docs[-1]}/approve', token=admin)[0] == 200)

    offers = data(call('GET', q)) or []
    check('offer appears: 40 seats, price 500, companyName', len(offers) == 1 and offers[0]['availableSeats'] == 40
          and float(offers[0]['price']) == 500 and offers[0]['fromSeq'] == 0 and offers[0]['toSeq'] == 2, offers)
    mid = data(call('GET', f'/api/bus-trips?fromCity={ca}&toCity={cb}&date={day}')) or []
    check('intermediate pair offered at its own fare', len(mid) == 1 and float(mid[0]['price']) == 300, mid)
    check('reverse direction not offered', data(call('GET', f'/api/bus-trips?fromCity={cc}&toCity={ca}&date={day}')) == [])
    sm = data(call('GET', f'/api/bus-trips/{trip}/seats?fromSeq=0&toSeq=2')) or {}
    check('seat map has 40 free seats', len(sm.get('seats', [])) == 40 and all(x['available'] for x in sm['seats']), sm)

    # parallel holds on one seat through the real service + Postgres
    def attempt(i):
        return hold(trip, 0, 2, [pax(7)], key=f'par-{tag}-{i}')
    with concurrent.futures.ThreadPoolExecutor(PARALLEL) as ex:
        res = list(ex.map(attempt, range(PARALLEL)))
    ok = [r for r in res if r[0] == 200]
    taken = [r for r in res if r[0] == 409 and err(r) == 'SEAT_TAKEN']
    check(f'{PARALLEL} parallel holds on seat 7: exactly 1 success', len(ok) == 1 and len(taken) == PARALLEL - 1,
          [r[0] for r in res])
    held = data(ok[0])
    check('hold priced from fare, seat 7, city names', float(held['totalAmount']) == 500 and held['seatNos'] == [7]
          and held['fromCity'] == ca and held['toCity'] == cc and held['status'] == 'HELD', held)
    n_tickets = psql('bus_db', f"SELECT count(*) FROM tickets WHERE trip_id = '{trip}' AND seat_no = 7")
    check('DB holds exactly one ticket for seat 7', n_tickets == '1', n_tickets)

    # idempotency (parallel, same key)
    with concurrent.futures.ThreadPoolExecutor(6) as ex:
        same = list(ex.map(lambda _: hold(trip, 0, 2, [pax(9, 'FEMALE')], key=f'same-{tag}'), range(6)))
    ids = {data(r)['holdId'] for r in same if r[0] == 200}
    check('same idempotency key (parallel) -> one hold', len(ids) == 1 and all(r[0] == 200 for r in same), [r[0] for r in same])

    # segments, gender
    r = hold(trip, 0, 1, [pax(7)])
    check('seat 7 taken on overlapping sub-segment', r[0] == 409 and err(r) == 'SEAT_TAKEN', r)
    r = hold(trip, 0, 2, [pax(10, 'MALE')])
    check('gender rule: male next to female seat 9 rejected', r[0] == 422 and err(r) == 'SEAT_GENDER_CONFLICT', r)
    r = hold(trip, 0, 2, [pax(10, 'FEMALE')])
    check('female next to female allowed', r[0] == 200, r)
    r1 = hold(trip, 0, 1, [pax(20)])
    r2 = hold(trip, 1, 2, [pax(20)])
    check('seat reusable on the next segment (0-1 then 1-2)', r1[0] == 200 and r2[0] == 200, (r1[0], r2[0]))
    sm = data(call('GET', f'/api/bus-trips/{trip}/seats?fromSeq=1&toSeq=2')) or {}
    check('seat map reflects holds', next(x for x in sm['seats'] if x['seatNo'] == 7)['available'] is False
          and next(x for x in sm['seats'] if x['seatNo'] == 1)['available'] is True, '')
    check('invalid seat / bad segment -> 422', hold(trip, 0, 2, [pax(41)])[0] == 422 and hold(trip, 2, 1, [pax(1)])[0] == 422)
    check('internal API needs token', call('POST', '/internal/bus-holds', {}, base=BUS)[0] == 401)

    # confirm / release
    hid = held['holdId']
    c = call('POST', f'/internal/bus-holds/{hid}/confirm', base=BUS, headers={'X-Internal-Token': INTERNAL})
    check('confirm HELD -> CONFIRMED', c[0] == 200 and data(c)['status'] == 'CONFIRMED', c)
    bk = data(call('GET', P + '/bookings', token=partner)) or []
    check('partner sees confirmed booking with passenger', len(bk) == 1 and bk[0]['tickets'][0]['seatNo'] == 7, bk)
    a = call('POST', f'/internal/bus-holds/{hid}/release', base=BUS, headers={'X-Internal-Token': INTERNAL})
    b2 = call('POST', f'/internal/bus-holds/{hid}/release', base=BUS, headers={'X-Internal-Token': INTERNAL})
    check('release CONFIRMED -> CANCELLED, idempotent', a[0] == 200 and b2[0] == 200 and data(b2)['status'] == 'CANCELLED')
    check('seat 7 free again after release', hold(trip, 0, 2, [pax(7)])[0] == 200)
    r = hold(trip, 0, 2, [pax(33)], user=partner_id, key=f'mine-{tag}')
    mine = data(call('GET', '/api/bus-bookings/me', token=partner)) or []
    check('customer lists own booking', r[0] == 200 and any(m['id'] == data(r)['holdId'] for m in mine), mine)


    # ---- order level: order -> pay -> voucher window -> partner redeem ----
    cust_email = f'bus-cust-{uuid.uuid4().hex[:10]}@example.test'
    cust_pw = 'Bc1' + secrets.token_urlsafe(16)
    emails.append(cust_email)
    if call('POST', '/api/auth/register', {'firstName': 'Yolcu', 'lastName': 'Test', 'email': cust_email, 'password': cust_pw})[0] not in (200, 201):
        sys.exit('customer register failed')
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{cust_email}'")
    time.sleep(13)
    cust = login(cust_email, cust_pw)
    s, _ = call('POST', '/api/admin/commission-rates', {'vertical': 'BUS', 'supplierOrganizationId': oid, 'rate': 0.08}, admin)
    check('BUS commission rate created', s in (200, 201), s)

    def passengers(*seats):
        return [{'seatNo': n, 'name': 'Yolcu ' + str(n), 'gender': g} for n, g in seats]
    body = {'tripId': trip, 'fromSeq': 0, 'toSeq': 2, 'passengers': passengers((21, 'MALE'), (22, 'FEMALE'))}
    s, r = call('POST', '/api/orders/bus', body, cust)
    check('customer bus order 201', s == 201, (s, r))
    order = data((s, r)) or {}
    order_ids.append(order.get('id'))
    item = (order.get('items') or [{}])[0]
    check('order PENDING, BUS item, total 1000 from the hold', order.get('status') == 'PENDING' and item.get('vertical') == 'BUS'
          and float(order.get('totalAmount', 0)) == 1000 and float(item.get('unitAmount', 0)) == 1000, order)
    check('description has route and seats', f'{ca} - {cc}' in item.get('description', '') and 'koltuk 21, 22' in item.get('description', ''), item.get('description'))
    s, r = call('POST', '/api/orders/bus', body, admin)
    check('admin order 403 ADMIN_CANNOT_PURCHASE', s == 403 and err((s, r)) == 'ADMIN_CANNOT_PURCHASE', (s, r))
    s, r = call('POST', '/api/orders/bus', dict(body, passengers=passengers((22, 'MALE'))), partner)
    check('second buyer on a held seat gets 409 SEAT_TAKEN', s == 409 and err((s, r)) == 'SEAT_TAKEN', (s, r))
    s, r = call('POST', '/api/orders/bus', dict(body, passengers=passengers((8, 'FEMALE'))), partner)
    check('gender rule via order: female next to male seat 7 is 422', s == 422 and err((s, r)) == 'SEAT_GENDER_CONFLICT', (s, r))

    oid_order = order['id']
    s, r = call('POST', f'/api/payments/orders/{oid_order}/intent', None, cust)
    check('payment intent created', s in (200, 201), s)
    intent = data((s, r))
    check('intent amount equals order total', float(intent['amount']) == 1000, intent)
    s, r = call('POST', f"/api/payments/intents/{intent['id']}/confirm", {'paymentToken': 'TEST_OK'}, cust)
    check('payment confirmed', s == 200 and data((s, r)).get('status') == 'AUTHORIZED', (s, r))
    check('order CONFIRMED', data(call('GET', f'/api/orders/{oid_order}', token=cust)).get('status') == 'CONFIRMED')
    mine_b = data(call('GET', '/api/bus-bookings/me', token=cust)) or []
    check('booking CONFIRMED with 2 tickets', len(mine_b) == 1 and mine_b[0]['status'] == 'CONFIRMED' and len(mine_b[0]['tickets']) == 2, mine_b)
    check('seats 21/22 taken after payment', hold(trip, 0, 2, [pax(21)])[0] == 409)
    esc = psql('payment_db', f"SELECT vertical||'|'||gross_amount||'|'||commission_rate||'|'||commission_amount||'|'||supplier_amount||'|'||status FROM escrow_items WHERE order_id='{oid_order}'")
    check('escrow BUS HELD, 8% commission 80.00 / 920.00', esc.startswith('BUS|1000') and esc.endswith('|HELD') and '|80.00|920.00|' in esc, esc)

    vouchers = data(call('GET', f'/api/orders/{oid_order}/vouchers', token=cust)) or []
    check('one voucher for the bus item', len(vouchers) == 1, vouchers)
    vid = vouchers[0]['voucherId']
    boarding = datetime.datetime.combine(day, datetime.time(22, 0), tzinfo=datetime.timezone(datetime.timedelta(hours=3)))
    vf = psql('package_booking_db', f"SELECT extract(epoch from valid_from)::bigint FROM vouchers WHERE id='{vid}'")
    vu = psql('package_booking_db', f"SELECT extract(epoch from valid_until)::bigint FROM vouchers WHERE id='{vid}'")
    check('voucher window = boarding - 2h .. boarding + 1h', vf and vu and int(vf) == int(boarding.timestamp()) - 7200
          and int(vu) == int(boarding.timestamp()) + 3600, (vf, vu, int(boarding.timestamp())))
    code = (vouchers[0].get('payload') or '').split(':')[-1]
    payload = f'WAYLO1:{vid}:{code}'
    R = f'/api/partner/organizations/{oid}/vouchers/redeem'
    s, r = call('POST', R, {'payload': payload}, partner)
    check('redeem before the window rejected (NOT_YET_VALID)', s in (409, 422) and 'NOT_YET_VALID' in (err((s, r)) or ''), (s, r))
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour', valid_until = now() + interval '1 hour' WHERE id='{vid}'")
    s, r = call('POST', R, {'payload': payload}, partner)
    check('partner redeem releases the escrow', s == 200 and data((s, r)).get('amountReleased') is not None, (s, r))
    esc2 = psql('payment_db', f"SELECT status FROM escrow_items WHERE order_id='{oid_order}'")
    check('escrow RELEASED after boarding scan', esc2 == 'RELEASED', esc2)
    check('second scan rejected (409)', call('POST', R, {'payload': payload}, partner)[0] == 409)

    # cancel before payment releases the seats
    s, r = call('POST', '/api/orders/bus', dict(body, passengers=passengers((30, 'MALE'))), cust)
    o2 = data((s, r)) or {}
    order_ids.append(o2.get('id'))
    check('second order PENDING', s == 201, (s, r))
    check('seat 30 held by the pending order', hold(trip, 0, 2, [pax(30)])[0] == 409)
    s, r = call('POST', f"/api/orders/{o2['id']}/cancel", None, cust)
    check('cancel order', s == 200 and data((s, r))['status'] == 'CANCELLED', (s, r))
    check('seat 30 free again after cancel', hold(trip, 0, 2, [pax(30)])[0] == 200)

    # sellability closes when a document expires, cancel with paid tickets blocked
    psql('bus_db', f"UPDATE documents SET valid_until = '{past}' WHERE type = 'SEAT_ACCIDENT_INSURANCE' AND organization_id = '{oid}'")
    check('expired insurance closes the offer', data(call('GET', q)) == [])
    r = hold(trip, 0, 2, [pax(40)])
    check('expired insurance blocks holds (PARTNER_NOT_SELLABLE)', r[0] == 422 and err(r) == 'PARTNER_NOT_SELLABLE', r)
    check('cancel trip with paid tickets blocked or ok', call('POST', f'{P}/trips/{trip}/cancel', token=partner)[0] in (200, 422))


try:
    main()
finally:
    cleanup()
failed = [n for n, ok in results if not ok]
print(f'\n{len(results) - len(failed)}/{len(results)} passed')
sys.exit(1 if failed else 0)
