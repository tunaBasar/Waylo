#!/usr/bin/env python3
"""RM-38 end-to-end hotel acceptance flow, ONE live script (static-checked only by its author).

One run: two hotel organizations (A real, B negative checks only) and one customer.
Steps: onboarding -> hotel setup + photo + publication -> customer search/quote/order/pay -> partner visibility
-> redeem/check-out/review/response -> cancel/refund -> hold expiry.
Every step asserts real API and DB results. Blocked steps count as failed (RM-38 rule).
Writes scripts/acceptance/out/full-hotel-flow-<timestamp>.json (AcceptanceReport) and exits 1 on any failure.
Only the two WAYLO_BOOTSTRAP_ADMIN_* keys of .env are read, nothing secret is printed. Cleanup removes only
rows created by this run. Requires the stack plus the waylo-postgres container.
Usage: full_hotel_flow.py [--base-url URL]
Requirement ids (PRODUCT-ARCHITECTURE.md R1..R12): R1 onboarding/publication, R4 partner views, R5 calendar/stock,
R6 order/payment/escrow, R7 review, R11 full flow + delivery evidence, R12 cancel/refund/expired hold.
"""
import argparse, datetime, json, os, re, secrets, struct, subprocess, sys, time, uuid, zlib
from datetime import date, timedelta
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'scripts', 'acceptance', 'out')
BASE_URL = 'http://localhost:8080'
UUID_RE = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')

PLANNED = ['onboarding', 'hotel-setup', 'publication', 'customer-order', 'payment', 'partner-visibility',
           'stay-review', 'cancel-refund', 'expiry']
requirements, observed_stock, observed_money, delivery_state, blockers = [], {}, {}, {}, []
current = {'step': 'init'}
P = F = 0


def ck(rid, label, cond, observed=None):
    """Record one assertion. Never raises, so a step reports all its failures."""
    global P, F
    cond = bool(cond)
    requirements.append({'rId': rid, 'step': f"{current['step']}: {label}", 'observed': _jsonable(observed), 'pass': cond})
    if cond:
        P += 1
        print(f'PASS: [{rid}] {label}')
    else:
        F += 1
        print(f'FAIL: [{rid}] {label} -> {str(observed)[:200]}')
    return cond


def _jsonable(v):
    if isinstance(v, Decimal):
        return str(v)
    if isinstance(v, (dict, list, tuple)):
        return json.loads(json.dumps(v, default=str))
    return v if v is None or isinstance(v, (str, int, float, bool)) else str(v)


def env_file_value(key):
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


def raw(method, path, body=None, token=None, headers=None):
    h = dict(headers or {})
    if token:
        h['Authorization'] = f'Bearer {token}'
    r = Request(BASE_URL + path, data=body, headers=h, method=method)
    try:
        with urlopen(r, timeout=30) as resp:
            return resp.status, resp.read(), resp.headers
    except HTTPError as e:
        return e.code, e.read(), e.headers


def req(method, path, body=None, token=None):
    s, b, _ = raw(method, path, json.dumps(body).encode() if body is not None else None, token,
                  {'Content-Type': 'application/json'})
    try:
        return s, (json.loads(b, parse_float=Decimal) if b else None)
    except ValueError:
        return s, None


def must(method, path, body=None, token=None, want=(200, 201)):
    s, d = req(method, path, body, token)
    if s not in want:
        raise AssertionError(f'{method} {path.split("?")[0]} -> {s} {(d or {}).get("message") if isinstance(d, dict) else ""}')
    return d['data'] if isinstance(d, dict) and 'data' in d else d


def dat(d):
    return d['data'] if isinstance(d, dict) and 'data' in d else d


