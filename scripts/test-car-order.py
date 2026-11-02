#!/usr/bin/env python3
"""Live acceptance test for car order flow through package-booking + payment services.

Checks:
 1. Setup: sellable CAR_RENTAL COMPANY org with branch, vehicle, rate, approved docs, commission.
 2. Customer car order: PENDING, CAR vertical, correct amount, Turkish description.
 3. Admin car order: 403 ADMIN_CANNOT_PURCHASE.
 4. Same-slot conflict: 409 NO_VEHICLE_AVAILABLE (not 500).
 5. Pay order: CONFIRMED rental, escrow with CAR vertical and correct commission.
 6. Voucher window: valid_from = pickupAt - 2h, valid_until = returnAt (Istanbul).
 7. Voucher redeem: reject before window, shift valid_from with psql, succeed then second reject.
 8. Cancel+release: second order cancelled before payment releases hold, slot bookable again.
 9. Foreign user cannot read another's rental (403/404).
10. Verify commission on escrow and ledger balance.

Credentials never printed. Unique emails per run. Only deletes own fixture data.
"""
import json
import pathlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import datetime

BASE = 'http://localhost:8080'
ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = b'%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'

results = []
created_orgs = []
order_ids = []
created_emails = []


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


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
    """Extract 'data' from full call() response tuple, or from a dict body."""
    if isinstance(resp, tuple):
        resp = resp[1]
    return (resp or {}).get('data') if isinstance(resp, dict) else None


def err(resp):
    """Extract error code from full call() response tuple, or from a dict body."""
    if isinstance(resp, tuple):
        resp = resp[1]
    return resp.get('error') if isinstance(resp, dict) else None


