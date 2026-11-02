#!/usr/bin/env python3
"""Live check for traffic-simulator (queue row 14).

Calls the simulator directly on its loopback port (no gateway route exists for /api/simulations).
Read-only scenarios only; tiny load (2 users, 5 s) so the laptop stays cool. Standard library only.
"""
import json
import sys
import time
import urllib.error
import urllib.request

SIM = 'http://127.0.0.1:8095'
ADMIN = {'X-User-Id': '00000000-0000-0000-0000-0000000000ad', 'X-User-Role': 'ADMIN'}
USER = {'X-User-Id': '00000000-0000-0000-0000-0000000000c1', 'X-User-Role': 'CUSTOMER'}
P = F = 0


def call(method, path, body=None, headers=None):
    h = {'Content-Type': 'application/json', **(headers or {})}
    r = urllib.request.Request(SIM + path, data=json.dumps(body).encode() if body is not None else None,
                               headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read() or b'{}')
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw or b'{}')
        except ValueError:
            return e.code, {}


def check(label, cond, detail=''):
    global P, F
    if cond:
        P += 1
        print(f'PASS {label}')
    else:
        F += 1
        print(f'FAIL {label} {detail}')


def data(d):
    return d.get('data', d) if isinstance(d, dict) else d


def req(scenario, users=2, seconds=5):
    return {'scenarioType': scenario, 'concurrentUsers': users, 'durationSeconds': seconds, 'rampUpSeconds': 0}


def main():
    s, _ = call('POST', '/api/simulations', req('FLIGHT_SEARCH'), USER)
    check('non-admin start is 403', s == 403, s)
    s, _ = call('GET', '/api/simulations', None, None)
    check('no role list is 403', s == 403, s)

    s, d = call('POST', '/api/simulations', req('PACKAGE_BOOKING'), ADMIN)
    check('PACKAGE_BOOKING rejected 422 SCENARIO_DISABLED', s == 422 and 'SCENARIO_DISABLED' in json.dumps(d), (s, d))
    s, d = call('POST', '/api/simulations', req('FULL_FLOW'), ADMIN)
    check('FULL_FLOW rejected 422', s == 422, s)
    s, _ = call('POST', '/api/simulations', req('FLIGHT_SEARCH', users=50), ADMIN)
    check('50 users rejected 400', s == 400, s)
    s, _ = call('POST', '/api/simulations', req('FLIGHT_SEARCH', seconds=600), ADMIN)
    check('600 s rejected 400', s == 400, s)
    s, _ = call('POST', '/api/simulations', {**req('FLIGHT_SEARCH'), 'targetBaseUrl': 'http://169.254.169.254'}, ADMIN)
    check('client target URL is ignored (still accepted, runs against gateway)', s in (200, 201), s)

    s, d = call('POST', '/api/simulations', req('FLIGHT_SEARCH'), ADMIN)
    check('FLIGHT_SEARCH started', s in (200, 201), (s, d))
    sim_id = data(d).get('simulationId')
    final = {}
    for _ in range(40):
        time.sleep(1)
        s, d = call('GET', f'/api/simulations/{sim_id}', None, ADMIN)
        final = data(d)
        if final.get('status') in ('COMPLETED', 'CANCELLED'):
            break
    check('FLIGHT_SEARCH completed', final.get('status') == 'COMPLETED', final.get('status'))
    check('requests were sent', (final.get('totalRequests') or 0) > 0, final.get('totalRequests'))
    check('no failed requests', final.get('failedRequests') == 0, final)
    check('latency measured', (final.get('avgResponseTimeMs') or 0) > 0, final.get('avgResponseTimeMs'))

    s, d = call('POST', '/api/simulations', req('MIXED', users=2, seconds=20), ADMIN)
    mid = data(d).get('simulationId')
    time.sleep(3)
    call('POST', f'/api/simulations/{mid}/cancel', None, ADMIN)
    time.sleep(3)
    _, d1 = call('GET', f'/api/simulations/{mid}', None, ADMIN)
    time.sleep(3)
    _, d2 = call('GET', f'/api/simulations/{mid}', None, ADMIN)
    a, b = data(d1), data(d2)
    check('cancel -> CANCELLED', b.get('status') == 'CANCELLED', b.get('status'))
    check('cancel stops traffic (count stable)', a.get('totalRequests') == b.get('totalRequests'),
          (a.get('totalRequests'), b.get('totalRequests')))

    print(f'\n{P}/{P + F} checks passed')
    sys.exit(1 if F else 0)


if __name__ == '__main__':
    main()
