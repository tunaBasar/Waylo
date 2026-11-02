#!/usr/bin/env python3
"""Seed demo flights so /flights shows real searchable offers. Idempotent: existing airports, flights and seats are kept.

Creates airports IST SAW ESB ADB AYT if missing, then 6 flights over the next 14 days (fixed flight numbers WD101..WD106,
re-runs only add flights that are missing and shift nothing) with 20 economy + 4 business seats each.
Admin credentials come from the environment or .env (WAYLO_BOOTSTRAP_ADMIN_*), never printed.
"""
import datetime
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

BASE = os.environ.get('SEED_BASE', 'http://localhost:8080')
ROOT = pathlib.Path(__file__).resolve().parent.parent

AIRPORTS = [
    ('IST', 'Istanbul Havalimani', 'Istanbul', 'Turkiye', 'Europe/Istanbul', 41.2753, 28.7519),
    ('SAW', 'Sabiha Gokcen Havalimani', 'Istanbul', 'Turkiye', 'Europe/Istanbul', 40.8986, 29.3092),
    ('ESB', 'Esenboga Havalimani', 'Ankara', 'Turkiye', 'Europe/Istanbul', 40.1281, 32.9951),
    ('ADB', 'Adnan Menderes Havalimani', 'Izmir', 'Turkiye', 'Europe/Istanbul', 38.2924, 27.1570),
    ('AYT', 'Antalya Havalimani', 'Antalya', 'Turkiye', 'Europe/Istanbul', 36.8987, 30.8005),
]
# number, from, to, airline, day offset, hour, minute, duration min, economy price, business price
FLIGHTS = [
    ('WD101', 'IST', 'ESB', 'Waylo Air', 1, 8, 30, 70, 1250, 3400),
    ('WD102', 'ESB', 'IST', 'Waylo Air', 2, 18, 0, 70, 1300, 3500),
    ('WD103', 'SAW', 'ADB', 'Waylo Air', 4, 7, 15, 65, 1100, 3000),
    ('WD104', 'IST', 'AYT', 'Waylo Air', 6, 11, 45, 85, 1650, 4200),
    ('WD105', 'AYT', 'SAW', 'Waylo Air', 9, 15, 30, 90, 1700, 4300),
    ('WD106', 'ADB', 'IST', 'Waylo Air', 13, 20, 10, 60, 1050, 2900),
]


def env(key):
    if os.environ.get(key):
        return os.environ[key]
    f = ROOT / '.env'
    if f.exists():
        for line in f.read_text().splitlines():
            if line.startswith(key + '='):
                return line.split('=', 1)[1].strip().strip('"').strip("'")
    return ''


def call(method, path, body=None, token=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, method=method, headers=h), timeout=30) as r:
            b = r.read()
            return r.status, json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except ValueError:
            return e.code, {}


def main():
    email = env('SEED_ADMIN_EMAIL') or env('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    pw = env('SEED_ADMIN_PASSWORD') or env('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not (email and pw):
        sys.exit('HATA: admin bilgisi gerekli (WAYLO_BOOTSTRAP_ADMIN_EMAIL/PASSWORD)')
    s, b = call('POST', '/api/auth/login', {'email': email, 'password': pw})
    if s != 200:
        sys.exit(f'HATA: admin girisi basarisiz ({s})')
    admin = b['data']['token']

    have = {a['iataCode'] for a in (call('GET', '/api/airports')[1].get('data') or [])}
    new_airports = 0
    for code, name, city, country, tz, lat, lon in AIRPORTS:
        if code in have:
            continue
        s, b = call('POST', '/api/airports', {'iataCode': code, 'name': name, 'city': city, 'country': country,
                                              'timezone': tz, 'latitude': lat, 'longitude': lon}, admin)
        if s != 201:
            sys.exit(f'HATA: havalimani {code} olusturulamadi ({s})')
        new_airports += 1

    new_flights = new_seats = 0
    today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=3))).replace(second=0, microsecond=0)
    for no, frm, to, airline, off, hh, mm, dur, eco, biz in FLIGHTS:
        s, b = call('GET', f'/api/flights/number/{no}')
        if s != 200:
            dep = (today + datetime.timedelta(days=off)).replace(hour=hh, minute=mm).astimezone(datetime.timezone.utc)
            arr = dep + datetime.timedelta(minutes=dur)
            s, b = call('POST', '/api/flights', {'flightNumber': no, 'departureAirportCode': frm, 'arrivalAirportCode': to,
                                                 'airline': airline, 'departureTime': dep.strftime('%Y-%m-%dT%H:%M:%SZ'),
                                                 'arrivalTime': arr.strftime('%Y-%m-%dT%H:%M:%SZ')}, admin)
            if s != 201:
                sys.exit(f'HATA: ucus {no} olusturulamadi ({s})')
            new_flights += 1
        existing = call('GET', f'/api/seat-inventory/seats/{no}')[1].get('data') or []
        if existing:
            continue
        for cabin, price, rows in (('BUSINESS', biz, (1, 2)), ('ECONOMY', eco, (3, 7))):
            letters = 'AC' if cabin == 'BUSINESS' else 'ABCD'
            s, b = call('POST', '/api/seat-inventory/seats/bulk', {'flightNumber': no, 'seatType': cabin, 'price': price,
                                                                   'fromRow': rows[0], 'toRow': rows[1], 'letters': letters}, admin)
            if s != 201:
                sys.exit(f'HATA: koltuklar {no} {cabin} olusturulamadi ({s})')
            new_seats += b['data']['created'] if isinstance(b.get('data'), dict) and 'created' in b['data'] else 0
    print(f'flight demo: +{new_airports} havalimani, +{new_flights} ucus, +{new_seats or "(mevcut)"} koltuk, toplam {len(FLIGHTS)} demo ucus')


if __name__ == '__main__':
    main()
