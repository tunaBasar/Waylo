import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// surface.ts has no imports, so it compiles standalone.
const source = await readFile(new URL('../src/lib/surface.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const m = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('admin guard: only ADMIN may enter /admin', () => {
  assert.equal(m.guardRedirect('/admin', 'ADMIN'), null);
  assert.equal(m.guardRedirect('/admin/flights', 'ADMIN'), null);
  assert.deepEqual(m.guardRedirect('/admin', 'USER'), { to: '/' });
  assert.deepEqual(m.guardRedirect('/admin/users', 'USER'), { to: '/' });
  assert.equal(m.surfaceOf('/administrator'), 'customer');
});

test('partner guard: needs a membership, never for ADMIN', () => {
  assert.equal(m.guardRedirect('/partner', 'USER', true), null);
  assert.equal(m.guardRedirect('/partner/calendar', 'USER', null), null);
  assert.deepEqual(m.guardRedirect('/partner', 'USER', false), { to: '/', notice: m.PARTNER_REQUIRED_MESSAGE });
  assert.deepEqual(m.guardRedirect('/partner', 'ADMIN', true), { to: '/admin' });
});

test('customer guard: ADMIN is sent to /admin, purchase pages carry the message', () => {
  for (const p of ['/hotels/abc', '/quotes/q1', '/vehicles', '/orders', '/rentals']) {
    assert.deepEqual(m.guardRedirect(p, 'ADMIN'), { to: '/admin', notice: m.ADMIN_PURCHASE_MESSAGE }, p);
  }
  assert.deepEqual(m.guardRedirect('/', 'ADMIN'), { to: '/admin' });
  assert.deepEqual(m.guardRedirect('/hotels', 'ADMIN'), { to: '/admin' });
  assert.equal(m.guardRedirect('/profile', 'ADMIN'), null);
  for (const p of ['/', '/hotels', '/hotels/abc', '/orders', '/rentals']) assert.equal(m.guardRedirect(p, 'USER'), null, p);
  assert.match(m.ADMIN_PURCHASE_MESSAGE, /satin alma yapilamaz/);
  assert.equal(m.redirectUrl({ to: '/admin', notice: 'a b' }), '/admin?notice=a%20b');
});

test('post-login redirect', () => {
  assert.equal(m.postLoginTarget('ADMIN', null), '/admin');
  assert.equal(m.postLoginTarget('USER', null), '/');
  assert.equal(m.postLoginTarget('USER', '/orders?created=1'), '/orders?created=1');
  assert.equal(m.postLoginTarget('USER', '/partner/calendar'), '/partner/calendar');
  assert.equal(m.postLoginTarget('USER', '/admin/users'), '/');
  assert.equal(m.postLoginTarget('ADMIN', '/admin/flights'), '/admin/flights');
  assert.equal(m.postLoginTarget('ADMIN', '/orders'), '/admin');
  assert.equal(m.postLoginTarget('ADMIN', '/hotels/x'), '/admin');
  assert.equal(m.postLoginTarget('USER', '//evil.com'), '/');
  assert.equal(m.postLoginTarget('USER', 'https://evil.com'), '/');
  assert.equal(m.postLoginTarget('USER', '/login'), '/');
});
