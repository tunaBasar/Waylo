#!/usr/bin/env python3
"""Live quote/hold acceptance checks against Waylo, including real PostgreSQL races.

Requires WAYLO_ADMIN_TOKEN (an existing platform admin's access token) and
WAYLO_INTERNAL_TOKEN. Tokens are read from environment and never printed.
Creates only dedicated test fixtures and deactivates its own hotel on exit.
This is not a payment-provider or order-to-hold integration test.
"""
import argparse
import json
import os
import secrets
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from decimal import Decimal
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gateway', default='http://localhost:8080')
parser.add_argument('--hotel', default='http://localhost:8083')
args = parser.parse_args()
admin = os.environ.get('WAYLO_ADMIN_TOKEN')
internal = os.environ.get('WAYLO_INTERNAL_TOKEN')
if not admin or not internal:
    raise SystemExit('WAYLO_ADMIN_TOKEN and WAYLO_INTERNAL_TOKEN must be set')
passed = 0


def check(condition, label):
    global passed
    if not condition:
        raise AssertionError(label)
    passed += 1
    print(f'PASS: {label}')


def request(method, path, body=None, token=None, internal_token=None, direct=False):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    if internal_token:
        headers['X-Internal-Token'] = internal_token
    data = json.dumps(body).encode() if body is not None else None
    req = Request((args.hotel if direct else args.gateway) + path, data=data,
                  headers=headers, method=method)
    try:
        with urlopen(req, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw, parse_float=Decimal) if raw else None
    except HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw, parse_float=Decimal) if raw else None


def register():
    status, result = request('POST', '/api/auth/register', {
        'firstName': 'QuoteHold', 'lastName': 'Acceptance',
        'email': f'qh{uuid.uuid4().hex}@example.test', 'password': secrets.token_urlsafe(20),
    })
    check(status == 200 and result['success'], 'register dedicated test user')
    return result['data']['userId'], result['data']['token']


def data(method, path, body=None, token=None, direct=False, internal_token=None):
    status, result = request(method, path, body, token, internal_token, direct)
    check(status in (200, 201) and result.get('success'), f'{method} {path.split("?")[0]} succeeds')
    return result['data']


