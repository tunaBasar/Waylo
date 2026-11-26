import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/order-actions.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { canOfferCancel } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

const order = (status = 'CONFIRMED', items = [{ id: 'a' }, { id: 'b' }]) => ({ status, items });

test('all items redeemed -> false', () => {
  assert.equal(canOfferCancel(order(), [{ orderItemId: 'a', status: 'REDEEMED' }, { orderItemId: 'b', status: 'NO_SHOW' }]), false);
});
test('one redeemed, one active -> true', () => {
  assert.equal(canOfferCancel(order(), [{ orderItemId: 'a', status: 'REDEEMED' }, { orderItemId: 'b', status: 'ACTIVE' }]), true);
});
test('no vouchers -> true', () => {
  assert.equal(canOfferCancel(order(), []), true);
  assert.equal(canOfferCancel(order(), null), true);
});
test('cancelled order -> false', () => {
  assert.equal(canOfferCancel(order('CANCELLED'), []), false);
});
test('cancelled item plus redeemed item -> false', () => {
  assert.equal(canOfferCancel(order('CONFIRMED', [{ id: 'a', cancelOutcome: 'REFUND' }, { id: 'b' }]), [{ orderItemId: 'b', status: 'REDEEMED' }]), false);
});