def psql(db, sql):
    out = subprocess.run(['docker', 'exec', 'waylo-postgres', 'sh', '-c',
                          f'psql -U "${{POSTGRES_USER:-waylo}}" -d {db} -At -c "$0"', sql],
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(f'psql {db} failed: {out.stderr.strip()[:200]}')
    return out.stdout.strip()


def lit(*ids):
    for i in ids:
        assert UUID_RE.match(str(i)), 'refusing non-uuid in SQL'
    return ','.join(f"'{i}'" for i in ids)


def register(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:12]}@example.test'
    pw = 'Pw7' + secrets.token_urlsafe(20)
    must('POST', '/api/auth/register', {'firstName': 'Acc', 'lastName': 'Flow', 'email': email, 'password': pw})
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    time.sleep(13)  # gateway register limit 5/min
    d = must('POST', '/api/auth/login', {'email': email, 'password': pw})
    return email, d


def png(w, h):
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    row = b'\x00' + b'\x80\x40\xc0' * w
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(row * h, 9)) + chunk(b'IEND', b''))


def upload_photo(token, hotel_id):
    b = '----' + uuid.uuid4().hex
    body = (f'--{b}\r\nContent-Disposition: form-data; name="caption"\r\n\r\nLobi\r\n'.encode()
            + f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="lobby.png"\r\nContent-Type: image/png\r\n\r\n'.encode()
            + png(800, 600) + b'\r\n' + f'--{b}--\r\n'.encode())
    s, rb, _ = raw('POST', f'/api/hotels/{hotel_id}/media', body, token,
                   {'Content-Type': f'multipart/form-data; boundary={b}'})
    if s != 201:
        raise AssertionError(f'photo upload -> {s}')
    return json.loads(rb)['data']


def publish_hotel(hotel_id, owner_token, admin_token):
    sub = must('POST', f'/api/hotels/{hotel_id}/publication-requests', {}, owner_token)
    must('POST', f'/api/admin/hotel-publications/{sub["id"]}/decisions',
         {'decision': 'APPROVE_AND_PUBLISH', 'expectedVersion': sub.get('version', 0)}, admin_token)


def poll(fn, seconds, every=2):
    end = time.time() + seconds
    last = None
    while time.time() < end:
        last = fn()
        if last:
            return last
        time.sleep(every)
    return last


# ---- DB observation helpers ----
def inventory(hotel_id):
    rows = psql('hotel_db', "SELECT stay_date||':'||allotment||':'||held||':'||confirmed FROM daily_inventory "
                            f"WHERE hotel_id IN ({lit(hotel_id)}) ORDER BY stay_date")
    return rows.splitlines()


def escrow(order_id):
    return psql('payment_db', "SELECT status||'|'||coalesce(settlement_reason,'')||'|'||gross_amount||'|'||charged_amount "
                              f"FROM escrow_items WHERE order_id IN ({lit(order_id)})")


def ledger_balance(order_id):
    return Decimal(psql('payment_db', "SELECT coalesce(sum(debit_amount)-sum(credit_amount),0) FROM ledger_entries "
                                      f"WHERE order_id IN ({lit(order_id)})") or '0')


def ledger_net(order_id, account):
    return Decimal(psql('payment_db', "SELECT coalesce(sum(credit_amount-debit_amount),0) FROM ledger_entries "
                                      f"WHERE order_id IN ({lit(order_id)}) AND account='{account}'") or '0')


def ledger_count(order_id):
    return int(psql('payment_db', f"SELECT count(*) FROM ledger_entries WHERE order_id IN ({lit(order_id)})"))


def order_status(order_id):
    return psql('package_booking_db', f"SELECT status FROM orders WHERE id IN ({lit(order_id)})")


def notif_rows(order_id):
    return psql('notification_db', f"SELECT type||'|'||status FROM notifications WHERE booking_id IN ({lit(order_id)}) ORDER BY created_at").splitlines()


