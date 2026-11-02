import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/bus.ts', import.meta.url), 'utf8');
  const stub = "class ApiError extends Error { constructor(m, s, c) { super(m); this.status = s; this.code = c; } }\nconst api = {};\n";
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
  assert.equal(m.formatMoney(500, 'TRY'), '500.00 TRY');
});

test('busErrorMessage maps known codes and falls back', () => {
  const err = (code, msg) => { const e = new Error(msg); e.code = code; return e; };
  assert.match(m.busErrorMessage('x', 'fb'), /fb/);
  assert.equal(m.busErrorMessage({}, 'fb'), 'fb');
});
