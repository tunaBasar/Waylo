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
  const { orderPolicyText } = await load();
  assert.equal(orderPolicyText('NON_REFUNDABLE', null, null), 'Iade edilmez');
  assert.equal(orderPolicyText('FREE_CANCELLATION', null, null), 'Hizmet baslangicina kadar ucretsiz iptal');
  assert.match(orderPolicyText('FREE_UNTIL', 48, null), /48 saat/);
  assert.equal(orderPolicyText(undefined, undefined, undefined), 'Hizmet baslangicina kadar ucretsiz iptal');
});

test('refund text shows amount or no refund', async () => {
  const { refundText } = await load();
  assert.match(refundText(1000, 'TRY'), /iade edilecek$/);
  assert.match(refundText(1000, 'TRY'), /1\.000/);
  assert.equal(refundText(0, 'TRY'), 'Iade yapilmayacak');
});

const NOW = new Date(2026, 10, 26, 10, 0, 0); // local 26.11.2026

test('offer text: future deadline, same-day check-in, past, non refundable', async () => {
  const { offerPolicyText } = await load();
  assert.equal(offerPolicyText('FREE_CANCELLATION', 2, '2026-12-05', NOW), '02.12.2026 gun sonuna kadar ucretsiz iptal');
  assert.equal(offerPolicyText('FREE_CANCELLATION', 2, '2026-11-26', NOW), 'Iade edilmez (ucretsiz iptal suresi gecti)');
  assert.equal(offerPolicyText('FREE_CANCELLATION', 2, '2026-11-27', NOW), 'Iade edilmez (ucretsiz iptal suresi gecti)');
  assert.equal(offerPolicyText('NON_REFUNDABLE', null, '2026-12-05', NOW), 'Iade edilmez');
});

test('order text', async () => {
  const { orderPolicyText } = await load();
  const now = new Date('2026-11-26T07:00:00Z');
  assert.equal(orderPolicyText('FREE_UNTIL', 48, '2026-11-29T21:00:00Z', now), '29.11.2026 gun sonuna kadar ucretsiz iptal');
  assert.equal(orderPolicyText('FREE_UNTIL', 48, '2026-11-24T21:00:00Z', now), 'Iade edilmez (ucretsiz iptal suresi gecti)');
  assert.equal(orderPolicyText('FREE_CANCELLATION', null, '2026-11-25T21:00:00Z', now), 'Iade edilmez (ucretsiz iptal suresi gecti)');
  assert.equal(orderPolicyText('FREE_UNTIL', 24, '2026-11-27T10:30:00Z', now), 'Ucretsiz iptal son an: 27.11.2026 13:30');
});
