import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

async function load() {
  const source = await readFile(new URL('../src/lib/cancellation.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('policy text per policy', async () => {
  const { policyText } = await load();
  assert.equal(policyText('NON_REFUNDABLE', null), 'Iade edilemez');
  assert.equal(policyText('FREE_CANCELLATION', null), 'Ucretsiz iptal');
  assert.match(policyText('FREE_UNTIL', 48), /48 saat/);
  assert.equal(policyText(undefined, undefined), 'Ucretsiz iptal');
});

test('refund text shows amount or no refund', async () => {
  const { refundText } = await load();
  assert.match(refundText(1000, 'TRY'), /iade edilecek$/);
  assert.match(refundText(1000, 'TRY'), /1\.000/);
  assert.equal(refundText(0, 'TRY'), 'Iade yapilmayacak');
});