def main():
    global BASE_URL
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default=BASE_URL)
    BASE_URL = ap.parse_args().base_url.rstrip('/')
    admin_email = env_file_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env_file_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    if not admin_email or not admin_pw:
        sys.exit('WAYLO_BOOTSTRAP_ADMIN_EMAIL / WAYLO_BOOTSTRAP_ADMIN_PASSWORD missing in .env')

    created = {'emails': [], 'orders': [], 'hotel': None, 'orgs': []}
    started = datetime.datetime.now(datetime.timezone.utc)
    try:
        run(admin_email, admin_pw, created)
    except Exception as ex:  # noqa: BLE001 - a crash is a failed step, never a pass
        ck('R?', 'unexpected abort', False, f'{type(ex).__name__}: {ex}')
        blockers.append(f"aborted in step '{current['step']}': {ex}")
    finally:
        try:
            cleanup(created)
        except Exception as ex:  # noqa: BLE001
            blockers.append(f'cleanup incomplete ({ex}); emails={created["emails"]} hotel={created["hotel"]} orgs={created["orgs"]}')
            print(f'WARNING: cleanup incomplete ({ex})')

    done = {r['step'].split(':')[0] for r in requirements}
    for name in PLANNED:
        if name not in done:
            ck('R?', f'{name}: BLOCKED, step never ran (counts as failed)', False, 'blocked')
            blockers.append(f'step {name} did not run')

    os.makedirs(OUT_DIR, exist_ok=True)
    ts = started.strftime('%Y%m%dT%H%M%SZ')
    path = os.path.join(OUT_DIR, f'full-hotel-flow-{ts}.json')
    report = {'run': ts, 'baseUrl': BASE_URL, 'passed': P, 'total': P + F, 'requirements': requirements,
              'observedStock': observed_stock, 'observedMoney': observed_money,
              'deliveryState': delivery_state, 'blockers': blockers}
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(report, fh, indent=2, default=str)
    print(f'\n{P}/{P + F} checks passed. Report: {path}')
    return 0 if F == 0 else 1


