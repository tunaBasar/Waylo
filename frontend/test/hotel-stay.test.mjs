import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// Compile hotel-stay.ts, strip the api/types/partner imports, and import via data: URL.
let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/hotel-stay.ts', import.meta.url), 'utf8');
  // Strip the three imports that pull in api, types, partner - replace with a stub for api.
  const stripped = source
    .replace(/import \{ api \} from '@\/lib\/api';\n?/, 'const api = { get: async () => ({}), post: async () => ({}), put: async () => ({}), patch: async () => ({}), del: async () => ({}); };\n')
    .replace(/import type { Hotel } from '@\/lib\/types';\n?/, '')
    .replace(/import type { MealPlan, CancellationPolicy } from '@\/lib\/partner';\n?/, '');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const m = await mod();

// Helper: build a query and mutate a field for out-of-range testing.
const mk = (overrides = {}) => ({
  checkIn: '2026-10-23',
  checkOut: '2026-10-25',
  rooms: 1,
  adults: 1,
  childrenAges: [],
  ...overrides,
});

// ── todayIso ─────────────────────────────────────────────────────────────
test('todayIso returns local yyyy-mm-dd', () => {
  const d = new Date(2026, 9, 22); // Oct 22 local
  assert.strictEqual(m.todayIso(d), '2026-10-22');
});

test('todayIso at year boundary', () => {
  assert.strictEqual(m.todayIso(new Date(2027, 0, 1)), '2027-01-01');
  assert.strictEqual(m.todayIso(new Date(2026, 11, 31)), '2026-12-31');
});

// ── addDaysIso ───────────────────────────────────────────────────────────
test('addDaysIso basic', () => {
  assert.strictEqual(m.addDaysIso('2026-10-22', 1), '2026-10-23');
  assert.strictEqual(m.addDaysIso('2026-10-22', 0), '2026-10-22');
  assert.strictEqual(m.addDaysIso('2026-10-22', -1), '2026-10-21');
});

test('addDaysIso month boundary', () => {
  assert.strictEqual(m.addDaysIso('2026-01-31', 1), '2026-02-01');
  assert.strictEqual(m.addDaysIso('2026-02-28', 1), '2026-03-01');
});

test('addDaysIso year boundary', () => {
  assert.strictEqual(m.addDaysIso('2026-12-31', 1), '2027-01-01');
});

test('addDaysIso leap year', () => {
  assert.strictEqual(m.addDaysIso('2024-02-28', 1), '2024-02-29');
  assert.strictEqual(m.addDaysIso('2024-02-29', 1), '2024-03-01');
});

test('addDaysIso large offset', () => {
  assert.strictEqual(m.addDaysIso('2026-01-01', 365), '2027-01-01');
});

// ── nightsBetween ────────────────────────────────────────────────────────
test('nightsBetween basic', () => {
  assert.strictEqual(m.nightsBetween('2026-10-22', '2026-10-23'), 1);
  assert.strictEqual(m.nightsBetween('2026-10-22', '2026-10-25'), 3);
});

test('nightsBetween zero', () => {
  assert.strictEqual(m.nightsBetween('2026-10-22', '2026-10-22'), 0);
});

test('nightsBetween cross month', () => {
  assert.strictEqual(m.nightsBetween('2026-01-30', '2026-02-01'), 2);
});

test('nightsBetween cross year', () => {
  assert.strictEqual(m.nightsBetween('2026-12-30', '2027-01-02'), 3);
});

// ── validateStay ─────────────────────────────────────────────────────────
test('validateStay valid query returns null', () => {
  assert.strictEqual(m.validateStay({
    checkIn: '2026-10-23',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 2,
    childrenAges: [],
  }, '2026-10-22'), null);
});

test('validateStay past checkIn', () => {
  const err = m.validateStay(mk({ checkIn: '2026-10-20', checkOut: '2026-10-22' }), '2026-10-22');
  assert(err !== null);
  assert(typeof err === 'string' && err.includes('bugunden once'));
});

test('validateStay checkOut equals checkIn', () => {
  const err = m.validateStay(mk({ checkIn: '2026-10-22', checkOut: '2026-10-22' }), '2026-10-22');
  assert(err !== null);
  assert(typeof err === 'string' && err.includes('sonra'));
});

test('validateStay 30 nights ok, 31 fails', () => {
  assert.strictEqual(m.validateStay({
    checkIn: '2026-10-01',
    checkOut: '2026-10-31',
    rooms: 1,
    adults: 1,
    childrenAges: [],
  }, '2026-10-01'), null);

  assert.strictEqual(m.validateStay({
    checkIn: '2026-10-01',
    checkOut: '2026-11-01',
    rooms: 1,
    adults: 1,
    childrenAges: [],
  }, '2026-10-01') !== null, true);
});

