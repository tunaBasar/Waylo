#!/usr/bin/env python3
"""Seed a CAR_RENTAL partner flow: partner user, approved COMPANY org, branches, vehicles, rates,
one-way routes and approved documents (tiny valid PDF). Idempotent: reuses the org when it exists.

Credentials come from the environment or .env (never printed):
  SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD   (fallback WAYLO_BOOTSTRAP_ADMIN_*)
  SEED_CAR_PARTNER_EMAIL (default seed-filo@waylo.test) / SEED_CAR_PARTNER_PASSWORD (required)
"""
import datetime
import json
import os
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('SEED_BASE', 'http://localhost:8080')
ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
ORG_NAME = 'Waylo Filo Kiralama'


def env(key):
    if os.environ.get(key):
        return os.environ[key]
    f = ROOT / '.env'
    if f.exists():
        for line in f.read_text().splitlines():
            if line.startswith(key + '='):
                return line.split('=', 1)[1].strip().strip('"').strip("'")
    return ''


def call(method, path, body=None, token=None, raw=None, ctype='application/json'):
    h = {'Content-Type': ctype}
    if token:
        h['Authorization'] = 'Bearer ' + token
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, method=method, headers=h), timeout=30) as r:
            b = r.read()
            return r.status, json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except ValueError:
            return e.code, {}


def must(method, path, body=None, token=None):
    s, b = call(method, path, body, token)
    if s not in (200, 201):
        sys.exit(f'  [FAIL:{s}] {method} {path}')
    return b.get('data')


def upload(org, token, dtype, number, valid, vehicle=None):
    boundary = '----seed' + uuid.uuid4().hex
    fields = {'type': dtype, 'documentNumber': number, 'validUntil': valid, 'vehicleId': vehicle}
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
             for k, v in fields.items() if v]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="belge.pdf"\r\n'
                 f'Content-Type: application/pdf\r\n\r\n'.encode() + PDF + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    s, b = call('POST', f'/api/partner/organizations/{org}/documents', token=token, raw=b''.join(parts),
                ctype='multipart/form-data; boundary=' + boundary)
    if s != 201:
        sys.exit(f'  [FAIL:{s}] upload {dtype}')
    return b['data']['id']


