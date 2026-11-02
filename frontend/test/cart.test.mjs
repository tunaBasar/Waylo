import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/cart.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}
const item = (id, vertical, request, amount = 100, currency = 'TRY') => ({ id, vertical, label: id, amount, currency, request });

test('addItem ignores duplicates and respects the limit', async () => {
  const { addItem, CART_MAX_ITEMS } = await mod();
  let c = addItem([], item('a', 'HOTEL', { quoteId: 'q1' }));
  c = addItem(c, item('b', 'HOTEL', { quoteId: 'q1' }));
  assert.equal(c.length, 1);
  for (let i = 0; i < 20; i++) c = addItem(c, item(`x${i}`, 'CAR', { n: i }));
  assert.equal(c.length, CART_MAX_ITEMS);
});

test('removeItem and totals', async () => {
  const { removeItem, totalsByCurrency, hasMixedCurrency } = await mod();
  const c = [item('a', 'HOTEL', {}, 100), item('b', 'CAR', {}, 50.5)];
  assert.deepEqual(totalsByCurrency(c), { TRY: 150.5 });
  assert.equal(removeItem(c, 'a').length, 1);
  assert.equal(hasMixedCurrency([...c, item('c', 'BUS', {}, 1, 'EUR')]), true);
});

test('toPackageRequest flattens request and trims promo', async () => {
  const { toPackageRequest } = await mod();
  const r = toPackageRequest([item('a', 'HOTEL', { quoteId: 'q' })], ' SUMMER ');
  assert.deepEqual(r, { items: [{ vertical: 'HOTEL', quoteId: 'q' }], promoCode: 'SUMMER' });
  assert.deepEqual(toPackageRequest([item('a', 'HOTEL', { quoteId: 'q' })], '  '), { items: [{ vertical: 'HOTEL', quoteId: 'q' }] });
});

test('parseCart is defensive; itemIndexMessage', async () => {
  const { parseCart, itemIndexMessage } = await mod();
  assert.deepEqual(parseCart('nope'), []);
  assert.deepEqual(parseCart('{"a":1}'), []);
  assert.equal(parseCart(JSON.stringify([item('a', 'CAR', {})])).length, 1);
  assert.equal(itemIndexMessage(1, [item('a', 'CAR', {}), item('b', 'BUS', {})]), '2. kalem (b)');
  assert.equal(itemIndexMessage(5, []), null);
});