owner_id, owner = register()
foreign_id, foreign = register()
hotel_id = None
try:
    organization = data('POST', '/api/organizations', {'name': f'QH acceptance {uuid.uuid4().hex[:10]}'}, owner)
    org_id = organization['id']
    hotel = data('POST', '/api/hotels', {
        'name': 'Quote hold acceptance fixture', 'city': f'QH{uuid.uuid4().hex[:10]}',
        'country': 'TR', 'address': 'Test only', 'starRating': 4, 'organizationId': org_id,
    }, owner)
    hotel_id = hotel['id']
    room = data('POST', f'/api/hotels/{hotel_id}/room-types', {
        'code': 'QH_DBL', 'name': 'Test double', 'maxAdults': 2, 'maxChildren': 0, 'maxOccupancy': 2,
    }, owner)
    plan = data('POST', f'/api/hotels/{hotel_id}/rate-plans', {
        'code': 'QH_NR', 'name': 'Test nonrefundable', 'mealPlan': 'ROOM_ONLY',
        'cancellationPolicy': 'NON_REFUNDABLE',
    }, owner)
    check_in = date.today() + timedelta(days=5)
    nights = [(check_in + timedelta(days=i)).isoformat() for i in range(2)]
    check_out = (check_in + timedelta(days=2)).isoformat()
    query = urlencode({'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2})
    availability_path = f'/api/hotels/{hotel_id}/availability?{query}'
    check(request('GET', availability_path)[0] in (404, 422), 'pending organization is not bookable')
    data('POST', f'/api/admin/organizations/{org_id}/approve', token=admin)
    inventory_path = f'/api/hotels/{hotel_id}/inventory'
    rates_path = f'/api/hotels/{hotel_id}/rates'
    data('PUT', inventory_path, {'items': [
        {'roomTypeId': room['id'], 'stayDate': night, 'allotment': 1, 'expectedVersion': None}
        for night in nights
    ], 'reason': 'quote hold acceptance fixture'}, owner)
    data('PUT', rates_path, {'items': [
        {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': night,
         'occupancy': 2, 'currency': 'EUR', 'amount': amount, 'expectedVersion': None}
        for night, amount in zip(nights, [100.25, 110.50])
    ]}, owner)
    offers = data('GET', availability_path)['offers']
    check(len(offers) == 1 and offers[0]['totalAmount'] == Decimal('210.75'), 'whole-stay offer total uses both nightly rates')
    check(len(offers[0]['nightly']) == 2, 'checkout date is excluded from nightly prices')
    too_long = urlencode({'checkIn': nights[0], 'checkOut': (check_in + timedelta(days=3)).isoformat(), 'adults': 2})
    check(data('GET', f'/api/hotels/{hotel_id}/availability?{too_long}')['offers'] == [], 'missing night excludes entire offer')
    search = data('GET', '/api/hotels/search?' + query + '&' + urlencode({'city': hotel['city']}))
    check([h['id'] for h in search] == [hotel_id], 'dated public search returns sellable test hotel')
    quote_body = {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                  'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                  'childrenAges': [], 'totalAmount': 0.01, 'currency': 'USD'}

    def quote():
        return data('POST', '/api/quotes', quote_body, owner)

    q1, q2 = quote(), quote()
    check(q1['totalAmount'] == Decimal('210.75') and q1['currency'] == 'EUR', 'client price and currency tampering ignored')
    check(request('GET', f'/api/quotes/{q1["quoteId"]}', token=foreign)[0] == 404, 'foreign principal cannot read quote')
    check(request('GET', f'/api/quotes/{q1["quoteId"]}')[0] == 401, 'quote read requires authentication')
    current_rates = data('GET', rates_path + '?' + urlencode({'from': nights[0], 'to': nights[-1], 'roomTypeId': room['id']}), token=owner)
    data('PUT', rates_path, {'items': [
        {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': rate['stayDate'],
         'occupancy': 2, 'currency': 'EUR', 'amount': 200, 'expectedVersion': rate['version']}
        for rate in current_rates
    ]}, owner)
    frozen = data('GET', f'/api/quotes/{q1["quoteId"]}', token=owner)
    check(frozen['totalAmount'] == Decimal('210.75'), 'quote total remains frozen after rates change')
    check([n['amount'] for n in frozen['nightly']] == [Decimal('100.25'), Decimal('110.50')], 'quote nightly snapshot remains frozen')
    hold_path = '/internal/hotel-holds'
    check(request('POST', hold_path, {}, direct=True)[0] == 401, 'direct internal hold rejects absent token')
    check(request('POST', hold_path, {}, internal_token='invalid', direct=True)[0] == 401, 'direct internal hold rejects invalid token')

    def hold_payload(q, key=None, principal=owner_id):
        return {'quoteId': q['quoteId'], 'idempotencyKey': key or uuid.uuid4().hex,
                'principalUserId': principal}

    check(request('POST', hold_path, hold_payload(q1, principal=foreign_id),
                  internal_token=internal, direct=True)[0] in (403, 404, 422), 'hold principal must match quote owner')
    barrier = threading.Barrier(2)
    payloads = [hold_payload(q) for q in (q1, q2)]

    def compete(payload):
        barrier.wait(timeout=10)
        return request('POST', hold_path, payload, internal_token=internal, direct=True)

    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(compete, payloads))
    winners = [(i, result['data']) for i, (status, result) in enumerate(outcomes) if status in (200, 201)]
    check(len(winners) == 1, 'real simultaneous holds for final room have exactly one winner')
    check(sorted(status for status, _ in outcomes)[-1] in (409, 422), 'losing hold is rejected without server error')
    index, winner = winners[0]

    def inventory():
        return data('GET', inventory_path + '?' + urlencode({'from': nights[0], 'to': nights[-1], 'roomTypeId': room['id']}), token=owner)

    rows = inventory()
    check(len(rows) == 2 and all(r['held'] == 1 and r['confirmed'] == 0 and r['available'] == 0 for r in rows), 'all nights held atomically without overselling')
    retry = data('POST', hold_path, payloads[index], direct=True, internal_token=internal)
    check(retry['holdId'] == winner['holdId'], 'idempotent hold retry returns original hold')
    check(all(r['held'] == 1 for r in inventory()), 'idempotent retry does not increment counters')
    confirm_path = f'{hold_path}/{winner["holdId"]}/confirm'
    confirmed = data('POST', confirm_path, direct=True, internal_token=internal)
    check(confirmed['status'] == 'CONFIRMED', 'hold confirms')
    data('POST', confirm_path, direct=True, internal_token=internal)
    check(all(r['held'] == 0 and r['confirmed'] == 1 for r in inventory()), 'repeat confirm preserves exactly one confirmed room')
    check(data('GET', availability_path)['offers'] == [], 'confirmed final room is no longer sellable')
    check(data('GET', '/api/hotels/search?' + query + '&' + urlencode({'city': hotel['city']})) == [], 'dated search excludes sold-out hotel')
    print(f'\n{passed} live assertions passed')
finally:
    if hotel_id:
        status, _ = request('POST', f'/api/hotels/{hotel_id}/deactivate', token=owner)
        if status not in (200, 201):
            print('WARNING: test hotel cleanup failed, deactivate its dedicated fixture manually')
