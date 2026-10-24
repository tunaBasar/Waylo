#!/usr/bin/env python3
"""Acceptance test for the hotel order contract (quote -> server-priced order -> hold -> cancel).

Builds its own fixture through the gateway (throwaway org, hotel, room type, rate plan, inventory,
rates, owner and two customers), uses the bootstrap admin from .env (only the two
WAYLO_BOOTSTRAP_ADMIN_* keys are read, nothing is printed), and removes everything it created
in `finally` with SQL restricted to the ids/emails it generated.
Requires the stack plus the waylo-postgres container. Usage: test-order.py [--base-url URL]
"""
import argparse, json, os, re, secrets, subprocess, sys, time, uuid
from datetime import date, timedelta
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE_URL = 'http://localhost:8080'
UUID_RE = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
P = 0


def ok(cond, label):
    global P
    if not cond:
        raise AssertionError(f'FAIL: {label}')
    P += 1
    print(f'PASS: {label}')


def env_file_value(key):
    """Safe single-key reader (same rules as seed-data.sh): never sources the file."""
    try:
        for line in open(os.path.join(ROOT, '.env'), encoding='utf-8'):
            if line.startswith(key + '='):
                v = line[len(key) + 1:].rstrip('\r\n')
                if len(v) >= 2 and v[0] == v[-1] and v[0] in '"\'':
                    v = v[1:-1]
                return v
    except FileNotFoundError:
        pass
    return ''


def req(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    r = Request(BASE_URL + path, data=json.dumps(body).encode() if body is not None else None,
                headers=headers, method=method)
    try:
        with urlopen(r, timeout=30) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw, parse_float=Decimal) if raw else None)
    except HTTPError as e:
        raw = e.read()
        try:
            return e.code, (json.loads(raw, parse_float=Decimal) if raw else None)
        except ValueError:
            return e.code, None


def must(method, path, body=None, token=None, want=(200, 201)):
    s, d = req(method, path, body, token)
    if s not in want:
        raise AssertionError(f'{method} {path.split("?")[0]} -> {s} {(d or {}).get("message")}')
    return d['data'] if d and 'data' in d else d


def psql(db, sql):
    out = subprocess.run(['docker', 'exec', 'waylo-postgres', 'sh', '-c',
                          f'psql -U "${{POSTGRES_USER:-waylo}}" -d {db} -At -c "$0"', sql],
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(f'psql {db} failed: {out.stderr.strip()[:200]}')
    return out.stdout.strip()


def lit(*ids):
    for i in ids:
        assert UUID_RE.match(i), 'refusing non-uuid in SQL'
    return ','.join(f"'{i}'" for i in ids)


def register(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:12]}@example.test'
    pw = secrets.token_urlsafe(20)
    d = must('POST', '/api/auth/register', {'firstName': 'Ord', 'lastName': 'Test', 'email': email, 'password': pw})
    time.sleep(13)  # gateway register limit 5/min
    return email, d


