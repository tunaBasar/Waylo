import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// Compile partner.ts with the api import stubbed, as the other lib tests do.
let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/partner.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error {}\nconst api = {};\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}
const m = await mod();

const VALID = 'TR33 0006 1005 1978 6457 8413 26';

test('IBAN mod-97 accepts the contract vector grouped, ungrouped, lowercase', () => {
  assert.equal(m.isValidTrIban(VALID), true);
  assert.equal(m.isValidTrIban('TR330006100519786457841326'), true);
  assert.equal(m.isValidTrIban(VALID.toLowerCase()), true);
  assert.equal(m.isValidTrIban('TR33-0006-1005-1978-6457-8413-26'), true);
});

test('IBAN rejects bad checksum, wrong length, non TR, letters', () => {
  assert.equal(m.isValidTrIban('TR33 0006 1005 1978 6457 8413 27'), false);
  assert.equal(m.isValidTrIban('TR33 0006 1005 1978 6457 8413 2'), false);
  assert.equal(m.isValidTrIban('TR33 0006 1005 1978 6457 8413 260'), false);
  assert.equal(m.isValidTrIban('DE89 3704 0044 0532 0130 00'), false);
  assert.equal(m.isValidTrIban('TR33 0006 1005 1978 6457 841A 26'), false);
  assert.equal(m.isValidTrIban(''), false);
});

test('mask shows only last two digits', () => {
  assert.equal(m.maskIbanFromLast4('1326'), 'TR** **** **** **** **** **26');
  assert.ok(!m.maskIbanFromLast4('1326').includes('0006'));
});

test('formatIbanGroups groups by four', () => {
  assert.equal(m.formatIbanGroups('TR330006100519786457841326'), VALID);
});

test('amount parsing and validation', () => {
  assert.equal(m.parseAmountInput('1.000,50'), 1000.5);
  assert.equal(m.parseAmountInput('100.25'), 100.25);
  assert.equal(m.parseAmountInput('abc'), null);
  assert.equal(m.parseAmountInput('1.234'), null);
  assert.equal(m.parseAmountInput(''), null);
  assert.equal(m.validatePayoutAmount('100.00', '900.00', '100.00'), null);
  assert.equal(m.validatePayoutAmount('900', 900, 100), null);
  assert.match(m.validatePayoutAmount('99.99', '900.00', '100.00'), /Asgari/);
  assert.match(m.validatePayoutAmount('900.01', '900.00', '100.00'), /bakiye/);
  assert.match(m.validatePayoutAmount('0', '900.00', '100.00'), /sifirdan/);
  assert.match(m.validatePayoutAmount('x', '900.00', '100.00'), /Gecerli/);
});

test('toAmount accepts numbers and decimal strings', () => {
  assert.equal(m.toAmount('12.50'), 12.5);
  assert.equal(m.toAmount(3), 3);
  assert.equal(m.toAmount(null), 0);
  assert.equal(m.toAmount('nope'), 0);
});

test('status and blocked reason labels cover every value', () => {
  for (const s of ['REQUESTED', 'APPROVED', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED']) {
    assert.ok(m.PAYOUT_STATUS_LABEL[s]); assert.ok(m.PAYOUT_STATUS_TONE[s]);
  }
  for (const s of ['PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'DISABLED']) assert.ok(m.BANK_STATUS_LABEL[s]);
  for (const r of ['NO_BANK_ACCOUNT', 'BANK_NOT_VERIFIED', 'BELOW_MINIMUM', 'OPEN_PAYOUT', 'NEGATIVE_BALANCE',
    'CURRENCY_NOT_SUPPORTED', 'NOT_OWNER', 'ORGANIZATION_NOT_ACTIVE']) assert.ok(m.PAYOUT_BLOCKED_LABEL[r]);
  assert.equal(m.PAYOUT_STATUS_LABEL.PAID, 'Odendi');
  assert.equal(m.BANK_STATUS_LABEL.PENDING_VERIFICATION, 'Onay bekliyor');
});
