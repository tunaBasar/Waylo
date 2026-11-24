#!/usr/bin/env python3
"""Live acceptance for RM-36 owned-order support cases, through the gateway (stack must run, same fixtures as test-checkin.py).

Customer A opens a case on A's own flight order. Customer B cannot open one on that order nor read A's case.
The partner (owner of the seller org of the item) sees and answers it, another org's owner cannot.
The admin lists it by status. The customer closes it, closed cases take no more messages.
Public capabilities: no live chat, no channels, no hours. Exit code 1 on any FAIL.
Needs the gateway routes for the crm-service support paths and the public /api/support/capabilities path.
"""
import datetime
import importlib.util
import pathlib
import secrets
import sys
import time
import uuid

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ci', HERE / 'test-checkin.py')
ci = importlib.util.module_from_spec(spec)
sys.modules['ci'] = ci
spec.loader.exec_module(ci)
t = ci.t
call, data, check = t.call, t.data, t.check
case_ids = []


def mail_rows(email, subject):
    """notifications rows (notification-service) for this recipient and subject, polled up to 30s."""
    q = f"SELECT count(*) FROM notifications WHERE recipient = '{email}' AND subject = '{subject}'"
    for _ in range(15):
        n = t.psql('notification_db', q).strip()
        if n not in ('', '0'):
            return int(n)
        time.sleep(2)
    return 0