test('validateStay 31 nights fails', () => {
  const err = m.validateStay({
    checkIn: '2026-10-01',
    checkOut: '2026-11-01',
    rooms: 1,
    adults: 1,
    childrenAges: [],
  }, '2026-10-01');
  assert(err !== null);
  assert(typeof err === 'string' && err.includes('gece'));
});

test('validateStay rooms out of range', () => {
  assert(m.validateStay(mk({ rooms: 0 })) !== null);
  assert(m.validateStay(mk({ rooms: 11 })) !== null);
});

test('validateStay adults out of range', () => {
  assert(m.validateStay(mk({ adults: 0 })) !== null);
  assert(m.validateStay(mk({ adults: 21 })) !== null);
});

test('validateStay child age 18 fails, 17 ok', () => {
  const err = m.validateStay({
    checkIn: '2026-10-23',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 2,
    childrenAges: [18],
  }, '2026-10-22');
  assert(err !== null);
  assert(typeof err === 'string' && err.includes('yas'));

  assert.strictEqual(m.validateStay({
    checkIn: '2026-10-23',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 2,
    childrenAges: [17],
  }, '2026-10-22'), null);
});

test('validateStay too many children', () => {
  const ages = Array.from({ length: 11 }, () => 5);
  assert(m.validateStay({
    checkIn: '2026-10-23',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 1,
    childrenAges: ages,
  }, '2026-10-22') !== null);
});

test('validateStay negative child age', () => {
  assert(m.validateStay({
    checkIn: '2026-10-23',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 1,
    childrenAges: [-1],
  }, '2026-10-22') !== null);
});

// ── stayQueryString ──────────────────────────────────────────────────────
test('stayQueryString basic', () => {
  const qs = m.stayQueryString({
    checkIn: '2026-10-22',
    checkOut: '2026-10-25',
    rooms: 2,
    adults: 3,
    childrenAges: [],
  });
  assert.strictEqual(qs, 'checkIn=2026-10-22&checkOut=2026-10-25&rooms=2&adults=3');
});

test('stayQueryString repeated childrenAges', () => {
  const qs = m.stayQueryString({
    checkIn: '2026-10-22',
    checkOut: '2026-10-25',
    rooms: 1,
    adults: 2,
    childrenAges: [3, 5],
  });
  assert.strictEqual(qs, 'checkIn=2026-10-22&checkOut=2026-10-25&rooms=1&adults=2&childrenAges=3&childrenAges=5');
});

test('stayQueryString no leading ?', () => {
  assert.ok(!m.stayQueryString({
    checkIn: '2026-10-22',
    checkOut: '2026-10-23',
    rooms: 1,
    adults: 1,
    childrenAges: [],
  }).startsWith('?'));
});

// ── parseStayQuery ───────────────────────────────────────────────────────
test('parseStayQuery round trip', () => {
  const original = {
    checkIn: '2026-10-22',
    checkOut: '2026-10-25',
    rooms: 2,
    adults: 3,
    childrenAges: [3, 5],
  };
  const qs = m.stayQueryString(original);
  const parsed = m.parseStayQuery(new URLSearchParams(qs));
  assert(parsed !== null);
  assert.deepStrictEqual(parsed, original);
});

test('parseStayQuery defaults', () => {
  const params = new URLSearchParams('checkIn=2026-10-22&checkOut=2026-10-25');
  const parsed = m.parseStayQuery(params);
  assert(parsed !== null);
  assert.strictEqual(parsed.rooms, 1);
  assert.strictEqual(parsed.adults, 1);
  assert.deepStrictEqual(parsed.childrenAges, []);
});

test('parseStayQuery missing checkIn returns null', () => {
  assert.strictEqual(m.parseStayQuery(new URLSearchParams('checkOut=2026-10-25')), null);
});

test('parseStayQuery missing checkOut returns null', () => {
  assert.strictEqual(m.parseStayQuery(new URLSearchParams('checkIn=2026-10-22')), null);
});

test('parseStayQuery ignores non-numeric childrenAges', () => {
  const params = new URLSearchParams('checkIn=2026-10-22&checkOut=2026-10-25&childrenAges=3&childrenAges=abc&childrenAges=5');
  const parsed = m.parseStayQuery(params);
  assert(parsed !== null);
  assert.deepStrictEqual(parsed.childrenAges, [3, 5]);
});
