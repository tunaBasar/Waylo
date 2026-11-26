"""Shared cleanup of test organizations and everything under them (all services), in FK order.

Used by the live test scripts (in a finally block) and by scripts/cleanup-test-orgs.py.
Never prints secrets. Statements are best effort: a failing one is skipped so the rest still runs.
"""
import subprocess

DB_USER = 'waylo'
CONTAINER = 'waylo-postgres'


def psql(db, sql):
    r = subprocess.run(['docker', 'exec', CONTAINER, 'psql', '-U', DB_USER, '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ''


def psql_strict(db, sql):
    """Like psql but raises: protection checks must never read a failed query as 'nothing found'."""
    r = subprocess.run(['docker', 'exec', CONTAINER, 'psql', '-U', DB_USER, '-d', db, '-tAc', sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f'query failed on {db}: {r.stderr.strip()[:200]}')
    return r.stdout.strip()


def _in(ids):
    return ','.join("'" + str(i) + "'" for i in ids)


def has_money_or_orders(org_ids):
    """Orgs that must never be deleted: an order item, escrow row, payout or payout ledger row references them."""
    if not org_ids:
        return set()
    q = _in(org_ids)
    found = set()
    for db, sql in (
        ('package_booking_db', f'SELECT DISTINCT supplier_organization_id FROM order_items WHERE supplier_organization_id IN ({q})'),
        ('payment_db', f'SELECT DISTINCT supplier_organization_id FROM escrow_items WHERE supplier_organization_id IN ({q})'),
        ('payment_db', f'SELECT DISTINCT organization_id FROM partner_payouts WHERE organization_id IN ({q})'),
        ('payment_db', f'SELECT DISTINCT organization_id FROM payout_ledger WHERE organization_id IN ({q})'),
    ):
        found.update(x for x in psql_strict(db, sql).split() if x)
    return found


HOTEL_CHILDREN = (
    'hotel_publication_request_media', 'hotel_media_decisions', 'hotel_media', 'hotel_publication_decisions',
)
HOTEL_CHILDREN_2 = (
    'hotel_publication_requests', 'hotel_content_revisions', 'hotel_holds', 'hotel_quote_nights', 'hotel_quotes',
    'inventory_adjustments', 'daily_rates', 'daily_inventory', 'rate_plans', 'room_types', 'rooms',
)


def delete_hotels(hotel_ids):
    if not hotel_ids:
        return
    h = _in(hotel_ids)
    for t in HOTEL_CHILDREN:
        psql('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({h})')
    psql('hotel_db', f'UPDATE hotels SET published_revision_id = NULL WHERE id IN ({h})')
    psql('hotel_db', f'DELETE FROM hotel_review_moderation_events WHERE review_id IN (SELECT id FROM hotel_reviews WHERE hotel_id IN ({h}))')
    psql('hotel_db', f'DELETE FROM hotel_reviews WHERE hotel_id IN ({h})')
    for t in HOTEL_CHILDREN_2:
        if t == 'hotel_quote_nights':
            psql('hotel_db', f'DELETE FROM hotel_quote_nights WHERE quote_id IN (SELECT id FROM hotel_quotes WHERE hotel_id IN ({h}))')
        else:
            psql('hotel_db', f'DELETE FROM {t} WHERE hotel_id IN ({h})')
    psql('hotel_db', f'DELETE FROM hotels WHERE id IN ({h})')


def delete_org_rows(org_ids):
    """All rows scoped to the organizations, then the organizations themselves. Caller decides which ids are safe."""
    if not org_ids:
        return
    o = _in(org_ids)
    # hotel-service
    hotels = psql('hotel_db', f'SELECT id FROM hotels WHERE organization_id IN ({o})').split()
    delete_hotels(hotels)
    # car-rental-service
    for t in ('rentals', 'documents', 'one_way_routes', 'category_rates', 'vehicles', 'branches'):
        psql('car_rental_db', f'DELETE FROM {t} WHERE organization_id IN ({o})')
    # bus-service
    psql('bus_db', f'DELETE FROM tickets WHERE trip_id IN (SELECT id FROM trips WHERE organization_id IN ({o}))')
    psql('bus_db', f'DELETE FROM bookings WHERE organization_id IN ({o})')
    for t in ('trip_fares', 'trip_stops'):
        psql('bus_db', f'DELETE FROM {t} WHERE trip_id IN (SELECT id FROM trips WHERE organization_id IN ({o}))')
    for t in ('trips', 'documents', 'buses'):
        psql('bus_db', f'DELETE FROM {t} WHERE organization_id IN ({o})')
    # payment-service (money rows are excluded by has_money_or_orders before this point)
    psql('payment_db', f'DELETE FROM commission_rates WHERE supplier_organization_id IN ({o})')
    for t in ('partner_bank_account_changes', 'partner_bank_accounts', 'payout_wallets', 'supplier_payout_accounts'):
        psql('payment_db', f'DELETE FROM {t} WHERE organization_id IN ({o})')
    # crm-service: support messages are append only (trigger disabled just for this delete)
    psql('crm_db', 'ALTER TABLE support_messages DISABLE TRIGGER USER; '
                   f'DELETE FROM support_messages WHERE case_id IN (SELECT id FROM support_cases WHERE supplier_organization_id IN ({o})); '
                   'ALTER TABLE support_messages ENABLE TRIGGER USER; '
                   f'DELETE FROM support_cases WHERE supplier_organization_id IN ({o});')
    # auth-service
    psql('auth_db', f'DELETE FROM role_assignments WHERE organization_id IN ({o})')
    psql('auth_db', f'DELETE FROM organization_memberships WHERE organization_id IN ({o})')
    psql('auth_db', f'DELETE FROM organizations WHERE id IN ({o})')


def delete_users(emails):
    if not emails:
        return
    q = _in(emails)
    psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
    psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
    psql('auth_db', f'DELETE FROM role_assignments WHERE membership_id IN '
                    f'(SELECT m.id FROM organization_memberships m JOIN users u ON u.id = m.user_id WHERE u.email IN ({q}))')
    psql('auth_db', f'DELETE FROM organization_memberships WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
    psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')


def cleanup_test_orgs(org_ids, emails=()):
    """For live test scripts (call in a finally block). Orgs that already carry orders or money rows are skipped and reported."""
    org_ids = [o for o in org_ids if o]
    try:
        protected = has_money_or_orders(org_ids)
        if protected:
            print('cleanup: kept organizations that have orders or payout rows:', ', '.join(sorted(protected)))
        delete_org_rows([o for o in org_ids if o not in protected])
        delete_users([e for e in emails if e])
    except Exception as e:  # noqa: BLE001 - cleanup must never mask the test result
        print('cleanup warning:', e)