def run(admin_email, admin_pw, created):
    # ---------------- 1. onboarding ----------------
    current['step'] = 'onboarding'
    admin = must('POST', '/api/auth/login', {'email': admin_email, 'password': admin_pw})['token']
    time.sleep(7)
    e, owner = register('acc-owner'); created['emails'].append(e)
    e, owner_b = register('acc-ownerb'); created['emails'].append(e)
    e, cust = register('acc-cust'); created['emails'].append(e)
    tok_o, tok_b, tok_c = owner['token'], owner_b['token'], cust['token']

    org = must('POST', '/api/organizations', {'name': f'acc-hotel-{uuid.uuid4().hex[:8]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, tok_o)
    org_id = org['id']; created['orgs'].append(org_id)
    ck('R1', 'organization A created PENDING', org.get('status') == 'PENDING' or
       psql('auth_db', f"SELECT status FROM organizations WHERE id IN ({lit(org_id)})") == 'PENDING',
       psql('auth_db', f"SELECT status FROM organizations WHERE id IN ({lit(org_id)})"))
    org_b = must('POST', '/api/organizations', {'name': f'acc-hotelb-{uuid.uuid4().hex[:8]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, tok_b)
    org_b_id = org_b['id']; created['orgs'].append(org_b_id)
    must('POST', f'/api/admin/organizations/{org_b_id}/approve', token=admin)
    must('POST', f'/api/admin/organizations/{org_id}/approve', token=admin)
    st = psql('auth_db', f"SELECT status FROM organizations WHERE id IN ({lit(org_id)})")
    ck('R1', 'admin approval makes organization A ACTIVE', st == 'ACTIVE', st)
    ck('R1', 'non-admin cannot approve organization', req('POST', f'/api/admin/organizations/{org_id}/approve', None, tok_b)[0] in (401, 403), None)
    # Sales need a commission rate (COMMISSION_RATE_MISSING otherwise): the admin sets a partner-scoped 10% rate, the way
    # test-cancellation.py does. Removed again in cleanup.
    must('POST', '/api/admin/commission-rates', {'vertical': 'HOTEL', 'supplierOrganizationId': org_id, 'rate': 0.1000}, admin)
    created['commissionOrgs'] = created.get('commissionOrgs', []) + [org_id]

    # ---------------- 2. hotel setup ----------------
    current['step'] = 'hotel-setup'
    city = f'AC{uuid.uuid4().hex[:8]}'
    hotel = must('POST', '/api/hotels', {'name': 'Acceptance flow hotel', 'city': city, 'country': 'TR', 'address': 'Test only',
                                         'starRating': 4, 'organizationId': org_id}, tok_o)
    hotel_id = hotel['id']; created['hotel'] = hotel_id
    room = must('POST', f'/api/hotels/{hotel_id}/room-types', {'code': 'AC_DBL', 'name': 'AC double', 'maxAdults': 2,
                                                              'maxChildren': 1, 'maxOccupancy': 3}, tok_o)
    plan = must('POST', f'/api/hotels/{hotel_id}/rate-plans', {'code': 'AC_FLEX', 'name': 'AC flex', 'mealPlan': 'BREAKFAST',
                                                              'cancellationPolicy': 'FREE_CANCELLATION', 'freeCancellationDaysBefore': 2}, tok_o)
    start = date.today() + timedelta(days=30)
    nights = [(start + timedelta(days=i)).isoformat() for i in range(2)]
    check_out = (start + timedelta(days=2)).isoformat()
    must('PUT', f'/api/hotels/{hotel_id}/inventory', {'items': [
        {'roomTypeId': room['id'], 'stayDate': n, 'allotment': 3, 'expectedVersion': None} for n in nights],
        'reason': 'full flow acceptance fixture'}, tok_o)
    must('PUT', f'/api/hotels/{hotel_id}/rates', {'items': [
        {'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'stayDate': n, 'occupancy': 2, 'currency': 'EUR',
         'amount': amt, 'expectedVersion': None} for n, amt in zip(nights, [120.25, 130.50])]}, tok_o)
    inv0 = inventory(hotel_id)
    observed_stock['initial'] = inv0
    ck('R5', 'calendar: 2 nights allotment 3, held 0, confirmed 0', inv0 == [f'{n}:3:0:0' for n in nights], inv0)
    ck('R1', 'unpublished hotel not in public list/search',
       not any(h['id'] == hotel_id for h in (dat(req('GET', '/api/hotels')[1]) or []))
       and not any(h['id'] == hotel_id for h in (dat(req('GET', f'/api/hotels/search?city={city}')[1]) or [])), None)
    photo = upload_photo(tok_o, hotel_id)
    ck('R1', 'photo uploaded PENDING', photo.get('reviewStatus') == 'PENDING', photo.get('reviewStatus'))
    must('POST', f'/api/admin/hotel-media/{photo["id"]}/decisions', {'decision': 'APPROVE'}, admin)
    ms = psql('hotel_db', f"SELECT review_status FROM hotel_media WHERE id IN ({lit(photo['id'])})")
    ck('R1', 'admin approved photo (DB review_status APPROVED)', ms == 'APPROVED', ms)

    # ---------------- 2b. publication ----------------
    current['step'] = 'publication'
    publish_hotel(hotel_id, tok_o, admin)
    pub = psql('hotel_db', f"SELECT publication_status FROM hotels WHERE id IN ({lit(hotel_id)})")
    ck('R1', 'hotel publication_status PUBLISHED (DB)', pub == 'PUBLISHED', pub)
    # hotel-service caches active organizations for up to 30s, so poll
    listed = poll(lambda: any(h['id'] == hotel_id for h in (dat(req('GET', '/api/hotels')[1]) or [])), 70)
    ck('R1', 'published hotel in public list (anonymous)', listed, None)
    qs = f'/api/hotels/search?city={city}&checkIn={nights[0]}&checkOut={check_out}&rooms=1&adults=2'
    found = poll(lambda: any(h['id'] == hotel_id for h in (dat(req('GET', qs)[1]) or [])), 40)
    ck('R1', 'published hotel in availability search (anonymous)', found, None)
    s, body, hdr = raw('GET', f'/api/hotels/{hotel_id}/media/{photo["id"]}')
    ck('R1', 'approved photo served publicly', s == 200 and body[:4] == b'\x89PNG', s)

    # ---------------- 3. customer order ----------------
    current['step'] = 'customer-order'
    quote_body = {'hotelId': hotel_id, 'roomTypeId': room['id'], 'ratePlanId': plan['id'], 'checkIn': nights[0],
                  'checkOut': check_out, 'rooms': 1, 'adults': 2, 'childrenAges': []}
    quote = must('POST', '/api/quotes', quote_body, tok_c)
    ck('R6', 'quote total 250.75 EUR from server rates', quote['totalAmount'] == Decimal('250.75') and quote['currency'] == 'EUR',
       {'total': quote['totalAmount'], 'currency': quote['currency']})
    order = must('POST', '/api/orders/hotel', {'quoteId': quote['quoteId']}, tok_c)
    oid = order['id']; created['orders'].append(oid)
    item = order['items'][0]
    inv1 = inventory(hotel_id)
    observed_stock['order1AfterOrder'] = inv1
    ck('R6', 'order PENDING with total == quote', order.get('status') == 'PENDING' and order['totalAmount'] == quote['totalAmount'], order.get('status'))
    ck('R6', 'item supplier is organization A', item.get('supplierOrganizationId') == org_id, item.get('supplierOrganizationId'))
    ck('R5', 'hold taken: held=1 on each night', inv1 == [f'{n}:3:1:0' for n in nights], inv1)

    # ---------------- 3b. payment ----------------
    current['step'] = 'payment'
    intent = must('POST', f'/api/payments/orders/{oid}/intent', None, tok_c)
    must('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, tok_c)
    stt = poll(lambda: order_status(oid) == 'CONFIRMED', 60, 2)
    ck('R6', 'order CONFIRMED after TEST_OK', stt, order_status(oid))
    esc = poll(lambda: escrow(oid).startswith('HELD'), 30, 2)
    ck('R6', 'escrow HELD', esc, escrow(oid))
    bal = ledger_balance(oid)
    ck('R6', 'ledger balanced (debits == credits)', bal == 0 and ledger_count(oid) > 0, {'balance': bal, 'entries': ledger_count(oid)})
    inv2 = inventory(hotel_id)
    observed_stock['order1AfterPay'] = inv2
    ck('R5', 'after payment hold is converted (held+confirmed == 1 per night)',
       all(int(r.split(':')[2]) + int(r.split(':')[3]) == 1 for r in inv2), inv2)
    gross = escrow(oid).split('|')
    observed_money['order1'] = {'charged': str(quote['totalAmount']), 'escrow': escrow(oid), 'ledgerBalance': str(bal)}
    ck('R6', 'escrow amount equals quote total', len(gross) == 4 and Decimal(gross[3]) == quote['totalAmount'], gross)
    vouchers = must('GET', f'/api/orders/{oid}/vouchers', None, tok_c)
    voucher = next((v for v in vouchers if v['orderItemId'] == item['id']), None)
    ck('R6', 'voucher exists for the item', voucher is not None, len(vouchers))
    rows = poll(lambda: notif_rows(oid), 60, 2)
    delivery_state['order1Confirmation'] = rows
    ck('R11', 'notification rows exist, all SUPPRESSED (test domain)', bool(rows) and all(r.endswith('|SUPPRESSED') for r in rows), rows)

    # ---------------- 4. partner visibility ----------------
    current['step'] = 'partner-visibility'
    list_a = dat(req('GET', f'/api/partner/organizations/{org_id}/order-items?size=100', None, tok_o)[1]) or {}
    ck('R4', 'org A partner list shows the order item', any(i['orderItemId'] == item['id'] for i in list_a.get('items', [])),
       len(list_a.get('items', [])))
    s, d = req('GET', f'/api/partner/organizations/{org_b_id}/order-items?size=100', None, tok_b)
    ck('R4', 'org B own partner list does NOT contain the item',
       s == 200 and not any(i['orderItemId'] == item['id'] for i in (dat(d) or {}).get('items', [])), s)
    s, _ = req('GET', f'/api/partner/organizations/{org_id}/order-items?size=100', None, tok_b)
    ck('R4', 'org B owner cannot read org A list (403/404)', s in (403, 404), s)
    s, _ = req('GET', f'/api/partner/organizations/{org_id}/order-items?size=100', None, tok_c)
    ck('R4', 'customer cannot read partner list (403)', s == 403, s)
    s, _ = req('POST', f'/api/partner/organizations/{org_b_id}/vouchers/redeem', {'payload': voucher['payload']}, tok_b)
    ck('R4', 'org B cannot redeem org A voucher', s in (400, 403, 404, 409, 422), s)

    # ---------------- 5. stay ----------------
    current['step'] = 'stay-review'
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id IN ({lit(voucher['voucherId'])})")
    s, r = req('POST', f'/api/partner/organizations/{org_id}/vouchers/redeem', {'payload': voucher['payload']}, tok_o)
    ck('R6', 'partner redeems QR voucher (200)', s == 200, s)
    esc = poll(lambda: escrow(oid).startswith('RELEASED'), 40, 2)
    ck('R6', 'escrow RELEASED after redeem', esc, escrow(oid))
    ck('R6', 'ledger still balanced after release', ledger_balance(oid) == 0, ledger_balance(oid))
    observed_money['order1']['afterRelease'] = {'escrow': escrow(oid), 'supplierPayable': str(ledger_net(oid, 'SUPPLIER_PAYABLE')),
                                                 'commission': str(ledger_net(oid, 'PLATFORM_COMMISSION')),
                                                 'ledgerBalance': str(ledger_balance(oid))}
    row = next((i for i in (dat(req('GET', f'/api/partner/organizations/{org_id}/order-items?size=100', None, tok_o)[1]) or {}).get('items', [])
                if i['orderItemId'] == item['id']), {})
    ck('R4', 'partner list shows voucher REDEEMED', row.get('voucherStatus') == 'REDEEMED', row.get('voucherStatus'))
    s, r = req('POST', f'/api/partner/organizations/{org_id}/order-items/{item["id"]}/check-out',
               {'expectedVersion': row.get('stayVersion', 0)}, tok_o)
    ck('R7', 'partner records check-out (200, checkedOutAt set)', s == 200 and (dat(r) or {}).get('checkedOutAt'), s)
    h0 = dat(req('GET', f'/api/hotels/{hotel_id}', None, tok_o)[1]) or {}
    ck('R7', 'before review: averageScore null, count 0', h0.get('averageScore') is None and h0.get('reviewCount') == 0,
       {'avg': h0.get('averageScore'), 'count': h0.get('reviewCount')})
    s, r = req('POST', '/api/me/hotel-reviews', {'orderItemId': item['id'], 'score': 4, 'text': 'Temiz ve sessiz'}, tok_c)
    review = dat(r) or {}
    ck('R7', 'customer review score 4 created (201)', s == 201 and review.get('score') == 4, (s, (r or {}).get('error') if isinstance(r, dict) else None))
    s, r = req('POST', '/api/me/hotel-reviews', {'orderItemId': item['id'], 'score': 5, 'text': 'dup'}, tok_c)
    ck('R7', 'duplicate review rejected 409', s == 409, s)
    s, r = req('POST', f'/api/partner/organizations/{org_id}/hotel-reviews/{review.get("id")}/response', {'text': 'Tesekkurler'}, tok_o)
    ck('R7', 'partner response saved', s == 200 and (dat(r) or {}).get('partnerResponse') == 'Tesekkurler', s)
    s, _ = req('POST', f'/api/partner/organizations/{org_b_id}/hotel-reviews/{review.get("id")}/response', {'text': 'x'}, tok_b)
    ck('R7', 'org B cannot respond to the review', s in (403, 404), s)
    page = dat(req('GET', f'/api/hotels/{hotel_id}/reviews')[1]) or {}
    items = page.get('items', [])
    ck('R7', 'review public with partner response, score unchanged',
       page.get('total') == 1 and items and items[0].get('score') == 4 and items[0].get('partnerResponse') == 'Tesekkurler', page.get('total'))
    h1 = dat(req('GET', f'/api/hotels/{hotel_id}', None, tok_o)[1]) or {}
    ck('R7', 'hotel average 4.0 and count 1', h1.get('averageScore') == 4.0 and h1.get('reviewCount') == 1,
       {'avg': h1.get('averageScore'), 'count': h1.get('reviewCount')})
    inv_stay = inventory(hotel_id)
    observed_stock['order1AfterStay'] = inv_stay

    # ---------------- 6. cancel / refund ----------------
    current['step'] = 'cancel-refund'
    q2 = must('POST', '/api/quotes', quote_body, tok_c)
    o2 = must('POST', '/api/orders/hotel', {'quoteId': q2['quoteId']}, tok_c)
    oid2 = o2['id']; created['orders'].append(oid2)
    observed_stock['order2AfterOrder'] = inventory(hotel_id)
    ck('R5', 'second order held: counters +1 per night over before',
       [sum(map(int, r.split(':')[2:])) for r in observed_stock['order2AfterOrder']] ==
       [sum(map(int, r.split(':')[2:])) + 1 for r in inv_stay], observed_stock['order2AfterOrder'])
    i2 = must('POST', f'/api/payments/orders/{oid2}/intent', None, tok_c)
    must('POST', f'/api/payments/intents/{i2["id"]}/confirm', {'paymentToken': 'TEST_OK'}, tok_c)
    ck('R6', 'second order CONFIRMED', poll(lambda: order_status(oid2) == 'CONFIRMED', 60, 2), order_status(oid2))
    ck('R6', 'second escrow HELD', poll(lambda: escrow(oid2).startswith('HELD'), 30, 2), escrow(oid2))
    snap = must('GET', f'/api/orders/{oid2}', None, tok_c)['items'][0]
    # 2026-11-26 (walkthrough S1): a FREE_CANCELLATION plan with N=2 days is stored as FREE_UNTIL 48h before check-in
    ck('R12', 'item snapshot policy FREE_UNTIL 48h (plan: free cancellation, 2 days before)',
       snap.get('cancellationPolicy') == 'FREE_UNTIL' and snap.get('cancelFreeHoursBefore') == 48 and snap.get('freeCancellationUntil'),
       (snap.get('cancellationPolicy'), snap.get('cancelFreeHoursBefore'), snap.get('freeCancellationUntil')))
    prev = must('GET', f'/api/orders/{oid2}/cancellation-preview', None, tok_c)
    expected = Decimal(str(q2['totalAmount']))
    ck('R12', 'preview: cancellable, refund == order total (free window)',
       prev.get('cancellable') is True and Decimal(str(prev['totalRefund'])) == expected, prev.get('totalRefund'))
    s, r = req('POST', f'/api/orders/{oid2}/cancel', None, tok_c)
    ck('R12', 'cancel 200 CANCELLED', s == 200 and (dat(r) or {}).get('status') == 'CANCELLED', s)
    snap = must('GET', f'/api/orders/{oid2}', None, tok_c)['items'][0]
    ck('R12', 'actual refund == preview == policy amount',
       snap.get('cancelOutcome') == 'REFUND' and Decimal(str(snap.get('refundAmount'))) == expected == Decimal(str(prev['totalRefund'])),
       {'outcome': snap.get('cancelOutcome'), 'refund': snap.get('refundAmount')})
    esc2 = poll(lambda: escrow(oid2).startswith('REFUNDED'), 40, 2)
    ck('R12', 'escrow REFUNDED', esc2, escrow(oid2))
    refunded = ledger_net(oid2, 'CUSTOMER_REFUND')
    ck('R12', 'ledger CUSTOMER_REFUND == refund and balanced', refunded == expected and ledger_balance(oid2) == 0,
       {'refund': refunded, 'balance': ledger_balance(oid2)})
    observed_money['order2'] = {'charged': str(expected), 'refunded': str(refunded), 'ledgerBalance': str(ledger_balance(oid2)),
                                'escrow': escrow(oid2)}
    inv3 = poll(lambda: inventory(hotel_id) == inv_stay and inv_stay, 40, 2)
    observed_stock['order2AfterCancel'] = inventory(hotel_id)
    ck('R5', 'inventory released back to pre-order-2 level', inv3, observed_stock['order2AfterCancel'])
    rr = poll(lambda: any(r.startswith('EMAIL') for r in notif_rows(oid2)) and len(notif_rows(oid2)) >= 2, 60, 2)
    delivery_state['order2'] = notif_rows(oid2)
    ck('R11', 'order 2 notification rows all SUPPRESSED', bool(rr) and all(r.endswith('|SUPPRESSED') for r in delivery_state['order2']),
       delivery_state['order2'])

    # ---------------- 7. expiry ----------------
    current['step'] = 'expiry'
    q3 = must('POST', '/api/quotes', quote_body, tok_c)
    o3 = must('POST', '/api/orders/hotel', {'quoteId': q3['quoteId']}, tok_c)
    oid3 = o3['id']; created['orders'].append(oid3)
    observed_stock['order3AfterOrder'] = inventory(hotel_id)
    ck('R5', 'third order holds a room', observed_stock['order3AfterOrder'] != inv_stay, observed_stock['order3AfterOrder'])
    psql('package_booking_db', f"UPDATE orders SET hold_expires_at = now() - interval '1 minute' WHERE id IN ({lit(oid3)})")
    psql('hotel_db', f"UPDATE hotel_holds SET expires_at = now() - interval '1 minute' WHERE order_correlation_id IN ({lit(oid3)})")
    ex = poll(lambda: order_status(oid3) == 'EXPIRED', 180, 5)
    ck('R12', 'unpaid order EXPIRED by the job within 3 minutes', ex, order_status(oid3))
    back = poll(lambda: inventory(hotel_id) == inv_stay and inv_stay, 40, 2)
    observed_stock['order3AfterExpiry'] = inventory(hotel_id)
    ck('R12', 'inventory released after expiry', back, observed_stock['order3AfterExpiry'])
    hs = psql('hotel_db', f"SELECT status FROM hotel_holds WHERE order_correlation_id IN ({lit(oid3)})")
    ck('R12', 'hold EXPIRED or RELEASED', hs in ('EXPIRED', 'RELEASED'), hs)
    ck('R12', 'no escrow/ledger for expired order', escrow(oid3) == '' and ledger_count(oid3) == 0, escrow(oid3))
    observed_money['order3'] = {'charged': '0', 'escrow': escrow(oid3), 'ledgerEntries': ledger_count(oid3)}


def cleanup(c):
    """Delete only rows created by this run (ids and emails recorded above)."""
    order_ids, hotel_id, orgs, emails = c['orders'], c['hotel'], c['orgs'], c['emails']
    quoted = ','.join("'" + e.replace("'", '') + "'" for e in emails)
    if order_ids:
        ids = lit(*order_ids)
        psql('notification_db', f'DELETE FROM notifications WHERE booking_id IN ({ids})')
        # ledger rows are append-only by design: the trigger is disabled only for this cleanup
        psql('payment_db', 'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete')
        try:
            psql('payment_db', f'DELETE FROM ledger_entries WHERE order_id IN ({ids})')
        finally:
            psql('payment_db', 'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('payment_db', f'DELETE FROM escrow_items WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM payment_intents WHERE order_id IN ({ids})')
        psql('package_booking_db', f'DELETE FROM vouchers WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id IN ({ids}))')
        psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({ids})')  # items cascade
    if orgs:
        psql('payment_db', f'DELETE FROM commission_rates WHERE supplier_organization_id IN ({lit(*orgs)})')
    if hotel_id:
        h = lit(hotel_id)
        for t in ('hotel_reviews',):  # moderation events cascade
            psql('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({h})')
        for t in ('hotel_media_decisions', 'hotel_media', 'hotel_publication_decisions', 'hotel_publication_requests',
                  'hotel_holds', 'hotel_quotes', 'inventory_adjustments', 'daily_rates', 'daily_inventory',
                  'rate_plans', 'room_types'):
            psql('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({h})')
        psql('hotel_db', f'DELETE FROM hotels WHERE id IN ({h})')
    if orgs:
        o = lit(*orgs)
        psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({o})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({o})')
    if emails:
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({quoted})')
        psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({quoted}))')
        psql('auth_db', f'DELETE FROM users WHERE email IN ({quoted})')


if __name__ == '__main__':
    sys.exit(main())
