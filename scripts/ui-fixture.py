#!/usr/bin/env python3
"""Browser walkthrough fixture: one customer and one partner owner (with an approved
org + hotel), plus the bootstrap admin from .env. Writes credentials to a 0600 file in
$JCODE_SCRATCH_DIR (never printed) and serves a one-shot JS login snippet per account on
127.0.0.1:18767 for the browser tool.

  python3 scripts/ui-fixture.py create          # create fixture
  python3 scripts/ui-fixture.py serve customer  # serve login snippet (customer|owner|admin)
  python3 scripts/ui-fixture.py cleanup         # delete everything it created
"""
import http.server
import json
import os
import pathlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:8080'
ROOT = pathlib.Path(__file__).resolve().parent.parent
STATE = pathlib.Path(os.environ.get('JCODE_SCRATCH_DIR', '/tmp')) / 'ui-fixture.json'


def env_value(key):
    for line in (ROOT / '.env').read_text().splitlines():
        if line.startswith(key + '='):
            return line.split('=', 1)[1].strip()
    return None


def call(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                 method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, None


def must(method, path, body=None, token=None):
    s, b = call(method, path, body, token)
    if s not in (200, 201, 204):
        sys.exit(f'{method} {path} -> {s} {b}')
    return (b or {}).get('data', b)


def psql(db, sql):
    subprocess.run(['docker', 'exec', 'waylo-postgres', 'psql', '-U', 'waylo', '-d', db, '-tAc', sql],
                   check=True, capture_output=True)


def register(prefix, first):
    email = f'{prefix}-{uuid.uuid4().hex[:10]}@example.test'
    pw = 'Ui1' + secrets.token_urlsafe(16)
    must('POST', '/api/auth/register', {'firstName': first, 'lastName': 'Test', 'email': email, 'password': pw})
    psql('auth_db', f"UPDATE users SET email_verified = true WHERE email = '{email}'")
    time.sleep(13)  # gateway register limit 5/min
    return {'email': email, 'password': pw}


def create():
    state = {'accounts': {}, 'orgId': None, 'hotelId': None}
    state['accounts']['admin'] = {'email': env_value('WAYLO_BOOTSTRAP_ADMIN_EMAIL'),
                                  'password': env_value('WAYLO_BOOTSTRAP_ADMIN_PASSWORD')}
    state['accounts']['customer'] = register('ui-customer', 'Musteri')
    state['accounts']['owner'] = register('ui-owner', 'Isletme')
    STATE.write_text(json.dumps(state))
    STATE.chmod(0o600)
    owner_tok = must('POST', '/api/auth/login', state['accounts']['owner'])['token']
    admin_tok = must('POST', '/api/auth/login', state['accounts']['admin'])['token']
    org = must('POST', '/api/organizations', {'name': f'UI Test Oteli {uuid.uuid4().hex[:4]}', 'businessType': 'HOTEL', 'legalType': 'COMPANY'}, owner_tok)
    state['orgId'] = org['id']
    STATE.write_text(json.dumps(state))
    hotel = must('POST', '/api/hotels', {'name': 'UI Test Hotel', 'city': 'Antalya', 'country': 'TR',
                                         'address': 'Test only', 'starRating': 4, 'organizationId': org['id']},
                 owner_tok)
    state['hotelId'] = hotel['id']
    STATE.write_text(json.dumps(state))
    must('POST', f"/api/admin/organizations/{org['id']}/approve", token=admin_tok)
    print('fixture ready (credentials in', STATE, 'mode 0600)')


def serve(role):
    acc = json.loads(STATE.read_text())['accounts'][role]
    js = ("(()=>{const set=(s,v)=>{const el=document.querySelector(s);"
          "Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);"
          "el.dispatchEvent(new Event('input',{bubbles:true}));};"
          f"set('input[type=email]',{json.dumps(acc['email'])});set('input[type=password]',{json.dumps(acc['password'])});"
          "document.querySelector('form button[type=submit],form button.w-full').click();return 'submitted'})()").encode()

    class H(http.server.BaseHTTPRequestHandler):
        done = False

        def log_message(self, *a):
            pass

        def do_GET(self):
            self.send_response(200)
            self.send_header('Access-Control-Allow-Origin', 'http://localhost:3000')
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            self.wfile.write(js)
            H.done = True

    srv = http.server.HTTPServer(('127.0.0.1', 18767), H)
    srv.timeout = 120
    while not H.done:
        srv.handle_request()


def cleanup():
    if not STATE.exists():
        return print('nothing to clean')
    st = json.loads(STATE.read_text())
    if st.get('hotelId'):
        psql('hotel_db', f"DELETE FROM hotels WHERE id = '{st['hotelId']}'")
    if st.get('orgId'):
        o = st['orgId']
        psql('auth_db', f"DELETE FROM role_assignments WHERE organization_id = '{o}'")
        psql('auth_db', f"DELETE FROM organization_memberships WHERE organization_id = '{o}'")
        psql('auth_db', f"DELETE FROM organizations WHERE id = '{o}'")
    emails = [a['email'] for r, a in st['accounts'].items() if r != 'admin']
    q = ','.join("'" + e + "'" for e in emails)
    psql('crm_db', f'DELETE FROM customers WHERE email IN ({q})')
    psql('auth_db', f'DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email IN ({q}))')
    psql('auth_db', f'DELETE FROM users WHERE email IN ({q})')
    STATE.unlink()
    print('cleaned')


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else ''
    if cmd == 'create':
        create()
    elif cmd == 'serve' and len(sys.argv) > 2:
        serve(sys.argv[2])
    elif cmd == 'cleanup':
        cleanup()
    else:
        sys.exit(__doc__)
