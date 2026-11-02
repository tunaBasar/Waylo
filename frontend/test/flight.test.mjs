import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/flight.ts', import.meta.url), 'utf8');
  const stripped = source
    .replace(/import \{[^}]+\} from '\.\/api';\n?/, '')
    .replace(/ADMIN_CANNOT_PURCHASE_MESSAGE/g, "'admin yasak'");
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}
const m = await mod();

test('flightErrorMessage maps known codes and falls back', () => {
  assert.match(m.flightErrorMessage('OFFER_EXPIRED', 'x'), /suresi doldu/);
  assert.match(m.flightErrorMessage('SEAT_ALREADY_RESERVED', 'x'), /koltuk/);
  assert.equal(m.flightErrorMessage('SOMETHING_ELSE', 'fallback'), 'fallback');
  assert.equal(m.flightErrorMessage(null, 'fallback'), 'fallback');
});

test('mustSearchAgain only for offer problems', () => {
  for (const c of ['OFFER_EXPIRED', 'INVALID_OFFER', 'FARE_CHANGED']) assert.equal(m.mustSearchAgain(c), true);
  assert.equal(m.mustSearchAgain('SEAT_ALREADY_RESERVED'), false);
  assert.equal(m.mustSearchAgain(undefined), false);
});

test('secondsLeft never negative, handles junk', () => {
  const now = Date.parse('2026-11-02T10:00:00Z');
  assert.equal(m.secondsLeft('2026-11-02T10:05:00Z', now), 300);
  assert.equal(m.secondsLeft('2026-11-02T09:00:00Z', now), 0);
  assert.equal(m.secondsLeft('garbage', now), 0);
});

test('formatCountdown', () => {
  assert.equal(m.formatCountdown(0), '0:00');
  assert.equal(m.formatCountdown(65), '1:05');
  assert.equal(m.formatCountdown(600), '10:00');
  assert.equal(m.formatCountdown(-4), '0:00');
});

test('isValidPassengerName matches backend rule', () => {
  assert.equal(m.isValidPassengerName('Ayşe'), true);
  assert.equal(m.isValidPassengerName("O'Neil-Smith"), true);
  assert.equal(m.isValidPassengerName('Ali Veli'), true);
  assert.equal(m.isValidPassengerName(''), false);
  assert.equal(m.isValidPassengerName('   '), false);
  assert.equal(m.isValidPassengerName('Test1'), false);
  assert.equal(m.isValidPassengerName('<b>'), false);
  assert.equal(m.isValidPassengerName('a'.repeat(81)), false);
});

test('seatsForCabin filters cabin and availability, sorts by row then letter', () => {
  const s = (seatNumber, seatType, available = true) => ({ id: seatNumber, flightNumber: 'TK1', seatNumber, seatType, price: 1, available });
  const out = m.seatsForCabin([s('10A', 'ECONOMY'), s('2B', 'ECONOMY'), s('2A', 'ECONOMY'), s('1A', 'BUSINESS'), s('3C', 'ECONOMY', false)], 'ECONOMY');
  assert.deepEqual(out.map(x => x.seatNumber), ['2A', '2B', '10A']);
});

test('newIdempotencyKey is unique-ish and prefixed', () => {
  const a = m.newIdempotencyKey(), b = m.newIdempotencyKey();
  assert.match(a, /^topup-/);
  assert.notEqual(a, b);
});
