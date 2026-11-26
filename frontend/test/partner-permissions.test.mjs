import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let seq = 0;
async function load(file, strip = s => s) {
  const source = strip(await readFile(new URL(`../src/lib/${file}`, import.meta.url), 'utf8'));
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${seq++}`);
}

const perms = await load('partner-permissions.ts');
const ms = (...roles) => ({ roles: roles.map(([role, scopeType, scopeId]) => ({ role, scopeType, scopeId })) });
const owner = ms(['ORGANIZATION_OWNER', 'ORGANIZATION', 'o']);
const otherHotelOwner = ms(['HOTEL_OWNER', 'HOTEL', 'h2']);
const manager = ms(['HOTEL_MANAGER', 'HOTEL', 'h1']);
const staff = ms(['HOTEL_STAFF', 'HOTEL', 'h1']);
const nav = ['/partner', '/partner/room-types', '/partner/rate-plans', '/partner/calendar', '/partner/orders', '/partner/team', '/partner/redeem'].map(href => ({ href }));
const hrefs = list => list.map(i => i.href);

test('canManageHotel', () => {
  assert.equal(perms.canManageHotel(owner, 'h1'), true);
  assert.equal(perms.canManageHotel(otherHotelOwner, 'h1'), false);
  assert.equal(perms.canManageHotel(manager, 'h1'), true);
  assert.equal(perms.canManageHotel(staff, 'h1'), false);
  assert.equal(perms.canManageHotel(null, 'h1'), false);
});

test('canViewTeam', () => {
  assert.equal(perms.canViewTeam(owner), true);
  assert.equal(perms.canViewTeam(otherHotelOwner), true);
  assert.equal(perms.canViewTeam(manager), true);
  assert.equal(perms.canViewTeam(staff), false);
  assert.equal(perms.canViewTeam(null), false);
});

test('hotelNavFor', () => {
  assert.equal(perms.hotelNavFor(owner, 'h1', nav).length, nav.length);
  assert.equal(perms.hotelNavFor(manager, 'h1', nav).length, nav.length);
  assert.deepEqual(hrefs(perms.hotelNavFor(staff, 'h1', nav)), ['/partner', '/partner/orders', '/partner/redeem']);
  // owner of another hotel: no room management here, but may view the team
  assert.deepEqual(hrefs(perms.hotelNavFor(otherHotelOwner, 'h1', nav)), ['/partner', '/partner/orders', '/partner/team', '/partner/redeem']);
  assert.equal(perms.hotelNavFor(null, 'h1', nav), nav);
});

test('sectionErrorMessage maps 403 to Turkish text with the role', async () => {
  const m = await load('partner.ts', s => s.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error { constructor(msg, status, code) { super(msg); this.status = status; this.code = code; } }\nglobalThis.__ApiError = ApiError;\n'));
  const E = globalThis.__ApiError;
  const forbidden = new E('Not allowed to perform this action', 403, null);
  assert.equal(m.sectionErrorMessage(forbidden, staff.roles, 'x'),
    'Bu bolum icin yetkiniz yok (rolunuz: Otel personeli). Isletme sahibinden yetki isteyin.');
  assert.equal(m.sectionErrorMessage(forbidden, undefined, 'x'), 'Bu bolum icin yetkiniz yok. Isletme sahibinden yetki isteyin.');
  assert.equal(m.partnerErrorMessage(forbidden, 'x'), 'Bu islem icin yetkiniz yok.');
  assert.equal(m.sectionErrorMessage(new E('Boom', 500, null), staff.roles, 'x'), 'Boom');
  assert.equal(m.sectionErrorMessage('weird', staff.roles, 'fallback'), 'fallback');
});
