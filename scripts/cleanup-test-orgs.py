#!/usr/bin/env python3
"""One-off cleanup of organizations left behind by live test scripts and ad hoc walkthroughs.

Dry-run by default (prints counts per name pattern and what would be deleted). `--apply` deletes, in FK order:
hotels and all hotel child rows, car and bus rows, commission rates and unused payout setup rows, support cases,
role assignments, memberships and the organizations. Test users are left alone (they are harmless without an org).

An organization is a deletion candidate only when BOTH hold:
  1. its name matches one of the known test patterns below, and
  2. every member is a live-test user: an e-mail ending in a TEST_DOMAINS entry whose local part does not
     start with a SEED_PREFIXES entry. Members with any other domain (@walkthrough.dev, gmail, ...) make the
     organization protected. An organization without members qualifies by name alone.

Always protected, never deleted, reported as skipped:
  - the named real/seed organizations (PROTECTED_NAMES) and any org with a seed-* member (seed-filo@waylo.test, ...)
  - any org referenced by an order item (package_booking_db), escrow item, payout or payout ledger row
If a protection query fails the script aborts before deleting anything.
Nothing secret is read or printed: it only talks to the waylo-postgres container as the waylo DB user.
"""
import argparse
import pathlib
import re
import subprocess
import sys
from collections import Counter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import org_cleanup  # noqa: E402

# test scripts register '<prefix>-<hex>@example.test'; @waylo.test is used by the seed accounts (seed-*), which stay protected
TEST_DOMAINS = ('@example.test', '@waylo.test')
SEED_PREFIXES = ('seed-',)
PROTECTED_NAMES = {'Waylo Demo Hotels', 'Waylo Ucus Satis'}

# label -> regex on the organization name
PATTERNS = {
    'Org A / Org C (calendar)': r'^Org [A-Z]$',
    'RLS ...': r'^RLS\b',
    'PubProj Org': r'^PubProj Org\b',
    'QH acceptance': r'^QH acceptance\b',
    'Review Org QA': r'^Review Org\b',
    'Media Org A / MediaPub Org': r'^Media(Pub)? Org\b',
    'V6 Constraint Test Org': r'^V6 Constraint Test Org\b',
    'LiveOrg': r'^LiveOrg',
    'Cap N': r'^Cap \d+$',
    'Delegation/Intruder/Owner/Verifier Org': r'^(Delegation|Intruder|Owner|Verifier)\b.*\bOrg\b',
    'can-test-/ord-test-/pay-test/po-test-/po-other-': r'^(can-test|ord-test|pay-test2?|po-test|po-other)-',
    'pkg-hotel-/pp-b-/stay-oth-': r'^(pkg-hotel|pp-b|stay-oth)-',
    'SC Other': r'^SC Other\b',
    'Bus Test / Bus Ind': r'^Bus (Test|Ind)\b',
    'Car Order Test Co': r'^Car Order Test Co\b',
    # e2e spec 13 (partner onboarding) creates this name with an @example.test user; the walkthrough partner of the same
    # name stays protected by its non-test (@walkthrough.dev) member.
    'Kaya Otelcilik (e2e onboarding)': r'^Kaya Otelcilik Ltd\. Sti\.$',
}
COMPILED = {label: re.compile(rx) for label, rx in PATTERNS.items()}
SEP = '\x1f'


