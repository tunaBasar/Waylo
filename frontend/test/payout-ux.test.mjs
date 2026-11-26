import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let seq = 0;
async function load(file, strip) {
  let source = await readFile(new URL(`../src/lib/${file}`, import.meta.url), 'utf8');
  source = strip(source);
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${seq++}`);
}
const partner = await load('partner.ts', s => s.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error { constructor(m, status, code) { super(m); this.status = status; this.code = code; } }\nexport { ApiError as __ApiError };\nconst api = {};\n'));
const admin = await load('admin.ts', s => s.replace(/import \{ api \} from '\.\/api';\n?/, 'const api = {};\n'));
const amount = await load('amount.ts', s => s);

test('F48 ibanProblem separates shape from checksum', () => {
  assert.equal(partner.ibanProblem(''), null);
  assert.equal(partner.ibanProblem('TR33 0006 1005 1978 6457 8413 26'), null);
  assert.equal(partner.ibanProblem('TR33 0006 1005 1978 6457 8413 27'), partner.IBAN_CHECKSUM_ERROR);
  assert.match(partner.IBAN_CHECKSUM_ERROR, /kontrol hanesi/);
  for (const bad of ['TR33 0006 1005 1978 6457 8413 2', 'DE89 3704 0044 0532 0130 00', 'TR33 0006 1005 1978 6457 841A 26', 'TR33 0006 1005 1978 6457 8413 260']) {
    assert.equal(partner.ibanProblem(bad), partner.IBAN_SHAPE_ERROR, bad);
  }
  assert.match(partner.IBAN_SHAPE_ERROR, /26 karakter/);
  assert.equal(partner.isValidTrIban('TR33 0006 1005 1978 6457 8413 27'), false);
});

test('F51 payoutActionsFor mirrors the state machine', () => {
  const names = s => admin.payoutActionsFor(s).actions.map(a => a.action);
  assert.deepEqual(names('REQUESTED'), ['approve', 'cancel']);
  assert.deepEqual(names('APPROVED'), ['processing', 'cancel']);
  assert.deepEqual(names('PROCESSING'), ['paid', 'failed']);
  for (const t of ['PAID', 'FAILED', 'CANCELLED']) {
    assert.deepEqual(names(t), []);
    assert.equal(admin.payoutActionsFor(t).hint, null);
  }
  assert.equal(admin.payoutActionsFor('REQUESTED').hint, 'Bakiyeyi kontrol edip onaylayin.');
  assert.equal(admin.payoutActionsFor('APPROVED').hint, "Havaleyi baslatirken Isleme al'a basin.");
  assert.equal(admin.payoutActionsFor('PROCESSING').hint, 'Havale yapildiysa Odendi olarak isaretleyin.');
});

test('F52 formatTrAmountInput is Turkish and round-trips', () => {
  assert.equal(amount.formatTrAmountInput(6498), '6.498,00');
  assert.equal(amount.formatTrAmountInput(0.5), '0,50');
  assert.equal(amount.formatTrAmountInput(1234567.89), '1.234.567,89');
  for (const n of [0.5, 1498, 6498, 1234567.89]) {
    const t = amount.formatTrAmountInput(n);
    assert.equal(amount.parseTrAmount(t), n, t);
    assert.equal(partner.parseAmountInput(t), n, t);
  }
});

test('F56 teamInviteErrorMessage explains an unknown invitee, other errors keep their text', () => {
  const E = partner.__ApiError;
  assert.match(partner.teamInviteErrorMessage(new E('kullanici bulunamadi', 404, null)), /kayitli bir Waylo hesabi yok/);
  assert.equal(partner.teamInviteErrorMessage(new E('Role assignment already exists', 409, null)), 'Role assignment already exists');
  assert.equal(partner.teamInviteErrorMessage(new Error('')), 'Rol verilemedi');
});

test('F53 paidAtProblem: not in the future, not before the request minute', () => {
  const requested = '2026-11-26T09:50:19.206Z';
  const now = new Date('2026-11-26T10:00:00Z');
  assert.equal(partner.paidAtProblem(null, requested, now), null);
  // the walk case: 12:45 local (09:45Z) for a request at 12:50 local
  assert.equal(partner.paidAtProblem(new Date('2026-11-26T09:45:00Z'), requested, now), 'Odeme tarihi talep tarihinden once olamaz.');
  // the request minute itself is fine (datetime-local has no seconds)
  assert.equal(partner.paidAtProblem(new Date('2026-11-26T09:50:00Z'), requested, now), null);
  assert.equal(partner.paidAtProblem(new Date('2026-11-26T09:55:00Z'), requested, now), null);
  assert.equal(partner.paidAtProblem(new Date('2026-11-26T10:01:00Z'), requested, now), 'Odeme tarihi gelecekte olamaz.');
  assert.equal(partner.paidAtProblem(new Date('nope'), requested, now), 'Gecersiz tarih.');
  assert.equal(partner.paidAtProblem(new Date('2026-11-26T09:00:00Z'), null, now), null);
  assert.equal(partner.PAYOUT_ERROR_LABEL.PAID_AT_BEFORE_REQUEST, 'Odeme tarihi talep tarihinden once olamaz.');
});
