import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// Compile car.ts, strip the api and partner-car imports, and import via data: URL.
let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/car.ts', import.meta.url), 'utf8');
  // Strip the import that pulls in api/types - replace with stubs.
  const stripped = source
    .replace(/import \{[^}]+\} from '\.\/api';\n?/, '')
    .replace(/import type \{[^}]+\} from '\.\/partner-car';\n?/, '')
    .replace(/ADMIN_CANNOT_PURCHASE_MESSAGE/g, "'Platform yonetıcisi hesabı ile satın alma yapılamaz.'");
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const m = await mod();

// ── generateTimeSlots ────────────────────────────────────────────────────────

test('generateTimeSlots returns 48 slots', () => {
  const slots = m.generateTimeSlots();
  assert.strictEqual(slots.length, 48);
});

test('generateTimeSlots first and last', () => {
  const slots = m.generateTimeSlots();
  assert.strictEqual(slots[0], '00:00');
  assert.strictEqual(slots[47], '23:30');
});

test('generateTimeSlots has 30-minute step pattern', () => {
  const slots = m.generateTimeSlots();
  // Verify slots for hour 9
  assert.strictEqual(slots[18], '09:00');
  assert.strictEqual(slots[19], '09:30');
  assert.strictEqual(slots[20], '10:00');
  assert.strictEqual(slots[21], '10:30');
});

// ── toLocalDateTime ──────────────────────────────────────────────────────────

test('toLocalDateTime basic', () => {
  assert.strictEqual(m.toLocalDateTime('2027-03-15', '10:00'), '2027-03-15T10:00:00');
});

test('toLocalDateTime with 30 min', () => {
  assert.strictEqual(m.toLocalDateTime('2027-03-15', '14:30'), '2027-03-15T14:30:00');
});

test('toLocalDateTime midnight', () => {
  assert.strictEqual(m.toLocalDateTime('2027-01-01', '00:00'), '2027-01-01T00:00:00');
});

test('toLocalDateTime last slot', () => {
  assert.strictEqual(m.toLocalDateTime('2027-06-30', '23:30'), '2027-06-30T23:30:00');
});

// ── errorToMessage ────────────────────────────────────────────────────────────

test('errorToMessage NO_VEHICLE_AVAILABLE', () => {
  assert.strictEqual(
    m.errorToMessage('NO_VEHICLE_AVAILABLE', 'fallback'),
    'Secilen tarihlerde musait arac bulunamadi.',
  );
});

test('errorToMessage DRIVER_TOO_YOUNG', () => {
  assert.strictEqual(
    m.errorToMessage('DRIVER_TOO_YOUNG', 'fallback'),
    'Sofor yas min gerekliligi karsilanmiyor.',
  );
});

test('errorToMessage OUTSIDE_BRANCH_HOURS', () => {
  assert.strictEqual(
    m.errorToMessage('OUTSIDE_BRANCH_HOURS', 'fallback'),
    'Secilen saat sube calisma saatleri disinda.',
  );
});

test('errorToMessage INVALID_TIME', () => {
  assert.strictEqual(
    m.errorToMessage('INVALID_TIME', 'fallback'),
    'Gecersiz tarih veya saat formati.',
  );
});

test('errorToMessage ROUTE_NOT_OFFERED', () => {
  assert.strictEqual(
    m.errorToMessage('ROUTE_NOT_OFFERED', 'fallback'),
    'Giden-donus rotasi icin araç kiralama teklifi bulunmuyor.',
  );
});

test('errorToMessage COMMISSION_RATE_MISSING', () => {
  const msg = m.errorToMessage('COMMISSION_RATE_MISSING', 'fallback');
  assert.ok(msg.includes('Komisyon'));
});

test('errorToMessage ADMIN_CANNOT_PURCHASE', () => {
  const msg = m.errorToMessage('ADMIN_CANNOT_PURCHASE', 'fallback');
  assert.ok(msg.includes('yonet')); // ASCII text from api.ts
});

test('errorToMessage unknown uses fallback', () => {
  assert.strictEqual(
    m.errorToMessage('UNKNOWN_CODE', 'bir hata olustu'),
    'bir hata olustu',
  );
});

test('errorToMessage null uses fallback', () => {
  assert.strictEqual(
    m.errorToMessage(null, 'bir hata olustu'),
    'bir hata olustu',
  );
});

test('errorToMessage undefined uses fallback', () => {
  assert.strictEqual(
    m.errorToMessage(undefined, 'bir hata olustu'),
    'bir hata olustu',
  );
});

// ── formatDateTime ───────────────────────────────────────────────────────────

test('formatDateTime valid ISO', () => {
  const result = m.formatDateTime('2027-03-15T10:30:00+03:00');
  assert.ok(result.length > 0);
  assert.ok(result.includes('2027'));
  assert.ok(result.includes('10'));
});

test('formatDateTime null returns empty', () => {
  assert.strictEqual(m.formatDateTime(null), '');
});

test('formatDateTime undefined returns empty', () => {
  assert.strictEqual(m.formatDateTime(undefined), '');
});

test('formatDateTime invalid returns raw', () => {
  assert.strictEqual(m.formatDateTime('not-a-date'), 'not-a-date');
});

// ── BRANCH_STATUS_LABEL ──────────────────────────────────────────────────────

test('BRANCH_STATUS_LABEL has 3 entries', () => {
  assert.strictEqual(m.BRANCH_STATUS_LABEL['PENDING'], 'Onay bekliyor');
  assert.strictEqual(m.BRANCH_STATUS_LABEL['APPROVED'], 'Onaylandi');
  assert.strictEqual(m.BRANCH_STATUS_LABEL['REJECTED'], 'Reddedildi');
});

// ── RENTAL_STATUS_LABEL ──────────────────────────────────────────────────────

test('RENTAL_STATUS_LABEL has 4 entries', () => {
  assert.strictEqual(m.RENTAL_STATUS_LABEL['HELD'], 'Rezerve edildi');
  assert.strictEqual(m.RENTAL_STATUS_LABEL['CONFIRMED'], 'Onayli');
  assert.strictEqual(m.RENTAL_STATUS_LABEL['CANCELLED'], 'Iptal edildi');
  assert.strictEqual(m.RENTAL_STATUS_LABEL['EXPIRED'], 'Suresi doldu');
});