def query(db, sql):
    r = subprocess.run(['docker', 'exec', org_cleanup.CONTAINER, 'psql', '-U', org_cleanup.DB_USER, '-d', db, '-tA', '-F', SEP, '-c', sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f'query failed on {db}: {r.stderr.strip()[:200]}')
    return [line.split(SEP) for line in r.stdout.splitlines() if line]


def pattern_of(name):
    for label, rx in COMPILED.items():
        if rx.search(name):
            return label
    return None


def is_test_email(email):
    e = (email or '').lower()
    local = e.split('@', 1)[0]
    return any(e.endswith(d) for d in TEST_DOMAINS) and not any(local.startswith(p) for p in SEED_PREFIXES)


def load_orgs():
    rows = query('auth_db',
                 "SELECT o.id, o.name, o.status, coalesce(string_agg(u.email, ',' ORDER BY u.email), '') "
                 "FROM organizations o LEFT JOIN organization_memberships m ON m.organization_id = o.id "
                 "LEFT JOIN users u ON u.id = m.user_id GROUP BY o.id, o.name, o.status ORDER BY o.created_at")
    return [{'id': r[0], 'name': r[1], 'status': r[2], 'emails': [e for e in r[3].split(',') if e]} for r in rows]


def plan(orgs):
    candidates, protected = [], []
    for o in orgs:
        label = pattern_of(o['name'])
        if o['name'] in PROTECTED_NAMES:
            protected.append((o, 'named protected organization'))
        elif any(e.lower().split('@', 1)[0].startswith(SEED_PREFIXES) for e in o['emails']):
            protected.append((o, 'has a seed account member'))
        elif label is None:
            continue
        elif any(not is_test_email(e) for e in o['emails']):
            protected.append((o, 'has a non test member: ' + ', '.join(sorted({e.split("@", 1)[-1] for e in o["emails"] if not is_test_email(e)}))))
        else:
            candidates.append((o, label))
    money = org_cleanup.has_money_or_orders([o['id'] for o, _ in candidates])  # strict: raises on query failure
    final = []
    for o, label in candidates:
        if o['id'] in money:
            protected.append((o, 'has orders, escrow or payout rows'))
        else:
            final.append((o, label))
    return final, protected


def counts_for(ids):
    if not ids:
        return {}
    q = org_cleanup._in(ids)
    out = {}
    for label, db, sql in (
        ('hotels', 'hotel_db', f'SELECT organization_id, count(*) FROM hotels WHERE organization_id IN ({q}) GROUP BY 1'),
        ('commission rates', 'payment_db', f'SELECT supplier_organization_id, count(*) FROM commission_rates WHERE supplier_organization_id IN ({q}) GROUP BY 1'),
        ('car rows', 'car_rental_db', f'SELECT organization_id, count(*) FROM branches WHERE organization_id IN ({q}) GROUP BY 1'),
        ('bus rows', 'bus_db', f'SELECT organization_id, count(*) FROM buses WHERE organization_id IN ({q}) GROUP BY 1'),
    ):
        out[label] = sum(int(r[1]) for r in query(db, sql))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--apply', action='store_true', help='actually delete (default is a dry-run)')
    args = ap.parse_args()

    orgs = load_orgs()
    final, protected = plan(orgs)
    per_pattern = Counter(label for _, label in final)

    print(f'{len(orgs)} organizations in total, {len(final)} would be deleted, {len(protected)} matched but protected')
    print('\nWould delete, per pattern:')
    for label in PATTERNS:
        if per_pattern[label]:
            print(f'  {per_pattern[label]:4d}  {label}')
    totals = counts_for([o['id'] for o, _ in final])
    print('\nRows under those organizations: ' + ', '.join(f'{k} {v}' for k, v in totals.items()))
    print('\nProtected (skipped):')
    for o, why in protected:
        print(f'  {o["name"]!r} [{o["id"][:8]}] {o["status"]}: {why}')
    if not protected:
        print('  (none)')

    if not args.apply:
        print('\nDry-run only. Re-run with --apply to delete.')
        return 0
    ids = [o['id'] for o, _ in final]
    for i in range(0, len(ids), 20):
        org_cleanup.delete_org_rows(ids[i:i + 20])
    left = {r[0] for r in query('auth_db', f'SELECT id FROM organizations WHERE id IN ({org_cleanup._in(ids)})')} if ids else set()
    print(f'\nDeleted {len(ids) - len(left)} organizations, {len(left)} could not be deleted (a foreign key still references them).')
    for o, _ in final:
        if o['id'] in left:
            print(f'  left: {o["name"]!r} [{o["id"][:8]}]')
    return 0


if __name__ == '__main__':
    sys.exit(main())
