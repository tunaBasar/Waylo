"""Test helper: sign an X-Service-Token like service-auth-common does (Ed25519 JWT, iss/aud/iat/exp/jti).

Keys come from .env (scripts/gen-service-keys.py) and are never printed. Used by test scripts that call
/internal endpoints directly on 127.0.0.1 service ports, impersonating the real caller service.
"""
import base64
import json
import pathlib
import time
import uuid

from cryptography.hazmat.primitives import serialization

ROOT = pathlib.Path(__file__).resolve().parent.parent


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip().strip('"').strip("'")
    return None


def _b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b'=').decode()


def sign(issuer, audience, ttl=30, now=None, key_of=None):
    """Signed token for issuer -> audience. key_of: service whose private key signs (default issuer)."""
    raw = env_value('WAYLO_SERVICE_PRIVATE_KEY_' + (key_of or issuer).upper().replace('-', '_'))
    if not raw:
        raise SystemExit('service keys missing in .env: run python3 scripts/gen-service-keys.py')
    key = serialization.load_der_private_key(base64.b64decode(raw), password=None)
    iat = int(now if now is not None else time.time())
    header = _b64(json.dumps({'alg': 'EdDSA', 'typ': 'JWT'}, separators=(',', ':')).encode())
    claims = _b64(json.dumps({'iss': issuer, 'aud': audience, 'iat': iat, 'exp': iat + ttl,
                              'jti': str(uuid.uuid4())}, separators=(',', ':')).encode())
    signing_input = f'{header}.{claims}'
    return signing_input + '.' + _b64(key.sign(signing_input.encode()))


def headers(issuer, audience):
    """Headers for a direct /internal call: signed token plus the legacy token (ignored in strict mode)."""
    h = {'X-Service-Token': sign(issuer, audience)}
    legacy = env_value('INTERNAL_API_TOKEN')
    if legacy:
        h['X-Internal-Token'] = legacy
    return h


class SignedHeaders(dict):
    """Header mapping that signs a fresh token on every copy/iteration, for scripts reusing one headers dict."""

    def __init__(self, issuer, audience):
        super().__init__()
        self.issuer, self.audience = issuer, audience

    def _fresh(self):
        return headers(self.issuer, self.audience)

    def items(self):
        return self._fresh().items()

    def keys(self):
        return self._fresh().keys()

    def __iter__(self):
        return iter(self._fresh())

    def __getitem__(self, k):
        return self._fresh()[k]

    def __len__(self):
        return len(self._fresh())