def main():
    admin_email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    email = env('SEED_CAR_PARTNER_EMAIL') or 'seed-filo@waylo.test'
    pw = env('SEED_CAR_PARTNER_PASSWORD')
    if not (admin_email and admin_pw and pw):
        sys.exit('HATA: SEED_CAR_PARTNER_PASSWORD (ve admin bilgisi) gerekli')

    s, b = call('POST', '/api/auth/register', {'firstName': 'Filo', 'lastName': 'Partner', 'email': email, 'password': pw})
    if s in (200, 201):
        subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', 'auth_db', '-tAc',
                        f"UPDATE users SET email_verified = true WHERE email = '{email}'"], check=True, capture_output=True)
        time.sleep(13)
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': pw})
    if s != 200:
        sys.exit(f'  [FAIL:{s}] partner login')
    partner = b['data']['token']
    admin = must('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})['token']

    mine = call('GET', '/api/me/memberships', token=partner)[1].get('data') or []
    org = next((m.get('organizationId') or m.get('id') for m in mine if m.get('organizationName', m.get('name')) == ORG_NAME), None)
    if not org:
        org = must('POST', '/api/organizations', {'name': ORG_NAME, 'businessType': 'CAR_RENTAL', 'legalType': 'COMPANY'}, partner)['id']
        call('POST', f'/api/admin/organizations/{org}/approve', token=admin)
        print(f'  [OK] organizasyon {ORG_NAME} -> {org}')

    # Idempotent: ensure a CAR commission rate exists for this org
    rates = call('GET', '/api/admin/commission-rates', token=admin)[1].get('data') or []
    exists = any(
        r.get('vertical') == 'CAR' and str(r.get('supplierOrganizationId')) == org
        for r in rates
    )
    if not exists:
        must('POST', '/api/admin/commission-rates',
             {'vertical': 'CAR', 'supplierOrganizationId': org, 'rate': 0.1000}, token=admin)
        print('  [OK] CAR komisyon orani %10')
    else:
        print('  [SKIP] CAR komisyon orani zaten var')

    P = f'/api/partner/organizations/{org}'

    if must('GET', P + '/branches', token=partner):
        print('  [SKIP] subeler zaten var')
        return
    sites = [('Istanbul Havalimani', 'Istanbul'), ('Sabiha Gokcen', 'Istanbul'), ('Antalya Havalimani', 'Antalya')]
    branch = {}
    for name, city in sites:
        branch[name] = must('POST', P + '/branches', {'name': name, 'city': city, 'address': name + ' Gelen Yolcu Salonu',
                                                      'phone': '+902120000000', 'openTime': '07:00:00', 'closeTime': '23:00:00'}, partner)['id']
    valid = str(datetime.date.today() + datetime.timedelta(days=365))
    fleet = [('34 WYL 10', 'Toyota', 'Corolla', 2024, 'ECONOMY', 'AUTOMATIC', 'PETROL', 5, 'Istanbul Havalimani'),
             ('34 WYL 12', 'Fiat', 'Egea', 2023, 'COMPACT', 'MANUAL', 'DIESEL', 5, 'Sabiha Gokcen'),
             ('07 WYL 20', 'Toyota', 'Camry', 2024, 'MIDSIZE', 'AUTOMATIC', 'HYBRID', 5, 'Antalya Havalimani'),
             ('34 WYL 70', 'Ford', 'Tourneo', 2023, 'VAN', 'MANUAL', 'DIESEL', 9, 'Istanbul Havalimani')]
    vehicles = []
    for plate, brand, model, year, cat, trans, fuel, seats, home in fleet:
        v = must('POST', P + '/vehicles', {'homeBranchId': branch[home], 'plateNumber': plate, 'brand': brand, 'model': model,
                                           'year': year, 'category': cat, 'transmission': trans, 'fuelType': fuel,
                                           'seatCount': seats}, partner)
        vehicles.append(v['id'])
    rates = [(c, t, d) for c, t, d in [('ECONOMY', 'AUTOMATIC', 1400), ('COMPACT', 'MANUAL', 1200),
                                       ('MIDSIZE', 'AUTOMATIC', 2000), ('VAN', 'MANUAL', 2600)]]
    items = [{'branchId': b, 'category': c, 'transmission': t, 'currency': 'TRY', 'dailyRate': d, 'depositAmount': 5000,
              'kmLimitPerDay': 400, 'extraKmFee': 6, 'minDriverAge': 21, 'minLicenseYears': 2}
             for b in branch.values() for c, t, d in rates]
    must('PUT', P + '/rates', {'items': items}, partner)
    ist, saw, ayt = branch['Istanbul Havalimani'], branch['Sabiha Gokcen'], branch['Antalya Havalimani']
    must('PUT', P + '/one-way-routes', {'routes': [
        {'fromBranchId': ist, 'toBranchId': saw, 'fee': 300, 'currency': 'TRY'},
        {'fromBranchId': saw, 'toBranchId': ist, 'fee': 300, 'currency': 'TRY'},
        {'fromBranchId': ist, 'toBranchId': ayt, 'fee': 1500, 'currency': 'TRY'},
        {'fromBranchId': ayt, 'toBranchId': ist, 'fee': 1500, 'currency': 'TRY'}]}, partner)
    docs = [upload(org, partner, 'RENTAL_AUTHORIZATION', 'TURSAB-DEMO-1', valid),
            upload(org, partner, 'TAX_CERTIFICATE', 'VERGI-DEMO-1', None)]
    for v in vehicles:
        for t in ('VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE'):
            docs.append(upload(org, partner, t, t[:3] + '-' + v[:6], valid, v))
    for d in docs:
        call('POST', f'/api/admin/car-documents/{d}/approve', token=admin)
    print(f'  [OK] {len(branch)} sube, {len(vehicles)} arac, {len(items)} fiyat, {len(docs)} belge onaylandi')


main()