def main():
    admin_email, admin_pw = t.env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'), t.env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')
    sfx = uuid.uuid4().hex[:10]
    emails = {k: f'sc-{k}-{sfx}@example.test' for k in ('a', 'b', 'p', 'o')}
    pws = {e: 'Pw1' + secrets.token_urlsafe(16) for e in emails.values()}
    for k in ('p', 'a', 'b', 'o'):
        t.register(emails[k], pws[emails[k]])
    admin = t.login(admin_email, admin_pw)
    tok = {k: t.login(emails[k], pws[emails[k]]) for k in emails}
    seller = t.ensure_seller(admin, tok['p'])
    # The seller org is reused across runs: make this run's partner an owner of it (as test-flight-order.py does)
    if t.psql('auth_db', f"SELECT count(*) FROM organization_memberships m JOIN users u ON u.id = m.user_id "
                         f"WHERE u.email = '{emails['p']}' AND m.organization_id = '{seller}'") in ('', '0'):
        t.psql('auth_db', f"INSERT INTO organization_memberships (user_id, organization_id) "
                          f"SELECT id, '{seller}' FROM users WHERE email = '{emails['p']}'")
        t.psql('auth_db', f"INSERT INTO role_assignments (membership_id, organization_id, role, scope_type, scope_id) "
                          f"SELECT m.id, '{seller}', 'ORGANIZATION_OWNER', 'ORGANIZATION', '{seller}' FROM organization_memberships m "
                          f"JOIN users u ON u.id = m.user_id WHERE u.email = '{emails['p']}' AND m.organization_id = '{seller}'")
        tok['p'] = t.login(emails['p'], pws[emails['p']])
    rates = (call('GET', '/api/admin/commission-rates', token=admin)[1].get('data')) or []
    if not any(r.get('vertical') == 'FLIGHT' and str(r.get('supplierOrganizationId')) == seller for r in rates):
        call('POST', '/api/admin/commission-rates', {'vertical': 'FLIGHT', 'supplierOrganizationId': seller, 'rate': 0.05}, admin)

    print('\n=== capabilities (public, no token) ===')
    s, b = call('GET', '/api/support/capabilities')
    cap = data((s, b)) or {}
    check('capabilities 200 without token', s == 200, s)
    check('no live chat, no channels, no hours, decision pending',
          cap.get('liveChat') is False and cap.get('channels') == [] and cap.get('hours') is None and cap.get('decisionPending') is True, cap)

    print('\n=== fixture: customer A order ===')
    now = datetime.datetime.now(datetime.timezone.utc).replace(second=0, microsecond=0)
    fno = 'WS' + str(secrets.randbelow(9000) + 1000)
    t.flight_numbers.append(fno)
    dep = now + datetime.timedelta(days=5)
    check('fixture flight created', ci.make_flight(admin, fno, dep))
    order, item = ci.order_and_pay(tok['a'], fno, dep, '1A')
    check('customer A order paid', bool(item))

    print('\n=== customer opens a case ===')
    s, b = call('POST', '/api/me/support-cases', {'orderId': order, 'orderItemId': item, 'subject': 'Bag question', 'message': 'Can I add a bag?'}, tok['a'])
    case = data((s, b)) or {}
    cid = case.get('id')
    case_ids.append(cid)
    check('A opens case 201 OPEN with supplier org', s == 201 and case.get('status') == 'OPEN' and case.get('supplierOrganizationId') == seller, (s, b))
    check('first message is the customer message', len(case.get('messages') or []) == 1 and case['messages'][0]['authorRole'] == 'CUSTOMER', case)
    s, b = call('POST', '/api/me/support-cases', {'orderId': order, 'subject': 'x', 'message': 'y'}, tok['b'])
    check('B cannot open a case on A order (404)', s == 404, s)
    s, b = call('POST', '/api/me/support-cases', {'orderId': str(uuid.uuid4()), 'subject': 'x', 'message': 'y'}, tok['a'])
    check('unknown order 404', s == 404, s)
    s, b = call('POST', '/api/me/support-cases', {'orderId': order, 'subject': '', 'message': 'y'}, tok['a'])
    check('blank subject 400', s == 400, s)
    s, _ = call('POST', '/api/me/support-cases', {'orderId': order, 'subject': 'x', 'message': 'y'})
    check('no token 401', s == 401, s)

    print('\n=== customer B isolation ===')
    s, b = call('GET', f'/api/me/support-cases/{cid}', token=tok['b'])
    check('B cannot read A case (404)', s == 404, s)
    s, b = call('GET', '/api/me/support-cases', token=tok['b'])
    check('B list does not contain it', s == 200 and all(c['id'] != cid for c in data((s, b))), s)
    s, b = call('POST', f'/api/me/support-cases/{cid}/messages', {'body': 'hijack'}, tok['b'])
    check('B cannot post to A case (404)', s == 404, s)
    s, b = call('POST', f'/api/me/support-cases/{cid}/close', None, tok['b'])
    check('B cannot close A case (404)', s == 404, s)
    s, b = call('GET', '/api/me/support-cases', token=tok['a'])
    check('A list contains the case', s == 200 and any(c['id'] == cid for c in data((s, b))), s)

    print('\n=== partner ===')
    s, b = call('GET', f'/api/partner/organizations/{seller}/support-cases', token=tok['p'])
    check('seller org owner sees the case', s == 200 and any(c['id'] == cid for c in data((s, b))), s)
    s, b = call('GET', f'/api/partner/organizations/{seller}/support-cases', token=tok['o'])
    check('user outside the seller org is denied (403)', s == 403, s)
    s, b = call('GET', f'/api/partner/organizations/{seller}/support-cases', token=tok['a'])
    check('customer A is denied on the seller org (403)', s == 403, s)
    other = call('POST', '/api/organizations', {'name': f'SC Other {sfx}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, tok['o'])
    other_org = ((other[1] or {}).get('data') or {}).get('id')
    if other_org:
        call('POST', f'/api/admin/organizations/{other_org}/approve', token=admin)
        s, b = call('GET', f'/api/partner/organizations/{other_org}/support-cases', token=tok['o'])
        check('other org sees none of it', s == 200 and all(c['id'] != cid for c in data((s, b))), s)
        s, b = call('POST', f'/api/partner/organizations/{other_org}/support-cases/{cid}/messages', {'body': 'not mine'}, tok['o'])
        check('other org cannot answer (404)', s == 404, s)
    else:
        check('other org created', False, other)
    s, b = call('POST', f'/api/partner/organizations/{seller}/support-cases/{cid}/messages', {'body': 'Yes, add it in the app.'}, tok['p'])
    ans = data((s, b)) or {}
    check('partner reply 201 -> ANSWERED', s == 201 and ans.get('status') == 'ANSWERED', (s, b))
    check('thread has 2 messages, partner second', [m['authorRole'] for m in ans.get('messages') or []] == ['CUSTOMER', 'PARTNER'], ans)
    check('partner reply e-mails the customer', mail_rows(emails['a'], 'Destek talebiniz yanıtlandı') >= 1)
    s, b = call('GET', f'/api/me/support-cases/{cid}', token=tok['a'])
    check('customer sees the partner answer', s == 200 and data((s, b))['status'] == 'ANSWERED' and len(data((s, b))['messages']) == 2, s)

    print('\n=== admin ===')
    s, b = call('GET', '/api/admin/support-cases?status=ANSWERED', token=admin)
    check('admin lists it by status', s == 200 and any(c['id'] == cid for c in data((s, b))), s)
    s, b = call('GET', '/api/admin/support-cases?status=OPEN', token=admin)
    check('not in the OPEN filter', s == 200 and all(c['id'] != cid for c in data((s, b))), s)
    s, _ = call('GET', '/api/admin/support-cases', token=tok['a'])
    check('customer cannot use the admin list (403)', s == 403, s)
    s, _ = call('GET', '/api/admin/support-cases', token=tok['p'])
    check('partner cannot use the admin list (403)', s == 403, s)

    print('\n=== customer follow up and close ===')
    s, b = call('POST', f'/api/me/support-cases/{cid}/messages', {'body': 'Thanks!'}, tok['a'])
    check('customer follow-up reopens (OPEN)', s == 201 and data((s, b))['status'] == 'OPEN', (s, b))
    time.sleep(3)
    # only support e-mails count: the customer's flight order can produce its own notification rows
    n_answered = t.psql('notification_db', f"SELECT count(*) FROM notifications WHERE recipient = '{emails['a']}' "
                        "AND subject IN ('Destek talebiniz yanıtlandı', 'Destek talebiniz kapatıldı')").strip()
    check('customer own message creates no e-mail row', n_answered == '1', n_answered)
    s, b = call('POST', f'/api/admin/support-cases/{cid}/close', None, admin)
    check('admin closes -> CLOSED', s == 200 and data((s, b))['status'] == 'CLOSED', (s, b))
    check('admin close e-mails the customer', mail_rows(emails['a'], 'Destek talebiniz kapatıldı') >= 1)
    s, b = call('POST', f'/api/me/support-cases/{cid}/close', None, tok['a'])
    check('customer close of closed case is idempotent -> CLOSED', s == 200 and data((s, b))['status'] == 'CLOSED', (s, b))
    s, b = call('POST', f'/api/me/support-cases/{cid}/messages', {'body': 'more'}, tok['a'])
    check('closed case takes no message (422)', s == 422, (s, b))
    s, b = call('POST', f'/api/partner/organizations/{seller}/support-cases/{cid}/messages', {'body': 'late'}, tok['p'])
    check('partner cannot answer a closed case (422)', s == 422, s)
    n = t.psql('crm_db', f"SELECT count(*) FROM support_messages WHERE case_id='{cid}'")
    check('3 messages stored', n.strip() == '3', n)


def cleanup_cases():
    """Fixture rows only. The append-only trigger is disabled just for this delete."""
    ids = ','.join(f"'{c}'" for c in case_ids if c)
    if ids:
        t.psql('crm_db', 'ALTER TABLE support_messages DISABLE TRIGGER USER; '
                         f'DELETE FROM support_messages WHERE case_id IN ({ids}); '
                         'ALTER TABLE support_messages ENABLE TRIGGER USER; '
                         f'DELETE FROM support_cases WHERE id IN ({ids});')


if __name__ == '__main__':
    try:
        main()
    finally:
        for fn in (cleanup_cases, t.cleanup):
            try:
                fn()
            except Exception as e:  # noqa: BLE001
                print('cleanup warning:', e)
    failed = [n for n, ok in t.results if not ok]
    if failed:
        print('FAILED: ' + ', '.join(failed))
        sys.exit(1)
    print(f'ALL {len(t.results)} CHECKS PASSED')