def psql(db, sql):
    r = subprocess.run(
        ['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
        capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def login(email, password):
    s, b, _ = call('POST', '/api/auth/login', {'email': email, 'password': password})
    if s != 200:
        sys.exit(f'login {email} failed ({s})')
    return b['data']['token']


def register(email, password):
    s, b, _ = call('POST', '/api/auth/register',
                    {'firstName': 'Test', 'lastName': 'User', 'email': email, 'password': password})
    if s not in (200, 201):
        sys.exit(f'register {email} failed ({s})')
    created_emails.append(email)


def cleanup():
    """Remove all fixture data created during this run."""
    org_ids = ','.join("'" + o + "'" for o in created_orgs) if created_orgs else ''
    if created_orgs:
        # Clean up car-rental document files
        keys = psql('car_rental_db',
                    f"SELECT storage_key FROM documents WHERE organization_id IN ({org_ids})").split()
        for k in keys:
            if k and all(c in '0123456789abcdef-' for c in k):
                subprocess.run(
                    ['docker', 'exec', 'waylo-car-rental-service', 'rm', '-f', '/data/car-documents/' + k],
                    capture_output=True)
        for t in ('rentals', 'documents', 'one_way_routes', 'category_rates', 'vehicles', 'branches'):
            psql('car_rental_db', f'DELETE FROM {t} WHERE organization_id IN ({org_ids})')
        # Remove org role assignments and memberships
        psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({org_ids})')
        psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({org_ids})')
        psql('auth_db', f'DELETE FROM organizations WHERE id IN ({org_ids})')
    if order_ids:
        ids = ','.join("'" + oid + "'" for oid in order_ids)
        psql('payment_db',
             'ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('payment_db', f'DELETE FROM ledger_entries WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM escrow_items WHERE order_id IN ({ids})')
        psql('payment_db', f'DELETE FROM payment_intents WHERE order_id IN ({ids})')
        psql('payment_db',
             'ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_no_update_delete')
        psql('package_booking_db',
             f'DELETE FROM vouchers WHERE order_item_id IN '
             f'(SELECT id FROM order_items WHERE order_id IN ({ids}))')
        psql('package_booking_db', f'DELETE FROM order_items WHERE order_id IN ({ids})')
        psql('package_booking_db', f'DELETE FROM orders WHERE id IN ({ids})')
    if created_emails:
        q = ','.join("'" + e + "'" for e in created_emails)
        psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
        psql('auth_db',
             f"DELETE FROM refresh_tokens WHERE user_id IN "
             f"(SELECT id FROM users WHERE email IN ({q}))")
        psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def _istanbul_zone():
    return datetime.timezone(datetime.timedelta(hours=3))


def _round_minute(dt):
    """Round datetime to nearest 00 or 30 minute boundary."""
    if dt.minute < 30:
        return dt.replace(minute=0, second=0, microsecond=0)
    return dt.replace(minute=30, second=0, microsecond=0)


def _parse_utc(s):
    """Parse a 'YYYY-MM-DD HH:MM:SS' string as UTC timestamp."""
    try:
        return datetime.datetime.strptime(s, '%Y-%m-%d %H:%M:%S').replace(
            tzinfo=datetime.timezone.utc).timestamp()
    except Exception:
        return 0


# ============================================================
#  Setup helpers
# ============================================================

def _setup_org(admin_token, partner_token, partner_email):
    """Create a CAR_RENTAL org with branch, vehicle, rate, docs, commission.

    Returns (org_id, branch_id, vehicle_id, pick_dt, ret_dt, pick_str, ret_str).
    """
    org_id = data(call('POST', '/api/organizations',
                       {'name': 'Car Order Test Co ' + uuid.uuid4().hex[:5],
                        'businessType': 'CAR_RENTAL', 'legalType': 'COMPANY'},
                       partner_token))['id']
    created_orgs.append(org_id)
    call('POST', f'/api/admin/organizations/{org_id}/approve', token=admin_token)
    P = f'/api/partner/organizations/{org_id}'

    # Branch
    br = {'city': 'Antalya', 'address': 'Test cad. 1', 'phone': '+905550000000',
          'openTime': '08:00:00', 'closeTime': '20:00:00'}
    sa, ba, _ = call('POST', P + '/branches', {**br, 'name': 'Test Branch A'},
                     token=partner_token)
    BRANCH_A = ba['data']['id']

    # Vehicle
    plate = '34 TST' + str(secrets.randbelow(900) + 100)
    sv, bv, _ = call('POST', P + '/vehicles', {
        'homeBranchId': BRANCH_A, 'plateNumber': plate,
        'brand': 'Fiat', 'model': 'Egea', 'year': 2024, 'category': 'ECONOMY',
        'transmission': 'MANUAL', 'fuelType': 'DIESEL', 'seatCount': 5},
                     token=partner_token)
    VEHICLE = bv['data']['id']

    # Rate
    rate = {'branchId': BRANCH_A, 'category': 'ECONOMY', 'transmission': 'MANUAL',
            'currency': 'TRY', 'dailyRate': 1500, 'depositAmount': 5000,
            'kmLimitPerDay': 300, 'extraKmFee': 5, 'minDriverAge': 21,
            'minLicenseYears': 2}
    call('PUT', P + '/rates', {'items': [rate]}, token=partner_token)

    # Documents
    far = str(datetime.date.today() + datetime.timedelta(days=400))

    def upload(dtype, number, vehicle=None):
        fields = {'type': dtype, 'documentNumber': number, 'validUntil': far,
                  'vehicleId': vehicle}
        body, ctype = multipart(fields, 'belge.pdf', PDF)
        return call('POST', P + '/documents', token=partner_token,
                    raw=body, headers={'Content-Type': ctype})

    for t, n, veh in [
            ('RENTAL_AUTHORIZATION', 'YB-2026-CO', None),
            ('TAX_CERTIFICATE', 'VL-CO-1', None),
            ('VEHICLE_REGISTRATION', 'RU-CO-1', VEHICLE),
            ('RENTAL_INSURANCE', 'KS-CO-1', VEHICLE),
            ('TRAFFIC_INSURANCE', 'TR-CO-1', VEHICLE)]:
        s, b, _ = upload(t, n, veh)
        check(f'upload {t}', s == 201, s)
        call('POST', f'/api/admin/car-documents/{b["data"]["id"]}/approve',
             token=admin_token)

    # Check sellable
    st = data(call('GET', P + '/status', token=partner_token))
    check('org is sellable', st and st.get('sellable') is True, st)

    # Commission rate for CAR vertical
    commission_exists = psql('payment_db',
                             f"SELECT count(*) FROM commission_rates "
                             f"WHERE vertical='CAR' AND "
                             f"supplier_organization_id='{org_id}'")
    if commission_exists != '0':
        check('CAR commission rate exists', True)
    else:
        s, _, _ = call('POST', '/api/admin/commission-rates',
                       {'vertical': 'CAR', 'supplierOrganizationId': org_id,
                        'rate': 0.10}, token=admin_token)
        check('CAR commission rate created',
              s in (200, 201), s)
        check('CAR commission rate verified in DB',
              psql('payment_db',
                   f"SELECT count(*) FROM commission_rates "
                   f"WHERE vertical='CAR' AND "
                   f"supplier_organization_id='{org_id}'") != '0',
              commission_exists)

    # Pickup/return times - must be within branch hours (08-20) and at least 3h from now
    now_dt = datetime.datetime.now(_istanbul_zone())
    base_pickup = now_dt + datetime.timedelta(hours=3)
    # Round to nearest 00 or 30 minute boundary
    if base_pickup.minute < 30:
        base_pickup = base_pickup.replace(minute=0, second=0, microsecond=0)
    else:
        base_pickup = base_pickup.replace(minute=30, second=0, microsecond=0)
    # Ensure pickup is within branch hours (08:00-20:00), next day if needed
    if base_pickup.hour < 8:
        base_pickup = base_pickup.replace(hour=8, minute=0, second=0, microsecond=0)
    elif base_pickup.hour >= 20:
        base_pickup = (base_pickup + datetime.timedelta(days=1)).replace(hour=8, minute=0, second=0, microsecond=0)
    pickup_dt = base_pickup
    return_dt = _round_minute(pickup_dt + datetime.timedelta(hours=26))
    # Ensure return is within branch hours too
    if return_dt.hour < 8:
        return_dt = return_dt.replace(hour=8, minute=0, second=0, microsecond=0)
    elif return_dt.hour >= 20:
        return_dt = return_dt.replace(hour=8, minute=0, second=0, microsecond=0)
    check('pickup within branch hours (08-20)',
          '08' <= pickup_dt.strftime('%H') <= '20')
    check('return within branch hours (08-20)',
          '08' <= return_dt.strftime('%H') <= '20')

    return (org_id, BRANCH_A, VEHICLE, pickup_dt, return_dt,
            pickup_dt.strftime('%Y-%m-%dT%H:%M:00'),
            return_dt.strftime('%Y-%m-%dT%H:%M:00'))


# ============================================================
#  Main
# ============================================================

def main():
    admin_email = env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')

    # ---- Register users with tracked passwords ----
    customer_email = f'car-ord-cust-{uuid.uuid4().hex[:10]}@example.test'
    customer2_email = f'car-ord-cust2-{uuid.uuid4().hex[:10]}@example.test'
    partner_email = f'car-ord-partner-{uuid.uuid4().hex[:10]}@example.test'
    other_email = f'car-ord-other-{uuid.uuid4().hex[:10]}@example.test'

    pw_cust = 'Cust1' + secrets.token_urlsafe(16)
    pw_cust2 = 'Cust2' + secrets.token_urlsafe(16)
    pw_partner = 'Part1' + secrets.token_urlsafe(16)
    pw_other = 'Other1' + secrets.token_urlsafe(16)

    for e, pw in [(customer_email, pw_cust), (partner_email, pw_partner),
                  (customer2_email, pw_cust2), (other_email, pw_other)]:
        register(e, pw)

    # Verify emails
    for e in [customer_email, partner_email, customer2_email, other_email]:
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
        time.sleep(13)  # gateway rate limit between registrations

    # Login
    partner_token = login(partner_email, pw_partner)
    cust_token = login(customer_email, pw_cust)
    cust2_token = login(customer2_email, pw_cust2)
    other_token = login(other_email, pw_other)
    admin_token = login(admin_email, admin_pw)

    # ---- CHECK 1: Setup ----
    print('\n=== CHECK 1: Setup sellable CAR_RENTAL COMPANY org ===')
    (org_id, BRANCH_A, VEHICLE,
     pickup_dt, return_dt, pickup_at, return_at) = _setup_org(
        admin_token, partner_token, partner_email)

    # ---- CHECK 2: Customer car order ----
    print('\n=== CHECK 2: Customer POST /api/orders/car ===')
    order_payload = {
        'pickupBranchId': BRANCH_A,
        'returnBranchId': BRANCH_A,
        'category': 'ECONOMY',
        'transmission': 'MANUAL',
        'pickupAt': pickup_at,
        'returnAt': return_at,
        'driverAge': 30
    }
    s, order_resp, _ = call('POST', '/api/orders/car', order_payload,
                            token=cust_token)
    check('customer car order returns 201', s == 201, s)

    order = data(order_resp)
    order_id = order['id']
    order_ids.append(order_id)
    items = order.get('items', [])
    check('order has exactly 1 item', len(items) == 1, len(items))

    item = items[0]
    check('item vertical is CAR', item['vertical'] == 'CAR', item['vertical'])
    check('order status is PENDING', order['status'] == 'PENDING',
          order['status'])
    check('order total == item unitAmount',
          float(order['totalAmount']) == float(item['unitAmount']),
          f"order={order['totalAmount']} item={item['unitAmount']}")
    check('item has pickupAt', item.get('pickupAt') is not None,
          item.get('pickupAt'))
    check('item has returnAt', item.get('returnAt') is not None,
          item.get('returnAt'))

    desc = item.get('description', '')
    check('description contains Turkish "veya benzeri"',
          'veya benzeri' in desc, desc)
    check('description contains "gun" or "g\u00fcn"',
          'gun' in desc or 'g\u00fcn' in desc, desc)

    # ---- CHECK 3: Admin cannot purchase ----
    print('\n=== CHECK 3: Admin car order -> 403 ADMIN_CANNOT_PURCHASE ===')
    s, admin_order_resp, _ = call('POST', '/api/orders/car', order_payload,
                                  token=admin_token)
    admin_err = err(admin_order_resp)
    check('admin gets 403 for car order', s == 403, s)
    check('admin error code ADMIN_CANNOT_PURCHASE',
          admin_err == 'ADMIN_CANNOT_PURCHASE', admin_err)

    # ---- CHECK 4: Same-slot conflict ----
    print('\n=== CHECK 4: Same-slot conflict -> 409 NO_VEHICLE_AVAILABLE ===')
    s, conflict_resp, _ = call('POST', '/api/orders/car', order_payload,
                               token=cust2_token)
    conflict_err = err(conflict_resp)
    check('second customer gets 409 (not 500)', s == 409, s)
    check('conflict error NO_VEHICLE_AVAILABLE',
          conflict_err == 'NO_VEHICLE_AVAILABLE', conflict_err)

    # ---- CHECK 5: Pay the order ----
    print('\n=== CHECK 5: Pay the order -> CONFIRMED ===')
    s, intent_resp, _ = call('POST',
                             f'/api/payments/orders/{order_id}/intent',
                             None, cust_token)
    check('payment intent created', s in (200, 201), s)
    intent = data(intent_resp)
    intent_id = intent['id']
    check('intent amount from server',
          float(intent['amount']) == float(order['totalAmount']),
          f"intent={intent['amount']} order={order['totalAmount']}")
    check('intent currency TRY', intent['currency'] == 'TRY', intent['currency'])

    s, confirm_resp, _ = call('POST',
                              f'/api/payments/intents/{intent_id}/confirm',
                              {'paymentToken': 'TEST_OK'}, cust_token)
    check('payment confirmed', s == 200, s)
    confirm_data = data(confirm_resp)
    check('intent status AUTHORIZED',
          confirm_data.get('status') == 'AUTHORIZED',
          confirm_data.get('status'))

    s, order_check, _ = call('GET', f'/api/orders/{order_id}',
                             token=cust_token)
    order_status = data(order_check)['status']
    check('order status CONFIRMED after payment',
          order_status == 'CONFIRMED', order_status)

    # Check rental via /api/rentals/me
    s, rentals_resp, _ = call('GET', '/api/rentals/me', token=cust_token)
    rentals = data(rentals_resp)
    check('customer sees rental in /api/rentals/me',
          len(rentals or []) >= 1, f"count={len(rentals or [])}")
    rental_found = any(r['status'] == 'CONFIRMED' for r in (rentals or []))
    check('rental status CONFIRMED', rental_found, rentals)

    # Partner sees confirmed rental
    s, partner_rentals_resp, _ = call(
        'GET', f'/api/partner/organizations/{org_id}/rentals',
        token=partner_token)
    partner_rentals = data(partner_rentals_resp)
    check('partner sees confirmed rental',
          any(r['status'] == 'CONFIRMED' for r in (partner_rentals or [])),
          partner_rentals)

    # Escrow
    esc = psql('payment_db',
               f"SELECT vertical||'|'||gross_amount||'|'||commission_rate"
               f"||'|'||commission_amount||'|'||supplier_amount||'|'||status "
               f"FROM escrow_items WHERE order_id='{order_id}'")
    check('escrow row exists CAR HELD',
          esc.startswith('CAR|') and 'HELD' in esc, esc)
    if esc:
        parts = esc.split('|')
        check('escrow vertical CAR', parts[0] == 'CAR', parts[0])
        check('escrow status HELD', parts[5] == 'HELD', parts[5])

    # ---- CHECK 6: Voucher window ----
    print('\n=== CHECK 6: Voucher valid_from and valid_until ===')
    s, vouchers_resp, _ = call('GET', f'/api/orders/{order_id}/vouchers',
                               token=cust_token)
    vouchers = data(vouchers_resp)
    check('voucher exists for car order',
          len(vouchers or []) >= 1, f"count={len(vouchers or [])}")

    voucher = vouchers[0]
    voucher_id = voucher['voucherId']

    pickup_tz = _istanbul_zone()
    pickup_inst = datetime.datetime.strptime(pickup_at,
                                             '%Y-%m-%dT%H:%M:%S').replace(
        tzinfo=pickup_tz)
    valid_from_expected = (
        pickup_inst - datetime.timedelta(hours=2)).astimezone(
        datetime.timezone.utc)
    return_inst = datetime.datetime.strptime(return_at,
                                             '%Y-%m-%dT%H:%M:%S').replace(
        tzinfo=pickup_tz)
    valid_until_expected = return_inst.astimezone(datetime.timezone.utc)

    db_valid_from = psql('package_booking_db',
                         f"SELECT to_char(valid_from, 'YYYY-MM-DD HH24:MI:SS') "
                         f"FROM vouchers WHERE id='{voucher_id}'")
    db_valid_until = psql('package_booking_db',
                          f"SELECT to_char(valid_until, 'YYYY-MM-DD HH24:MI:SS') "
                          f"FROM vouchers WHERE id='{voucher_id}'")

    expected_from_utc = valid_from_expected.strftime('%Y-%m-%d %H:%M:%S')
    expected_until_utc = valid_until_expected.strftime('%Y-%m-%d %H:%M:%S')

    check('valid_from ~= pickupAt - 2h IST',
          abs(_parse_utc(db_valid_from) -
              valid_from_expected.timestamp()) < 30,
          f"expected={expected_from_utc} db={db_valid_from}")
    check('valid_until ~= returnAt IST',
          abs(_parse_utc(db_valid_until) -
              valid_until_expected.timestamp()) < 30,
          f"expected={expected_until_utc} db={db_valid_until}")

    # ---- CHECK 7: Voucher redeem ----
    print('\n=== CHECK 7: Voucher redeem window check ===')
    code_hash = voucher.get('payload', '').split(':')[-1] if voucher.get(
        'payload') else ''
    payload = f"WAYLO1:{voucher_id}:{code_hash}"

    # Redeem NOW -> rejected (valid_from in future)
    redeem_endpoint = f'/api/partner/organizations/{org_id}/vouchers/redeem'
    s, redeem_resp, _ = call('POST', redeem_endpoint,
                             {'payload': payload}, token=partner_token)
    redeem_err = err(redeem_resp)
    check('redeem now rejected (not yet valid)',
          s in (409, 422) and 'NOT_YET_VALID' in (redeem_err or ''),
          f"status={s} error={redeem_err}")

    # Shift valid_from into past
    psql('package_booking_db',
         f"UPDATE vouchers SET valid_from = now() - interval '1 hour' "
         f"WHERE id='{voucher_id}'")

    # Redeem succeeds
    s, redeem2_resp, _ = call('POST', redeem_endpoint,
                              {'payload': payload}, token=partner_token)
    redeem2_data = data(redeem2_resp)
    check('redeem after shifting valid_from succeeds',
          s == 200, f"status={s} data={redeem2_data}")
    if s == 200:
        check('redeem releases amount',
              redeem2_data.get('amountReleased') is not None, redeem2_data)

    # Second redeem rejected
    s, redeem3_resp, _ = call('POST', redeem_endpoint,
                              {'payload': payload}, token=partner_token)
    check('second redeem rejected (already redeemed)',
          s == 409, f"status={s}")

    # ---- CHECK 8: Cancel + release -> slot bookable again ----
    print('\n=== CHECK 8: Cancel order releases hold ===')

    second_pickup = _round_minute(pickup_dt + datetime.timedelta(days=7))
    second_return = _round_minute(second_pickup + datetime.timedelta(hours=26))
    second_pickup_str = second_pickup.strftime('%Y-%m-%dT%H:%M:00')
    second_return_str = second_return.strftime('%Y-%m-%dT%H:%M:00')
    second_payload = {
        'pickupBranchId': BRANCH_A,
        'returnBranchId': BRANCH_A,
        'category': 'ECONOMY',
        'transmission': 'MANUAL',
        'pickupAt': second_pickup_str,
        'returnAt': second_return_str,
        'driverAge': 30
    }

    s, second_order_resp, _ = call('POST', '/api/orders/car', second_payload,
                                   token=cust2_token)
    check('second order created', s == 201, s)
    second_order = data(second_order_resp)
    second_order_id = second_order['id']
    order_ids.append(second_order_id)
    check('second order PENDING',
          second_order['status'] == 'PENDING', second_order['status'])

    # Cancel second order
    s, cancel_resp, _ = call('POST', f'/api/orders/{second_order_id}/cancel',
                             None, token=cust2_token)
    check('second order cancelled', s == 200, s)
    cancel_data = data(cancel_resp)
    check('cancelled order status CANCELLED',
          cancel_data['status'] == 'CANCELLED', cancel_data.get('status'))

    # Slot should be bookable again
    s, rebook_resp, _ = call('POST', '/api/orders/car', second_payload,
                             token=cust_token)
    check('slot bookable again after cancel (201)',
          s == 201, s)

    if s == 201:
        rebook_order = data(rebook_resp)
        rebook_order_id = rebook_order['id']
        call('POST', f'/api/orders/{rebook_order_id}/cancel',
             None, token=cust_token)
        order_ids.remove(rebook_order_id) if rebook_order_id in order_ids else None
        order_ids.remove(second_order_id) if second_order_id in order_ids else None

    # ---- CHECK 9: Foreign user cannot read another's rental ----
    print('\n=== CHECK 9: Foreign user cannot read another rental ===')
    s, rentals_resp2, _ = call('GET', '/api/rentals/me', token=cust_token)
    rentals_list = data(rentals_resp2) or []
    rental_id = None
    for r in rentals_list:
        if r['status'] == 'CONFIRMED':
            rental_id = r['id']
            break
    check('found a CONFIRMED rental ID', rental_id is not None, rentals_list)

    if rental_id:
        s, foreign_resp, _ = call(
            'GET', f'/api/rentals/{rental_id}', token=other_token)
        check('foreign user gets 403/404 for another rental',
              s in (403, 404), f"status={s}")

    # ---- CHECK 10: Commission and ledger ----
    print('\n=== CHECK 10: Commission on escrow ===')
    esc_check = psql('payment_db',
                     f"SELECT commission_rate||'|'||commission_amount"
                     f"||'|'||supplier_amount FROM escrow_items "
                     f"WHERE order_id='{order_id}'")
    if esc_check:
        parts = esc_check.split('|')
        check('commission_rate 0.10 (10%)',
              float(parts[0]) == 0.10 if parts[0] else False, esc_check)
        ledger = psql('payment_db',
                      f"SELECT sum(debit_amount)-sum(credit_amount) "
                      f"FROM ledger_entries WHERE order_id='{order_id}'")
        check('ledger balanced after payment',
              ledger in ('0', '0.00'), f"ledger={ledger}")

    # ---- Results ----
    failed = [name for name, ok in results if not ok]
    passed = len(results) - len(failed)
    total = len(results)
    print(f'\n{passed}/{total} checks passed')
    if failed:
        print(f'FAILED: {", ".join(failed)}')
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        cleanup()
