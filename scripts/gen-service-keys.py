#!/usr/bin/env python3
"""Generate one Ed25519 keypair per service for signed service tokens (X-Service-Token, service-auth-common).

Writes into .env (gitignored), never prints key material:
  WAYLO_SERVICE_PRIVATE_KEY_<SERVICE>  base64 DER PKCS#8, only passed to that service's container
  WAYLO_SERVICE_KEYS_<SERVICE>         base64 DER SubjectPublicKeyInfo, passed to the services it may call
Existing keys are kept (idempotent). --rotate SERVICE replaces one service's pair (redeploy it and its callees).
Usage: python3 scripts/gen-service-keys.py [--rotate SERVICE]
"""
import base64
import os
import pathlib
import subprocess
import sys

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

ROOT = pathlib.Path(__file__).resolve().parent.parent
ENV = ROOT / '.env'
SERVICES = ['auth-service', 'hotel-service', 'bus-service', 'car-rental-service', 'checkin-service', 'crm-service',
            'flight-seat-inventory-service', 'notification-service', 'package-booking-saga-orchestrator',
            'package-booking-service', 'payment-service', 'pricing-service']


def var(service):
    return service.upper().replace('-', '_')


def main():
    rotate = sys.argv[2] if len(sys.argv) == 3 and sys.argv[1] == '--rotate' else None
    if len(sys.argv) > 1 and rotate not in SERVICES:
        raise SystemExit(__doc__)
    ignored = subprocess.run(['git', 'check-ignore', '-q', str(ENV)], cwd=ROOT).returncode == 0
    if not ignored:
        raise SystemExit('.env is not gitignored, refusing to write keys')
    lines = ENV.read_text().splitlines() if ENV.exists() else []
    present = {l.split('=', 1)[0] for l in lines if '=' in l and not l.startswith('#')}
    added = []
    for svc in SERVICES:
        priv_name, pub_name = f'WAYLO_SERVICE_PRIVATE_KEY_{var(svc)}', f'WAYLO_SERVICE_KEYS_{var(svc)}'
        if svc != rotate and priv_name in present and pub_name in present:
            continue
        key = Ed25519PrivateKey.generate()
        priv = base64.b64encode(key.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8,
                                                  serialization.NoEncryption())).decode()
        pub = base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,
                                                             serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
        lines = [l for l in lines if l.split('=', 1)[0] not in (priv_name, pub_name)]
        lines += [f'{priv_name}={priv}', f'{pub_name}={pub}']
        added.append(svc)
    if 'WAYLO_SERVICE_AUTH_MODE' not in present:
        lines.append('WAYLO_SERVICE_AUTH_MODE=dual')
    tmp = ENV.with_suffix('.tmp')
    tmp.write_text('\n'.join(lines) + '\n')
    os.chmod(tmp, 0o600)
    tmp.replace(ENV)
    print(f'service keys: generated for {len(added)} service(s): {", ".join(added) or "none (all present)"}')


if __name__ == '__main__':
    main()