def main():
    global BASE_URL
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default=BASE_URL)
    BASE_URL = ap.parse_args().base_url.rstrip('/')

    admin_email = env_file_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env_file_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not admin_email or not admin_pw:
        sys.exit('WAYLO_BOOTSTRAP_ADMIN_EMAIL / WAYLO_BOOTSTRAP_ADMIN_PASSWORD missing in .env')

    emails, order_ids, hotel_id, org_id = [], [], None, None
    try:
        admin = must('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})
        time.sleep(7)
        e, owner = register('ord-owner'); emails.append(e)
        e, cust_a = register('ord-cust-a'); emails.append(e)
        e, cust_b = register('ord-cust-b'); emails.append(e)
        tok_o, tok_a, tok_b, tok_admin = owner['token'], cust_a['token'], cust_b['token'], admin['token']

        # --- fixture ---
        org = must('POST', '/api/organizations', {'name': f'ord-test-{uuid.uuid4().hex[:8]}'}, tok_o)
        org_id = org['id']
        hotel = must('POST', '/api/hotels', {'name': 'Order test hotel', 'city': f'OT{uuid.uuid4().hex[:8]}',
                                              'country': 'TR', 'address': 'Test only', 'starRating': 3,
                                              'organizationId': org_id}, tok_o)
        hotel_id = hotel['id']
        must('POST', f'/api/admin/organizations/{org_id}/approve', token=tok_admin)
        room = must('POST', f'/api/hotels/{hotel_id}/room-types', {'code': 'OT_DBL', 'name': 'OT double',
                    'maxAdults': 2, 'maxChildren': 1, 'maxOccupancy': 3}, tok_o)
        plan = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'OT_FLEX', 'name': 'OT flex',
                    'mealPlan': 'BREAKFAST', 'cancellationPolicy': 'FREE_CANCELLATION',
                    'freeCancellationDaysBefore': 2}, tok_o)
        start = date.today() + timedelta(days=30)
        nights = [(start + timedelta(days=i)).isoformat() for i in range(2)]
        check_out = (start + timedelta(days=2)).isoformat()
        must('PUT', f'/api/hotels/{hotel_id}/inventory', {'items': [
            {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 3, 'expectedVersion': None} for n in nights],
            'reason': 'order acceptance fixture'}, tok_o)
        must('PUT', f'/api/hotels/{hotel_id}/rates', {'items': [
            {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': n, 'occupancy': 2,
             'currency': 'EUR', 'amount': amt, 'expectedVersion': None}
            for n, amt in zip(nights, [120.25, 130.50])]}, tok_o)

        def inventory():
            rows = psql('hotel_db', "SELECT stay_date||':'||allotment||':'||held||':'||confirmed FROM daily_inventory "
                                    f"WHERE hotel_id IN ({lit(hotel_id)}) ORDER BY stay_date")
            return rows.splitlines()

        original = inventory()
        ok(original == [f'{n}:3:0:0' for n in nights], 'fixture inventory starts at held=0')

        # --- quote and order ---
        quote_body = {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                      'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2, 'childrenAges': []}
        quote = must('POST', '/api/quotes', quote_body, tok_a)
        ok(quote['totalAmount'] == Decimal('250.75') and quote['currency'] == 'EUR', 'quote total from server rates')

        ok(req('POST', '/api/orders/hotel', {'quoteId': quote['quoteId']})[0] == 401, 'hotel order without token 401')
        s, d = req('POST', '/api/orders/hotel', {'quoteId': quote['quoteId'], 'unitAmount': 0.01,
                                                  'totalAmount': 0.01, 'currency': 'USD', 'notes': 'ord test'}, tok_a)
        ok(s == 201, f'POST /api/orders/hotel 201 (got {s})')
        order = d['data']
        order_ids.append(order['id'])
        ok(order['totalAmount'] == quote['totalAmount'] and order['currency'] == quote['currency'],
           'order price == quote total, client price fields ignored')
        item = order['items'][0]
        ok(item['vertical'] == 'HOTEL' and item['unitAmount'] == quote['totalAmount'] and item['quantity'] == 1,
           'item built from hold')
        ok(item['supplierOrganizationId'] == org_id, 'item supplierOrganizationId == hotel organization')
        ok(item['productRef'] == room['id'] and item['fulfillmentRef'], 'item productRef is room type, fulfillmentRef is hold id')
        ok(bool(order.get('holdExpiresAt')), 'response has holdExpiresAt')

        hold_row = psql('hotel_db', "SELECT status||'|'||order_correlation_id FROM hotel_holds "
                                    f"WHERE id IN ({lit(item['fulfillmentRef'])})")
        ok(hold_row == f"HELD|{order['id']}", 'hold is HELD with orderCorrelationId == order id')
        ok(inventory() == [f'{n}:3:1:0' for n in nights], 'inventory held=1 per night')

        s, d2 = req('POST', '/api/orders/hotel', {'quoteId': quote['quoteId']}, tok_a)
        ok(s == 201 and d2['data']['id'] == order['id'], 'same request again returns same order id')
        ok(psql('package_booking_db', f"SELECT count(*) FROM orders WHERE customer_id IN ({lit(cust_a['userId'])}) "
                                      f"AND quote_id IN ({lit(quote['quoteId'])})") == '1', 'only one order row')
        ok(inventory() == [f'{n}:3:1:0' for n in nights], 'repeat did not hold a second room')

        s, d = req('POST', '/api/orders/hotel', {'quoteId': quote['quoteId']}, tok_b)
        ok(400 <= s < 500, f"customer B posting A's quote is rejected (got {s})")
        ok(psql('package_booking_db', f"SELECT count(*) FROM orders WHERE customer_id IN ({lit(cust_b['userId'])})") == '0',
           'no order for customer B')

        # --- generic endpoint ---
        generic = {'currency': 'USD', 'items': [{'vertical': 'HOTEL', 'productRef': room['id'],
                   'description': 'tamper', 'unitAmount': 0.01, 'quantity': 1, 'currency': 'USD'}]}
        s, _ = req('POST', '/api/orders', dict(generic, customerId=cust_a['userId']), tok_a)
        ok(s == 403, f'generic POST /api/orders as customer 403 (got {s})')
        s, d = req('POST', '/api/orders', dict(generic, customerId=admin['userId']), tok_admin)
        ok(s == 422 and d.get('error') == 'USE_QUOTE_ENDPOINT', f'generic POST as admin with HOTEL 422 USE_QUOTE_ENDPOINT (got {s})')

        # --- cancel ---
        s, d = req('POST', f"/api/orders/{order['id']}/cancel", token=tok_a)
        ok(s == 200 and d['data']['status'] == 'CANCELLED', 'cancel 200 CANCELLED')
        ok(psql('hotel_db', f"SELECT status FROM hotel_holds WHERE id IN ({lit(item['fulfillmentRef'])})") == 'RELEASED',
           'hold RELEASED after cancel')
        ok(inventory() == original, 'inventory back to original')
        print(f'\nPASS: {P}')
        return 0
    finally:
        try:
            cleanup(hotel_id, org_id, order_ids, emails)
        except Exception as ex:  # noqa: BLE001
            print(f'WARNING: cleanup incomplete ({ex}); remove rows for emails {emails}, hotel {hotel_id}, org {org_id}')


def cleanup(hotel_id, org_id, order_ids, emails):
    if order_ids:
        psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({lit(*order_ids)})')  # items cascade
    if hotel_id:
        h = lit(hotel_id)
        psql('hotel_db', f'DELETE FROM hotel_holds WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM hotel_quotes WHERE hotel_id IN ({h})')  # nights cascade
        psql('hotel_db', f'DELETE FROM inventory_adjustments WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM daily_rates WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM daily_inventory WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM rate_plans WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM room_types WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM hotels WHERE id IN ({h})')
    quoted = ','.join("'" + e.replace("'", '') + "'" for e in emails)
    if org_id:
        o = lit(org_id)
        psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({o})')
    if emails:
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({quoted})')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({quoted}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({quoted})')


if __name__ == '__main__':
    try:
        sys.exit(main())
    except AssertionError as e:
        print(e)
        sys.exit(1)
