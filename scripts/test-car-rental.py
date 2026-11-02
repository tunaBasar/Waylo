#!/usr/bin/env python3
"""Live test of the car rental v2 flow through the gateway. Self-cleaning.

Registers one partner user (paced for the gateway limits), creates CAR_RENTAL orgs, fleet, rates,
documents, then checks offers, document review effects, the individual-partner gate and the internal
hold API (parallel holds, confirm, release, idempotency). Credentials and tokens are never printed.

  python3 scripts/test-car-rental.py
"""
import concurrent.futures
import datetime
import json
import os
import pathlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:8080'
CAR = 'http://localhost:8084'
ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'

results = []
created_orgs = []
emails = []


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
            return r.status, (json.loads(b) if b and r.headers.get('Content-Type', '').startswith('application/json') else b), r.headers
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b), e.headers
        except ValueError:
            return e.code, b, e.headers


def multipart(fields, filename, content):
    boundary = '----t' + uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        if v is not None:
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                 f'Content-Type: application/octet-stream\r\n\r\n'.encode() + content + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  -> {detail}'))


def data(resp):
    return (resp[1] or {}).get('data') if isinstance(resp[1], dict) else None


def err(resp):
    return resp[1].get('error') if isinstance(resp[1], dict) else None


def psql(db, sql):
    r = subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def login(email, password):
    s, b, _ = call('POST', '/api/auth/login', {'email': email, 'password': password})
    if s != 200:
        sys.exit(f'login failed ({s})')
    return b['data']['token']


