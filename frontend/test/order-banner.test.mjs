import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/order-banner.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { showCreatedBanner, sortOrdersForDisplay } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('sortOrdersForDisplay: unpaid orders first, newest first, input untouched (F76)', () => {
  const orders = [
    { id: 'paid-old', status: 'CONFIRMED', createdAt: '2026-11-26T08:00:00Z' },
    { id: 'pending-new', status: 'PENDING', createdAt: '2026-11-26T12:28:00Z' },
    { id: 'paid-new', status: 'CONFIRMED', createdAt: '2026-11-26T12:00:00Z' },
    { id: 'cancelled', status: 'CANCELLED', createdAt: '2026-11-26T11:00:00Z' },
    { id: 'pending-old', status: 'PENDING', createdAt: '2026-11-26T12:20:00Z' },
  ];
  const copy = JSON.stringify(orders);
  assert.deepEqual(sortOrdersForDisplay(orders).map(o => o.id), ['pending-new', 'pending-old', 'paid-new', 'cancelled', 'paid-old']);
  assert.equal(JSON.stringify(orders), copy);
  assert.deepEqual(sortOrdersForDisplay([]), []);
});

test('created banner only while that order is PENDING', () => {
  assert.equal(showCreatedBanner(null, [{ id: 'a', status: 'PENDING' }]), false);
  assert.equal(showCreatedBanner('a', [{ id: 'a', status: 'PENDING' }]), true);
  assert.equal(showCreatedBanner('a', [{ id: 'a', status: 'CONFIRMED' }]), false);
  assert.equal(showCreatedBanner('a', []), false);
});
