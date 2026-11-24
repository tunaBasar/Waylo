#!/usr/bin/env python3
"""Live acceptance test for hotel stay operations (RM-29 CHECK_OUT).

Cases: redeemed but not checked out -> review NOT_CHECKED_OUT, check-out then review accepted, double check-out is an
idempotent 200, check-out on an ACTIVE voucher 409, another org 403/404, future checkedOutAt 400.
Reuses helpers of test-reviews.py (which reuses test-car-order.py / test-package-order.py; hotels are published there
through the publication workflow). Only fixture data of this run is created/removed.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


rev = load('rev', 'test-reviews.py')
car, pkg = rev.car, rev.pkg
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def partner_item(org, item_id, token):
    s, r, _ = call('GET', f'/api/partner/organizations/{org}/order-items?size=100', token=token)
    items = (data(r) or {}).get('items', [])
    return next((i for i in items if i['orderItemId'] == item_id), {})


def main():
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    emails = {k: f'stay-{k}-{uuid.uuid4().hex[:10]}@example.test' for k in ('cust', 'part', 'oth')}
    pws = {k: k.capitalize() + '1' + secrets.token_urlsafe(16) for k in emails}
    for k, e in emails.items():
        car.register(e, pws[k])
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
    time.sleep(13)
    cust, partner, outsider = (car.login(emails[k], pws[k]) for k in ('cust', 'part', 'oth'))
    admin = car.login(admin_email, admin_pw)

    print('\n=== setup: published hotel, outsider org ===')
    (hotel, room, plan, nights, check_out), hotel_id, org = rev.hotel_with_stock(admin, partner, 'stay')
    other_org = data(call('POST', '/api/organizations', {'name': f'stay-oth-{uuid.uuid4().hex[:6]}',
                                                          'businessType': 'HOTEL', 'legalType': 'COMPANY'}, outsider))['id']
    call('POST', f'/api/admin/organizations/{other_org}/approve', token=admin)

    order_id, item_id = rev.place_paid_order(hotel, room, plan, nights, check_out, cust)
    url = f'/api/partner/organizations/{org}/order-items/{item_id}/check-out'
    review = {'orderItemId': item_id, 'score': 5, 'text': 'Harika konaklama'}

    print('\n=== ACTIVE voucher cannot be checked out ===')
    ver0 = int(psql('package_booking_db', f"SELECT version FROM vouchers WHERE order_item_id='{item_id}'"))
    s, r, _ = call('POST', url, {'expectedVersion': ver0}, partner)
    check('ACTIVE -> 409 INVALID_STATE_TRANSITION', s == 409 and err(r) == 'INVALID_STATE_TRANSITION', (s, r))

    s, r, _ = rev.redeem(order_id, item_id, cust, partner, org)
    check('redeem 200', s == 200, (s, err(r)))
    row = partner_item(org, item_id, partner)
    check('partner list: REDEEMED, checkedOutAt null', row.get('voucherStatus') == 'REDEEMED' and row.get('checkedOutAt') is None, row)
    version = row.get('stayVersion', 0)

    print('\n=== review needs a recorded check-out ===')
    s, r, _ = call('POST', '/api/me/hotel-reviews', review, cust)
    check('redeemed, no check-out -> 422 REVIEW_NOT_CHECKED_OUT', s == 422 and err(r) == 'REVIEW_NOT_CHECKED_OUT', (s, r))

    print('\n=== check-out negatives ===')
    future = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%SZ')
    s, r, _ = call('POST', url, {'expectedVersion': version, 'checkedOutAt': future}, partner)
    check('future checkedOutAt -> 400', s == 400, (s, r))
    s, r, _ = call('POST', f'/api/partner/organizations/{other_org}/order-items/{item_id}/check-out',
                   {'expectedVersion': version}, outsider)
    check('another org -> 403/404', s in (403, 404), (s, r))
    s, r, _ = call('POST', f'/api/partner/organizations/{org}/order-items/{item_id}/check-out',
                   {'expectedVersion': version}, outsider)
    check('outsider on this org -> 403', s == 403, (s, r))
    s, r, _ = call('POST', url, {'expectedVersion': version}, cust)
    check('customer -> 403', s == 403, (s, r))

    print('\n=== check-out, idempotency, review ===')
    s, r, _ = call('POST', url, {'expectedVersion': version}, partner)
    first = data(r) or {}
    check('check-out 200 with checkedOutAt', s == 200 and first.get('checkedOutAt'), (s, r))
    s, r, _ = call('POST', url, {'expectedVersion': version}, partner)
    again = data(r) or {}
    check('second check-out 200, same checkedOutAt', s == 200 and again.get('checkedOutAt') == first.get('checkedOutAt'), (s, r))
    row = partner_item(org, item_id, partner)
    check('partner list exposes checkedOutAt', row.get('checkedOutAt') is not None, row)
    s, r, _ = call('POST', '/api/me/hotel-reviews', review, cust)
    check('review accepted after check-out (201)', s == 201, (s, r))


if __name__ == '__main__':
    try:
        main()
    finally:
        car.cleanup()
        pkg.hotel_cleanup()