def cleanup():
    if not created_orgs:
        pass
    ids = ','.join("'" + o + "'" for o in created_orgs)
    if created_orgs:
        keys = psql('car_rental_db', f'SELECT storage_key FROM documents WHERE organization_id IN ({ids})').split()
        for k in keys:
            if all(c in '0123456789abcdef-' for c in k):
                subprocess.run(['docker', 'exec', 'waylo-car-rental-service', 'rm', '-f', '/data/car-documents/' + k],
                               capture_output=True)
        for t in ('rentals', 'documents', 'one_way_routes', 'category_rates', 'vehicles', 'branches'):
            psql('car_rental_db', f'DELETE FROM {t} WHERE organization_id IN ({ids})')
        psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({ids})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({ids})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({ids})')
    if emails:
        q = ','.join("'" + e + "'" for e in emails)
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def main():
    admin_email, admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    email = f'car-test-{uuid.uuid4().hex[:10]}@example.test'
    pw = 'Ct1' + secrets.token_urlsafe(16)
    emails.append(email)
    s, b, _ = call('POST', '/api/auth/register', {'firstName': 'Filo', 'lastName': 'Test', 'email': email, 'password': pw})
    if s not in (200, 201):
        sys.exit(f'register failed ({s})')
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    time.sleep(13)
    partner = login(email, pw)
    admin = login(admin_email, admin_pw)

    def org(name, legal):
        o = data(call('POST', '/api/organizations', {'name': name, 'businessType': 'CAR_RENTAL', 'legalType': legal}, partner))
        created_orgs.append(o['id'])
        s2 = call('POST', f"/api/admin/organizations/{o['id']}/approve", token=admin)[0]
        check(f'admin approves {legal} org', s2 == 200, s2)
        return o['id']

    day = datetime.date.today() + datetime.timedelta(days=10)
    pickup = f'{day}T10:00:00'
    ret = f'{day + datetime.timedelta(days=2)}T10:00:00'
    far = str(datetime.date.today() + datetime.timedelta(days=400))
    past = str(datetime.date.today() - datetime.timedelta(days=1))

    oid = org('Car Test ' + uuid.uuid4().hex[:5], 'COMPANY')
    P = f'/api/partner/organizations/{oid}'

    # branches
    br = {'city': 'Antalya', 'address': 'Test cad. 1', 'phone': '+905550000000', 'openTime': '08:00:00', 'closeTime': '20:00:00'}
    sa, ba, _ = call('POST', P + '/branches', {**br, 'name': 'Test Sube A'}, partner)
    sb, bb, _ = call('POST', P + '/branches', {**br, 'name': 'Test Sube B', 'city': 'Izmir'}, partner)
    check('two branches created', sa == 201 and sb == 201, (sa, sb))
    A, B = ba['data']['id'], bb['data']['id']

    # vehicle
    sv, bv, _ = call('POST', P + '/vehicles', {'homeBranchId': A, 'plateNumber': '34 tst ' + str(secrets.randbelow(900) + 100),
                                               'brand': 'Fiat', 'model': 'Egea', 'year': 2024, 'category': 'ECONOMY',
                                               'transmission': 'MANUAL', 'fuelType': 'DIESEL', 'seatCount': 5}, partner)
    check('vehicle created, not sellable, 3 docs missing', sv == 201 and bv['data']['sellable'] is False
          and len(bv['data']['missingDocuments']) == 3, bv)
    V = bv['data']['id']

    # rates and route
    rate = {'branchId': A, 'category': 'ECONOMY', 'transmission': 'MANUAL', 'currency': 'TRY', 'dailyRate': 1000,
            'depositAmount': 5000, 'kmLimitPerDay': 300, 'extraKmFee': 5, 'minDriverAge': 21, 'minLicenseYears': 2}
    s, b, _ = call('PUT', P + '/rates', {'items': [rate]}, partner)
    check('rates upserted', s == 200 and len(b['data']) == 1, (s, b))
    s, b, _ = call('PUT', P + '/one-way-routes', {'routes': [{'fromBranchId': A, 'toBranchId': B, 'fee': 400, 'currency': 'TRY'}]}, partner)
    check('one-way route saved', s == 200 and len(b['data']) == 1, (s, b))
    s, b, _ = call('PUT', P + '/one-way-routes', {'routes': [{'fromBranchId': A, 'toBranchId': B, 'fee': 400, 'currency': 'USD'}]}, partner)
    check('route currency mismatch rejected (4xx)', 400 <= s < 500, s)
    call('PUT', P + '/one-way-routes', {'routes': [{'fromBranchId': A, 'toBranchId': B, 'fee': 400, 'currency': 'TRY'}]}, partner)

    offers_q = f'/api/car-offers?pickupBranchId={A}&returnBranchId={A}&pickupAt={pickup}&returnAt={ret}&driverAge=30'
    check('no offers before documents', data(call('GET', offers_q)) == [])

    # access denial
    s, _, _ = call('GET', P + '/branches', token=admin)
    check('admin without FLEET_MANAGE denied on partner endpoint', s == 403, s)
    s, _, _ = call('GET', P + '/branches')
    check('unauthenticated partner call is 401', s == 401, s)

    # documents
    def upload(org_path, dtype, number, valid, vehicle=None, content=PDF, name='belge.pdf'):
        body, ctype = multipart({'type': dtype, 'documentNumber': number, 'validUntil': valid, 'vehicleId': vehicle}, name, content)
        r = call('POST', org_path + '/documents', token=partner, raw=body, headers={'Content-Type': ctype})
        return r

    s, b, _ = upload(P, 'TAX_CERTIFICATE', 'VL-1', None, content=b'<html>not a pdf</html>', name='x.pdf')
    check('upload with wrong magic bytes rejected (415)', s == 415, s)
    s, b, _ = upload(P, 'TAX_CERTIFICATE', 'VL-1', None, content=b'%PDF' + b'0' * (10 * 1024 * 1024 + 10))
    check('upload over 10 MB rejected (413)', s == 413, s)

    docs = {}
    for t, n, v, veh in [('RENTAL_AUTHORIZATION', 'YB-2026-1', far, None), ('TAX_CERTIFICATE', 'VL-1', None, None),
                         ('VEHICLE_REGISTRATION', 'RU-1', far, V), ('RENTAL_INSURANCE', 'KS-1', far, V),
                         ('TRAFFIC_INSURANCE', 'TR-1', far, V)]:
        s, b, _ = upload(P, t, n, v, veh)
        check(f'upload {t}', s == 201 and b['data']['status'] == 'PENDING', (s, b))
        docs[t] = b['data']['id']

    check('offers hidden while documents are pending', data(call('GET', offers_q)) == [])
    s, b, h = call('GET', f"/api/admin/car-documents?status=PENDING", token=admin)
    check('admin lists pending documents', s == 200 and any(d['id'] == docs['TAX_CERTIFICATE'] for d in b['data']), s)
    s, b, h = call('GET', f"/api/admin/car-documents/{docs['RENTAL_AUTHORIZATION']}/file", token=admin)
    check('admin file download is an attachment', s == 200 and 'attachment' in (h.get('Content-Disposition') or '') and b == PDF, s)
    s, b, h = call('GET', f"{P}/documents/{docs['RENTAL_AUTHORIZATION']}/file", token=partner)
    check('partner file download is an attachment', s == 200 and 'attachment' in (h.get('Content-Disposition') or ''), s)
    s, _, _ = call('POST', f"/api/admin/car-documents/{docs['TAX_CERTIFICATE']}/approve", token=partner)
    check('non-admin cannot approve a document', s == 403, s)

    # reject one, expect hidden; replace and approve
    for t in ('RENTAL_AUTHORIZATION', 'VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE'):
        call('POST', f'/api/admin/car-documents/{docs[t]}/approve', token=admin)
    s, b, _ = call('POST', f"/api/admin/car-documents/{docs['TAX_CERTIFICATE']}/reject", {'reason': 'Okunmuyor'}, admin)
    check('admin rejects a document', s == 200 and b['data']['status'] == 'REJECTED', (s, b))
    check('offers hidden when a required document is rejected', data(call('GET', offers_q)) == [])
    s, b, _ = upload(P, 'TAX_CERTIFICATE', 'VL-2', None)
    call('POST', f"/api/admin/car-documents/{b['data']['id']}/approve", token=admin)

    st = data(call('GET', P + '/status', token=partner))
    check('partner status sellable', st and st['sellable'] is True and st['missingOrgDocuments'] == [], st)
    offers = data(call('GET', offers_q))
    o = offers[0] if offers else {}
    check('public offer appears with contract pricing', len(offers) == 1 and o['chargeableDays'] == 2
          and float(o['totalAmount']) == 2000.0 and o['exampleModel'] == 'Fiat Egea veya benzeri'
          and o['authorizationDocumentNumber'] == 'YB-2026-1', offers)
    one_way = data(call('GET', offers_q.replace(f'returnBranchId={A}', f'returnBranchId={B}')))
    check('one-way offer adds the route fee', one_way and float(one_way[0]['totalAmount']) == 2400.0, one_way)
    young = data(call('GET', offers_q.replace('driverAge=30', 'driverAge=19')))
    check('driver below min age gets no offer', young == [], young)
    bs = data(call('GET', '/api/car-branches?city=Antalya'))
    check('sellable branch is listed publicly', any(x['id'] == A for x in bs), bs)
    s, b, _ = call('GET', offers_q.replace('T10:00:00', 'T10:15:00', 1))
    check('invalid minutes rejected with 422, not 500', s == 422 and b['error'] == 'INVALID_TIME', (s, b))

    # expired document closes the listing
    s, b, _ = upload(P, 'RENTAL_AUTHORIZATION', 'YB-OLD', past)
    call('POST', f"/api/admin/car-documents/{b['data']['id']}/approve", token=admin)
    check('offers hidden when the newest authorization is expired', data(call('GET', offers_q)) == [])
    s, b, _ = upload(P, 'RENTAL_AUTHORIZATION', 'YB-NEW', far)
    call('POST', f"/api/admin/car-documents/{b['data']['id']}/approve", token=admin)
    check('offers return after renewal', len(data(call('GET', offers_q)) or []) == 1)

    # internal holds
    IH = {'X-Internal-Token': INTERNAL}
    def hold(key, customer=None, a=A, r=A):
        return call('POST', '/internal/car-holds', {
            'pickupBranchId': a, 'returnBranchId': r, 'category': 'ECONOMY', 'transmission': 'MANUAL',
            'pickupAt': pickup, 'returnAt': ret, 'driverAge': 30, 'principalUserId': customer or str(uuid.uuid4()),
            'idempotencyKey': key, 'orderCorrelationId': str(uuid.uuid4())}, base=CAR, headers=IH)

    s, _, _ = call('POST', '/internal/car-holds', {}, base=CAR)
    check('internal endpoint without token is 401', s == 401, s)
    keys = [f'live-{uuid.uuid4().hex}' for _ in range(6)]
    with concurrent.futures.ThreadPoolExecutor(6) as ex:
        rs = list(ex.map(hold, keys))
    ok = [r for r in rs if r[0] == 200]
    codes = sorted(r[0] for r in rs)
    check('6 parallel holds for 1 unit: exactly 1 success, 5 conflicts', len(ok) == 1 and codes.count(409) == 5
          and all(err(r) == 'NO_VEHICLE_AVAILABLE' for r in rs if r[0] == 409), codes)
    winner_key = keys[[r[0] for r in rs].index(200)]
    hid = ok[0][1]['data']['holdId']
    again = hold(winner_key)
    check('same idempotency key returns the same hold', again[0] == 200 and again[1]['data']['holdId'] == hid, again[0])
    check('offers hidden while the unit is held', data(call('GET', offers_q)) == [])
    s, b, _ = call('POST', f'/internal/car-holds/{hid}/confirm', base=CAR, headers=IH)
    check('confirm HELD hold', s == 200 and b['data']['status'] == 'CONFIRMED', (s, b))
    check('another hold still conflicts after confirm', hold('live-' + uuid.uuid4().hex)[0] == 409)
    pr = data(call('GET', P + '/rentals', token=partner))
    check('partner sees the confirmed rental', any(x['id'] == hid for x in pr or []), pr)
    s, b, _ = call('POST', f'/internal/car-holds/{hid}/release', base=CAR, headers=IH)
    s2, b2, _ = call('POST', f'/internal/car-holds/{hid}/release', base=CAR, headers=IH)
    check('release is idempotent', s == 200 and s2 == 200 and b2['data']['status'] == 'CANCELLED', (s, s2))
    check('unit is free again after release', hold('live-' + uuid.uuid4().hex)[0] == 200)
    s, b, _ = call('POST', f'/internal/car-holds/{uuid.uuid4()}/confirm', base=CAR, headers=IH)
    check('confirm unknown hold is 404', s == 404, s)

    # individual partner gate
    ind = org('Sahis Test ' + uuid.uuid4().hex[:5], 'INDIVIDUAL')
    PI = f'/api/partner/organizations/{ind}'
    ia = data(call('POST', PI + '/branches', {**br, 'name': 'Sahis Sube'}, partner))['id']
    iv = data(call('POST', PI + '/vehicles', {'homeBranchId': ia, 'plateNumber': '35 sht ' + str(secrets.randbelow(900) + 100),
                                              'brand': 'Renault', 'model': 'Clio', 'year': 2022, 'category': 'ECONOMY',
                                              'transmission': 'MANUAL', 'fuelType': 'PETROL', 'seatCount': 5}, partner))['id']
    call('PUT', PI + '/rates', {'items': [{**rate, 'branchId': ia}]}, partner)
    for t, n, veh in [('IDENTITY_DOCUMENT', 'ID-1', None), ('VEHICLE_REGISTRATION', 'R', iv), ('RENTAL_INSURANCE', 'K', iv),
                      ('TRAFFIC_INSURANCE', 'T', iv)]:
        s, b, _ = upload(PI, t, n, far, veh)
        call('POST', f"/api/admin/car-documents/{b['data']['id']}/approve", token=admin)
    iq = f'/api/car-offers?pickupBranchId={ia}&returnBranchId={ia}&pickupAt={pickup}&returnAt={ret}&driverAge=30'
    check('INDIVIDUAL org offers hidden by the legal gate', data(call('GET', iq)) == [])
    st = data(call('GET', PI + '/status', token=partner))
    check('INDIVIDUAL status reports the gate closed', st and st['individualGateOpen'] is False, st)
    s, b, _ = hold('live-' + uuid.uuid4().hex, a=ia, r=ia)
    check('INDIVIDUAL hold is 422 INDIVIDUAL_PARTNERS_DISABLED', s == 422 and err((s, b, None)) == 'INDIVIDUAL_PARTNERS_DISABLED', (s, b))


if __name__ == '__main__':
    try:
        main()
    finally:
        cleanup()
    failed = [n for n, ok in results if not ok]
    print(f'\n{len(results) - len(failed)}/{len(results)} checks passed')
    sys.exit(1 if failed else 0)
