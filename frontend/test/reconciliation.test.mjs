import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/reconciliation.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

test('labels and tones cover all statuses, unknown shown raw', async () => {
  const m = await mod();
  assert.equal(m.reconciliationLabel('ESCROW_REPAIRED'), 'Escrow onarıldı');
  assert.equal(m.reconciliationLabel('PROVIDER_CANCELLED'), 'Ödeme iptal edildi');
  assert.equal(m.reconciliationLabel('MANUAL_REVIEW'), 'Manuel inceleme');
  assert.equal(m.reconciliationLabel('X'), 'X');
  assert.equal(m.reconciliationTone('ESCROW_REPAIRED'), 'success');
  assert.equal(m.reconciliationTone('MANUAL_REVIEW'), 'warning');
  assert.equal(m.reconciliationTone('X'), 'neutral');
});

test('reconciliationPath omits empty status', async () => {
  const m = await mod();
  assert.equal(m.reconciliationPath('', 0, 20), '/api/admin/payment-reconciliation?page=0&size=20');
  assert.equal(m.reconciliationPath('MANUAL_REVIEW', 2, 10), '/api/admin/payment-reconciliation?status=MANUAL_REVIEW&page=2&size=10');
});

test('shortId takes first 8 chars', async () => {
  const m = await mod();
  assert.equal(m.shortId('12345678-aaaa-bbbb-cccc-dddddddddddd'), '12345678');
  assert.equal(m.shortId('abc'), 'abc');
});
