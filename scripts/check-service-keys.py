#!/usr/bin/env python3
"""Static check: every service-auth caller a receiver allows must also get that caller's public key in compose.

A receiver verifies signed service tokens with WAYLO_SERVICE_KEYS_<CALLER>. When a caller is added to
`waylo.service-auth.routes[].callers` in the receiver's application.yml but the key env line is missing from the
receiver's docker-compose service, every call is rejected with "unknown issuer" (401) at runtime while all unit
tests stay green. Found live on 2026-11-26 (notification-service -> auth-service partner contacts).

Exit code 1 and one line per problem; last line is the summary (pass-unit.sh prints it).
"""
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent


def env_keys(service: dict) -> set:
    env = (service or {}).get('environment') or {}
    if isinstance(env, dict):
        return set(env.keys())
    return {str(e).split('=', 1)[0] for e in env}


def main() -> int:
    compose = yaml.safe_load((ROOT / 'docker-compose.yml').read_text()) or {}
    services = compose.get('services') or {}
    problems = []
    for app in sorted(ROOT.glob('*/src/main/resources/application.yml')):
        name = app.relative_to(ROOT).parts[0]
        try:
            conf = yaml.safe_load(app.read_text()) or {}
        except yaml.YAMLError as e:
            problems.append(f'{name}: application.yml does not parse ({e.__class__.__name__})')
            continue
        routes = ((conf.get('waylo') or {}).get('service-auth') or {}).get('routes') or []
        callers = sorted({c for r in routes for c in (r.get('callers') or [])})
        if not callers or name not in services:
            continue
        have = env_keys(services[name])
        for caller in callers:
            key = 'WAYLO_SERVICE_KEYS_' + caller.upper().replace('-', '_')
            if key not in have:
                problems.append(f'{name}: allows caller {caller} but docker-compose passes no {key}')
    for p in problems:
        print(p)
    print(f'{len(problems)} problem(s)')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
