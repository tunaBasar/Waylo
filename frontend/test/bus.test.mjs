import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/bus.ts', import.meta.url), 'utf8');
  const stub = "class ApiError extends Error { constructor(m, s, c) { super(m); this.status = s; this.code = c; } }\nconst api = {};\nconst SALES_CLOSED_MESSAGE = 'Bu urun su anda satisa kapali. Lutfen daha sonra tekrar deneyin veya destek ile iletisime gecin.';\n";
  const stripped = stub + source.replace(/import \{[^}]+\} from '\.\/api';\n?/, '');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const m = await mod();
const seats = n => Array.from({ length: n }, (_, i) => ({ seatNo: i + 1, available: true, occupantGender: null }));

test('seatRows splits 2+2 into blocks of two', () => {
  const rows = m.seatRows('TWO_PLUS_TWO', seats(8));
  assert.equal(rows.length, 2);
  assert.deepEqual(rows[0].map(b => b.map(s => s.seatNo)), [[1, 2], [3, 4]]);
});

test('seatRows 2+1 has a single seat on the right', () => {
  const rows = m.seatRows('TWO_PLUS_ONE', seats(6));
  assert.deepEqual(rows[1].map(b => b.map(s => s.seatNo)), [[4, 5], [6]]);
});

test('neighborSeat matches the backend layout', () => {
  assert.equal(m.neighborSeat('TWO_PLUS_TWO', 1), 2);
  assert.equal(m.neighborSeat('TWO_PLUS_TWO', 2), 1);
  assert.equal(m.neighborSeat('TWO_PLUS_TWO', 3), 4);
  assert.equal(m.neighborSeat('TWO_PLUS_TWO', 5), 6);
  assert.equal(m.neighborSeat('TWO_PLUS_ONE', 3), null);
  assert.equal(m.neighborSeat('TWO_PLUS_ONE', 4), 5);
});

test('seatBlockedByGender: other gender neighbour blocks, same gender or own booking does not', () => {
  const s = seats(8);
  s[0] = { seatNo: 1, available: false, occupantGender: 'FEMALE' };
  assert.equal(m.seatBlockedByGender('TWO_PLUS_TWO', s, 2, 'MALE', [2]), true);
  assert.equal(m.seatBlockedByGender('TWO_PLUS_TWO', s, 2, 'FEMALE', [2]), false);
  assert.equal(m.seatBlockedByGender('TWO_PLUS_TWO', s, 3, 'MALE', [3]), false);
  const own = seats(8);
  assert.equal(m.seatBlockedByGender('TWO_PLUS_TWO', own, 2, 'MALE', [1, 2]), false);
});

test('validateFares requires full route and valid ranges', () => {
  assert.equal(m.validateFares(3, [{ fromSeq: 0, toSeq: 2, price: 500 }]), null);
  assert.match(m.validateFares(3, [{ fromSeq: 0, toSeq: 1, price: 300 }]), /guzergah/);
  assert.match(m.validateFares(3, [{ fromSeq: 0, toSeq: 3, price: 1 }]), /Gecersiz/);
  assert.match(m.validateFares(3, [{ fromSeq: 0, toSeq: 2, price: 5 }, { fromSeq: 0, toSeq: 2, price: 6 }]), /Tekrar/);
  assert.match(m.validateFares(3, [{ fromSeq: 0, toSeq: 2, price: 0 }]), /Fiyat/);
});

test('formatters', () => {
  assert.equal(m.formatTripTime('2027-02-02T22:05:00'), '02.02.2027 22:05');
  assert.equal(m.formatClock('2027-02-02T22:05:00'), '22:05');
  assert.equal(m.formatClock(null), '');
  assert.equal(m.formatMoney(500, 'TRY'), '500,00 TL');
  assert.equal(m.formatMoney(850, 'TRY'), '850,00 TL');
  assert.equal(m.formatMoney(1234.5, 'TRY'), '1.234,50 TL');
  assert.equal(m.formatMoney(1234567.891, 'TRY'), '1.234.567,89 TL');
  assert.equal(m.formatMoney(12, 'EUR'), '12,00 EUR');
});

test('busErrorMessage maps known codes and falls back', () => {
  assert.match(m.busErrorMessage('x', 'fb'), /fb/);
  assert.equal(m.busErrorMessage({}, 'fb'), 'fb');
});

test('missingBusDocumentsText uses Turkish labels, never the raw enum (F70)', () => {
  const t = m.missingBusDocumentsText(['TRANSPORT_AUTHORIZATION', 'TAX_CERTIFICATE']);
  assert.equal(t, 'Eksik firma belgeleri: Yetki belgesi (D1), Vergi levhasi. Belgeler sayfasindan yukleyin.');
  assert.ok(!t.includes('TRANSPORT_AUTHORIZATION'));
  assert.match(m.missingBusDocumentsText(['SOMETHING_NEW']), /SOMETHING_NEW/);
});

test('busErrorMessage turns the trip rule texts into Turkish (F71)', () => {
  const E = class extends Error { constructor(msg, code) { super(msg); this.code = code; } };
  assert.match(m.busErrorMessage(new Error('Stop times must increase along the route'), 'fb'), /Durak saatleri guzergah boyunca ileri gitmeli/);
  assert.match(m.busErrorMessage(new Error('Departure is before arrival at stop 2'), 'fb'), /kalkis saati varis saatinden once olamaz/);
  assert.match(m.busErrorMessage(new Error('A fare for the full route is required'), 'fb'), /Tum guzergah/);
  assert.equal(m.busErrorMessage(new Error('Something unknown'), 'fb'), 'Something unknown');
  assert.equal(m.busErrorMessage(new Error(''), 'fb'), 'fb');
  void E;
});

test('tripBadge: a published trip of a company or bus that cannot sell is not shown as on sale (D-BUS-TRIP)', () => {
  assert.deepEqual(m.tripBadge('CANCELLED', true, true), { label: 'Iptal', tone: 'neutral', hint: null });
  assert.equal(m.tripBadge('PUBLISHED', true, true).label, 'Satista');
  const company = m.tripBadge('PUBLISHED', false, true);
  assert.equal(company.label, 'Satista degil');
  assert.match(company.hint, /Firma onayi/);
  const bus = m.tripBadge('PUBLISHED', true, false);
  assert.equal(bus.label, 'Satista degil');
  assert.match(bus.hint, /Otobusun belgeleri/);
  // unknown (status call failed) does not claim anything negative
  assert.equal(m.tripBadge('PUBLISHED', null, null).label, 'Satista');
});
