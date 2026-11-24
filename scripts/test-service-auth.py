#!/usr/bin/env python3
"""Service-to-service auth acceptance (service-auth-common, X-Service-Token).

Calls /internal endpoints directly on 127.0.0.1-bound service ports:
  - no credential -> 401
  - the retired legacy X-Internal-Token alone -> 401
  - token signed for another audience -> 401
  - expired token, tampered token, caller not in the allowlist, issuer signing with another service's key -> rejected
  - valid token from an allowed caller -> accepted
  - the same token used twice -> second call 401 (replay protection)
Keys are read from .env and never printed. Standard library + cryptography.
"""
import base64
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import service_token  # noqa: E402

P = F = 0
PB = 'http://127.0.0.1:8085'
AUTH = 'http://127.0.0.1:8092'
UNKNOWN_ITEM = '/internal/orders/items/00000000-0000-0000-0000-000000000000'


def ok(c, label):
    global P, F
    print(('PASS: ' if c else 'FAIL: ') + label)
    if c:
        P += 1
    else:
        F += 1


def call(base, path, headers=None):
    r = urllib.request.Request(base + path, headers=headers or {}, method='GET')
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code


def tok(iss, aud, **kw):
    return {'X-Service-Token': service_token.sign(iss, aud, **kw)}


def main():
    legacy = {'X-Internal-Token': 'l' * 40}
    # A passing call reaches the controller: unknown item -> 404, which proves the auth filter let it through.
    passed = lambda s: s in (200, 404)

    ok(call(PB, UNKNOWN_ITEM) == 401, 'no credential -> 401')
    s = call(PB, UNKNOWN_ITEM, legacy)
    ok(s == 401, f'legacy X-Internal-Token rejected ({s})')
    ok(call(PB, UNKNOWN_ITEM, {'X-Internal-Token': 'x' * 40}) == 401, 'wrong legacy token -> 401')

    s = call(PB, UNKNOWN_ITEM, tok('checkin-service', 'payment-service'))
    ok(s == 401, f'token for another aud (payment-service) -> 401 ({s})')
    s = call(PB, UNKNOWN_ITEM, tok('checkin-service', 'package-booking-service', now=time.time() - 300))
    ok(s == 401, f'expired token -> 401 ({s})')
    t = service_token.sign('payment-service', 'package-booking-service')
    h, c, sig = t.split('.')
    claims = json.loads(base64.urlsafe_b64decode(c + '=' * (-len(c) % 4)))
    claims['iss'] = 'checkin-service'
    forged = h + '.' + base64.urlsafe_b64encode(json.dumps(claims).encode()).rstrip(b'=').decode() + '.' + sig
    s = call(PB, UNKNOWN_ITEM, {'X-Service-Token': forged})
    ok(s == 401, f'tampered claims (iss swapped) -> 401 ({s})')
    s = call(PB, UNKNOWN_ITEM, tok('checkin-service', 'package-booking-service', key_of='payment-service'))
    ok(s == 401, f'checkin-service identity signed with payment-service key -> 401 ({s})')
    s = call(PB, UNKNOWN_ITEM, tok('payment-service', 'package-booking-service'))
    ok(s == 403, f'valid token, caller not in allowlist (payment-service on /internal/orders/items) -> 403 ({s})')
    s = call(PB, UNKNOWN_ITEM, tok('crm-service', 'package-booking-service'))
    ok(s == 401, f'issuer whose key the receiver does not hold (crm-service) -> 401 ({s})')

    s = call(PB, UNKNOWN_ITEM, tok('checkin-service', 'package-booking-service'))
    ok(passed(s), f'valid token from allowed caller checkin-service -> passes auth ({s})')
    s = call(PB, UNKNOWN_ITEM, tok('hotel-service', 'package-booking-service'))
    ok(passed(s), f'valid token from allowed caller hotel-service -> passes auth ({s})')

    # Replay: the same token used twice, the second call must be rejected (jti remembered until exp + skew).
    replay = tok('checkin-service', 'package-booking-service')
    first = call(PB, UNKNOWN_ITEM, replay)
    second = call(PB, UNKNOWN_ITEM, replay)
    ok(passed(first) and second == 401, f'same token used twice: first passes ({first}), replay -> 401 ({second})')

    # Second service: auth-service /internal/organizations (hotel-service and payment-service allowed, checkin-service not).
    s = call(AUTH, '/internal/organizations/active-ids', tok('hotel-service', 'auth-service'))
    ok(s == 200, f'auth-service: hotel-service -> /internal/organizations 200 ({s})')
    s = call(AUTH, '/internal/organizations/active-ids', tok('payment-service', 'auth-service'))
    ok(s == 200, f'auth-service: payment-service (payout balance) -> /internal/organizations 200 ({s})')
    s = call(AUTH, '/internal/organizations/active-ids', tok('checkin-service', 'auth-service'))
    ok(s in (401, 403), f'auth-service: checkin-service not allowed on /internal/organizations -> 401/403 ({s})')
    s = call(AUTH, '/internal/organizations/active-ids', tok('hotel-service', 'hotel-service'))
    ok(s == 401, f'auth-service: token with aud=hotel-service -> 401 ({s})')

    print(f'\n{P} passed, {F} failed')
    sys.exit(1 if F else 0)


if __name__ == '__main__':
    main()
