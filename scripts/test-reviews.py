#!/usr/bin/env python3
"""Live acceptance test for verified hotel reviews (RM-29).

Flow: hotel org + hotel, customer order paid, voucher redeemed (guest checked in), stay dates moved to the past with
psql on the test's own order item (fixture only), then: positive review, duplicate 409, not-redeemed rejected, other
user rejected, hotel-org member rejected, partner response, partner cannot alter score, admin hide removes the review
from the public list and the aggregate, an empty hotel has a null score.

Reuses helpers of test-car-order.py and fixtures of test-package-order.py. Credentials never printed, only fixture
data created by this run is removed.
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


car = load('car_order', 'test-car-order.py')
pkg = load('pkg_order', 'test-package-order.py')
call, check, data, err, psql = car.call, car.check, car.data, car.err, car.psql


def place_paid_order(hotel, room, plan, nights, check_out, token, redeem_with=None, org=None):
    q = data(call('POST', '/api/quotes', {'hotelId': hotel, 'roomTypeId': room['id'], 'ratePlanId': plan['id'],
                                          'checkIn': nights[0], 'checkOut': check_out, 'rooms': 1, 'adults': 2,
                                          'childrenAges': []}, token))
    s, r, _ = call('POST', '/api/orders/hotel', {'quoteId': q['quoteId']}, token)
    assert s == 201, (s, r)
    order = data(r)
    car.order_ids.append(order['id'])
    s, r, _ = call('POST', f'/api/payments/orders/{order["id"]}/intent', None, token)
    intent = data(r)
    s, r, _ = call('POST', f'/api/payments/intents/{intent["id"]}/confirm', {'paymentToken': 'TEST_OK'}, token)
    assert s == 200, (s, r)
    return order['id'], order['items'][0]['id']


def redeem(order_id, item_id, owner_token, token, org):
    vs = data(call('GET', f'/api/orders/{order_id}/vouchers', token=owner_token)[1])
    v = next(x for x in vs if x['orderItemId'] == item_id)
    psql('package_booking_db', f"UPDATE vouchers SET valid_from = now() - interval '1 hour' WHERE id='{v['voucherId']}'")
    return call('POST', f'/api/partner/organizations/{org}/vouchers/redeem', {'payload': v['payload']}, token)


def hotel_with_stock(admin, partner, label):
    # pkg.hotel_fixture keeps its ids in pkg.hotel_state, so run it once per hotel and remember them.
    pkg.hotel_state.update({'hotel': None, 'org': None})
    h = pkg.hotel_fixture(admin, partner)
    return h, pkg.hotel_state['hotel'], pkg.hotel_state['org']


EMPTY = {'id': None}


def main():
    admin_email = car.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL')
    admin_pw = car.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    emails = {k: f'rev-{k}-{uuid.uuid4().hex[:10]}@example.test' for k in ('cust', 'other', 'part')}
    pws = {k: k.capitalize() + '1' + secrets.token_urlsafe(16) for k in emails}
    for k, e in emails.items():
        car.register(e, pws[k])
        psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{e}'")
    time.sleep(13)
    cust, other, partner = (car.login(emails[k], pws[k]) for k in ('cust', 'other', 'part'))
    admin = car.login(admin_email, admin_pw)
    cust_id = psql('auth_db', f"SELECT id FROM users WHERE email='{emails['cust']}'")

    print('\n=== setup: two hotels (one stays empty) ===')
    (hotel, room, plan, nights, check_out), hotel_id, org = hotel_with_stock(admin, partner, 'main')
    org_main = org
    empty_hotel = data(call('POST', '/api/hotels', {'name': 'Empty review hotel', 'city': f'RV{uuid.uuid4().hex[:6]}',
                                                    'country': 'TR', 'address': 'Test only', 'starRating': 3,
                                                    'organizationId': org_main}, partner))['id']
    EMPTY['id'] = empty_hotel
    s, r, _ = call('GET', f'/api/hotels/{empty_hotel}', token=partner)
    check('hotel without reviews: averageScore null, count 0',
          s == 200 and data(r)['averageScore'] is None and data(r)['reviewCount'] == 0, data(r))

    order_id, item_id = place_paid_order(hotel, room, plan, nights, check_out, cust)
    review_body = {'orderItemId': item_id, 'score': 4, 'text': '  Temiz ve sessiz  '}

    print('\n=== not eligible before check-in ===')
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body, cust)
    check('not redeemed -> 422 REVIEW_NOT_CHECKED_IN', s == 422 and err(r) == 'REVIEW_NOT_CHECKED_IN', (s, r))
    s, r, _ = call('GET', '/api/me/reviewable-hotel-items', token=cust)
    check('not listed as reviewable', s == 200 and data(r) == [], (s, r))

    print('\n=== redeem (guest checked in) but stay not finished ===')
    s, r, _ = redeem(order_id, item_id, cust, partner, org_main)
    check('voucher redeem 200', s == 200, (s, err(r)))
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body, cust)
    check('redeemed, not checked out -> 422 REVIEW_NOT_CHECKED_OUT', s == 422 and err(r) == 'REVIEW_NOT_CHECKED_OUT', (s, r))
    ver = int(psql('package_booking_db', f"SELECT version FROM vouchers WHERE order_item_id='{item_id}'"))
    s, r, _ = call('POST', f'/api/partner/organizations/{org_main}/order-items/{item_id}/check-out',
                   {'expectedVersion': ver}, partner)
    check('partner check-out 200', s == 200, (s, r))

    # Fixture only: move this test order's own stay into the past.
    past_in = (datetime.date.today() - datetime.timedelta(days=3)).isoformat()
    past_out = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
    psql('package_booking_db', f"UPDATE order_items SET check_in='{past_in}', check_out='{past_out}' WHERE id='{item_id}'")

    print('\n=== positive path and negatives ===')
    s, r, _ = call('GET', '/api/me/reviewable-hotel-items', token=cust)
    check('item listed as reviewable', s == 200 and [i['orderItemId'] for i in data(r)] == [item_id], (s, r))
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body, other)
    check('other user -> 403', s == 403, (s, r))
    s, r, _ = call('POST', '/api/me/hotel-reviews', dict(review_body, score=6), cust)
    check('score 6 -> 400', s == 400, (s, r))
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body)
    check('anonymous -> 401', s == 401, s)
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body, cust)
    review = data(r) or {}
    check('review created 201, text trimmed', s == 201 and review.get('score') == 4 and review.get('text') == 'Temiz ve sessiz', (s, r))
    check('review has no customer id', 'customerId' not in review, review)
    s, r, _ = call('POST', '/api/me/hotel-reviews', review_body, cust)
    check('duplicate -> 409', s == 409, (s, r))
    s, r, _ = call('GET', '/api/me/reviewable-hotel-items', token=cust)
    check('reviewed item no longer reviewable', s == 200 and data(r) == [], (s, r))
    s, r, _ = call('GET', '/api/me/hotel-reviews', token=cust)
    check('own reviews list has it', s == 200 and [x['id'] for x in data(r)] == [review.get('id')], (s, r))

    print('\n=== public list and aggregate ===')
    s, r, _ = call('GET', f'/api/hotels/{hotel}/reviews')
    page = data(r) or {}
    check('public list (anonymous) shows the review, no PII', s == 200 and page.get('total') == 1
          and 'customerId' not in page['items'][0] and page['items'][0].get('status') is None, (s, r))
    check('page aggregate 4.0 / 1', page.get('averageScore') == 4.0 and page.get('reviewCount') == 1, page)
    s, r, _ = call('GET', f'/api/hotels/{hotel}')
    h = data(r) or {}
    check('hotel DTO averageScore/reviewCount', h.get('averageScore') == 4.0 and h.get('reviewCount') == 1, h)

    print('\n=== member of the hotel organization cannot review ===')
    order2, item2 = place_paid_order(hotel, room, plan, nights, check_out, partner)
    s, r, _ = redeem(order2, item2, partner, partner, org_main)
    check('partner own order redeem 200', s == 200, (s, err(r)))
    psql('package_booking_db', f"UPDATE order_items SET check_in='{past_in}', check_out='{past_out}' WHERE id='{item2}'")
    s, r, _ = call('POST', '/api/me/hotel-reviews', {'orderItemId': item2, 'score': 5, 'text': 'self'}, partner)
    check('org member -> 403 ORG_MEMBER_CANNOT_REVIEW', s == 403 and err(r) == 'ORG_MEMBER_CANNOT_REVIEW', (s, r))
    s, r, _ = call('GET', '/api/me/reviewable-hotel-items', token=partner)
    check('org member sees nothing reviewable', s == 200 and data(r) == [], (s, r))

    print('\n=== partner response, no score change ===')
    s, r, _ = call('GET', f'/api/partner/organizations/{org_main}/hotel-reviews', token=partner)
    check('partner lists the review', s == 200 and [x['id'] for x in data(r)['items']] == [review['id']], (s, r))
    s, _, _ = call('GET', f'/api/partner/organizations/{org_main}/hotel-reviews', token=cust)
    check('customer cannot list partner reviews', s == 403, s)
    s, r, _ = call('POST', f'/api/partner/organizations/{org_main}/hotel-reviews/{review["id"]}/response',
                   {'text': 'Tesekkurler'}, partner)
    check('partner response 200', s == 200 and data(r)['partnerResponse'] == 'Tesekkurler' and data(r)['respondedAt'], (s, r))
    s, _, _ = call('POST', f'/api/partner/organizations/{org_main}/hotel-reviews/{review["id"]}/response',
                   {'text': 'x'}, cust)
    check('customer cannot respond', s == 403, s)
    for method in ('PUT', 'PATCH', 'DELETE'):
        s, _, _ = call(method, f'/api/partner/organizations/{org_main}/hotel-reviews/{review["id"]}',
                       {'score': 1}, partner)
        check(f'partner {method} on a review is not routed/allowed', s in (404, 405), s)
    s, r, _ = call('POST', f'/api/partner/organizations/{org_main}/hotel-reviews/{review["id"]}/response',
                   {'text': 'ok', 'score': 1}, partner)
    s, r, _ = call('GET', f'/api/hotels/{hotel}/reviews')
    it = data(r)['items'][0]
    check('score unchanged, response visible publicly', it['score'] == 4 and it['partnerResponse'] == 'ok', it)

    print('\n=== admin moderation ===')
    s, _, _ = call('POST', f'/api/admin/hotel-reviews/{review["id"]}/moderate', {'status': 'HIDDEN', 'reason': 'x'}, partner)
    check('partner cannot moderate', s == 403, s)
    s, r, _ = call('POST', f'/api/admin/hotel-reviews/{review["id"]}/moderate', {'status': 'HIDDEN'}, admin)
    check('reason required -> 400', s == 400, (s, r))
    s, r, _ = call('POST', f'/api/admin/hotel-reviews/{review["id"]}/moderate', {'status': 'HIDDEN', 'reason': 'Uygunsuz icerik'}, admin)
    check('admin hide 200', s == 200 and data(r)['status'] == 'HIDDEN', (s, r))
    s, r, _ = call('GET', f'/api/hotels/{hotel}/reviews')
    page = data(r) or {}
    check('hidden review gone from public list and aggregate', page.get('total') == 0 and page.get('averageScore') is None
          and page.get('reviewCount') == 0, page)
    s, r, _ = call('GET', f'/api/hotels/{hotel}')
    check('hotel aggregate back to null/0', data(r)['averageScore'] is None and data(r)['reviewCount'] == 0, data(r))
    audit = psql('hotel_db', f"SELECT from_status||'>'||to_status||'|'||reason||'|'||(actor_id IS NOT NULL) "
                             f"FROM hotel_review_moderation_events WHERE review_id='{review['id']}'")
    check('moderation audit stored', audit == 'PUBLISHED>HIDDEN|Uygunsuz icerik|true', audit)
    s, r, _ = call('GET', '/api/admin/hotel-reviews?status=HIDDEN', token=admin)
    check('admin list shows hidden review', s == 200 and any(x['id'] == review['id'] for x in data(r)['items']), (s, r))
    s, _, _ = call('GET', '/api/admin/hotel-reviews', token=cust)
    check('customer cannot read admin reviews', s == 403, s)
    s, r, _ = call('POST', f'/api/admin/hotel-reviews/{review["id"]}/moderate', {'status': 'PUBLISHED', 'reason': 'Itiraz kabul'}, admin)
    check('admin can republish', s == 200 and data(r)['status'] == 'PUBLISHED', (s, r))

    failed = [n for n, ok in car.results if not ok]
    print(f'\n{len(car.results) - len(failed)}/{len(car.results)} checks passed')
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    finally:
        car.cleanup()
        pkg.hotel_cleanup()
        if EMPTY['id']:
            psql('hotel_db', f"DELETE FROM hotels WHERE id='{EMPTY['id']}'")
