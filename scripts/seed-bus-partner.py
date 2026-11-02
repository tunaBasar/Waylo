#!/usr/bin/env python3
"""Seed a BUS partner flow: partner user, approved COMPANY org, terminals, two buses, approved documents
(tiny valid PDF), BUS commission rate and a few trips over the next days. Idempotent: reuses the org.

Credentials come from the environment or .env (never printed):
  SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD   (fallback WAYLO_BOOTSTRAP_ADMIN_*)
  SEED_BUS_PARTNER_EMAIL (default seed-otobus@waylo.test) / SEED_BUS_PARTNER_PASSWORD (required)
Public search stays empty until bus-service runs with BUS_SALES_ENABLED=true (legal gate).
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
ORG_NAME = 'Waylo Otobus Isletmesi'
STOPS = [('Istanbul', 'Esenler Otogari'), ('Bolu', 'Bolu Otogari'), ('Ankara', 'ASTI Otogari')]


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


def upload(P, token, dtype, number, valid, bus=None):
    boundary = '----seed' + uuid.uuid4().hex
    fields = {'type': dtype, 'documentNumber': number, 'validUntil': valid, 'busId': bus}
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
             for k, v in fields.items() if v]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="belge.pdf"\r\n'
                 f'Content-Type: application/pdf\r\n\r\n'.encode() + PDF + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    s, b = call('POST', P + '/documents', token=token, raw=b''.join(parts), ctype='multipart/form-data; boundary=' + boundary)
    if s != 201:
        sys.exit(f'  [FAIL:{s}] upload {dtype}')
    return b['data']['id']


def main():
    admin_email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    email = env('SEED_BUS_PARTNER_EMAIL') or 'seed-otobus@waylo.test'
    pw = env('SEED_BUS_PARTNER_PASSWORD')
    if not (admin_email and admin_pw and pw):
        sys.exit('HATA: SEED_BUS_PARTNER_PASSWORD (ve admin bilgisi) gerekli')

    s, _ = call('POST', '/api/auth/register', {'firstName': 'Otobus', 'lastName': 'Partner', 'email': email, 'password': pw})
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
    org = next((m.get('organizationId') for m in mine if m.get('organizationName') == ORG_NAME), None)
    if not org:
        org = must('POST', '/api/organizations', {'name': ORG_NAME, 'businessType': 'BUS', 'legalType': 'COMPANY'}, partner)['id']
        call('POST', f'/api/admin/organizations/{org}/approve', token=admin)
        print(f'  [OK] organizasyon {ORG_NAME} -> {org}')

    rates = call('GET', '/api/admin/commission-rates', token=admin)[1].get('data') or []
    if not any(r.get('vertical') == 'BUS' and str(r.get('supplierOrganizationId')) == org for r in rates):
        must('POST', '/api/admin/commission-rates', {'vertical': 'BUS', 'supplierOrganizationId': org, 'rate': 0.0800}, token=admin)
        print('  [OK] BUS komisyon orani %8')

    P = f'/api/bus-partner/organizations/{org}'
    if must('GET', P + '/buses', token=partner):
        print('  [SKIP] otobusler zaten var')
        return
    term = {}
    existing = {(t['city'], t['name']): t['id'] for t in must('GET', '/api/admin/bus-terminals', token=admin)}
    for city, name in STOPS:
        term[city] = existing.get((city, name)) or must('POST', '/api/admin/bus-terminals', {'city': city, 'name': name}, admin)['id']
    valid = str(datetime.date.today() + datetime.timedelta(days=365))
    buses = []
    for plate, layout, rows in [('34 WYL 100', 'TWO_PLUS_TWO', 12), ('34 WYL 101', 'TWO_PLUS_ONE', 14)]:
        buses.append(must('POST', P + '/buses', {'plateNumber': plate, 'brand': 'Mercedes', 'model': 'Tourismo', 'year': 2023,
                                                 'layout': layout, 'rowCount': rows}, partner)['id'])
    docs = [upload(P, partner, 'TRANSPORT_AUTHORIZATION', 'D1-DEMO-1', valid), upload(P, partner, 'TAX_CERTIFICATE', 'VERGI-DEMO-1', None)]
    for b in buses:
        for t in ('VEHICLE_REGISTRATION', 'VEHICLE_CARD', 'TRAFFIC_INSURANCE', 'SEAT_ACCIDENT_INSURANCE'):
            docs.append(upload(P, partner, t, t[:3] + '-' + b[:6], valid, b))
    for d in docs:
        call('POST', f'/api/admin/bus-documents/{d}/approve', token=admin)
    today = datetime.date.today()
    n = 0
    for day in range(2, 6):
        d = today + datetime.timedelta(days=day)
        at = lambda h, m=0, plus=0: f'{d + datetime.timedelta(days=plus)}T{h:02d}:{m:02d}:00'
        for bus, code, h in [(buses[0], 'WY1', 22), (buses[1], 'WY2', 9)]:
            must('POST', P + '/trips', {
                'busId': bus, 'code': f'{code}-{d:%m%d}', 'currency': 'TRY',
                'stops': [{'terminalId': term['Istanbul'], 'arrivalAt': at(h), 'departureAt': at(h)},
                          {'terminalId': term['Bolu'], 'arrivalAt': at((h + 3) % 24, 0, (h + 3) // 24), 'departureAt': at((h + 3) % 24, 20, (h + 3) // 24)},
                          {'terminalId': term['Ankara'], 'arrivalAt': at((h + 6) % 24, 30, (h + 6) // 24), 'departureAt': at((h + 6) % 24, 30, (h + 6) // 24)}],
                'fares': [{'fromSeq': 0, 'toSeq': 2, 'price': 750}, {'fromSeq': 0, 'toSeq': 1, 'price': 400}, {'fromSeq': 1, 'toSeq': 2, 'price': 450}]}, partner)
            n += 1
    print(f'  [OK] {len(STOPS)} durak, {len(buses)} otobus, {len(docs)} belge onaylandi, {n} sefer')


main()
