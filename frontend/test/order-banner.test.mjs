import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/order-banner.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { showCreatedBanner } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('created banner only while that order is PENDING', () => {
  assert.equal(showCreatedBanner(null, [{ id: 'a', status: 'PENDING' }]), false);
  assert.equal(showCreatedBanner('a', [{ id: 'a', status: 'PENDING' }]), true);
  assert.equal(showCreatedBanner('a', [{ id: 'a', status: 'CONFIRMED' }]), false);
  assert.equal(showCreatedBanner('a', []), false);
});
