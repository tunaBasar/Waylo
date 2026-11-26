import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const opts = { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } };
const amount = ts.transpileModule(await readFile(new URL('../src/lib/amount.ts', import.meta.url), 'utf8'), opts).outputText;
const amountUrl = `data:text/javascript;base64,${Buffer.from(amount).toString('base64')}`;
const src = (await readFile(new URL('../src/lib/commission.ts', import.meta.url), 'utf8')).replace("'./amount'", `'${amountUrl}'`);
const m = await import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(src, opts).outputText).toString('base64')}`);

test('verticalOf maps business types', () => {
  assert.equal(m.verticalOf('HOTEL'), 'HOTEL');
  assert.equal(m.verticalOf('CAR_RENTAL'), 'CAR');
  assert.equal(m.verticalOf('BUS'), 'BUS');
  assert.equal(m.verticalOf(undefined), null);
});

test('percent parsing: 0 < x <= 50, comma allowed', () => {
  assert.equal(m.parseCommissionPercent('10'), 0.1);
  assert.equal(m.parseCommissionPercent('12,5'), 0.125);
  assert.equal(m.parseCommissionPercent('50'), 0.5);
  for (const bad of ['0', '50,01', '51', '-3', 'abc', '', '1,234']) assert.equal(m.parseCommissionPercent(bad), null, bad);
});

test('prefill prefers the vertical default, then the org override', () => {
  const rows = [
    { id: '1', vertical: 'HOTEL', supplierOrganizationId: null, rate: 0.12 },
    { id: '2', vertical: 'CAR', supplierOrganizationId: 'o1', rate: 0.1 },
  ];
  assert.equal(m.prefillPercent(rows, 'HOTEL', 'o1'), '12');
  assert.equal(m.prefillPercent(rows, 'CAR', 'o1'), '10');
  assert.equal(m.prefillPercent(rows, 'BUS', 'o1'), '');
  assert.equal(m.prefillPercent(rows, null, 'o1'), '');
  assert.equal(m.ratePercentText(0.125), '12,5');
});

test('keepTypedPercent: a late rate suggestion never overwrites what the admin typed', () => {
  assert.equal(m.keepTypedPercent('', '12'), '12');
  assert.equal(m.keepTypedPercent('   ', '12'), '12');
  assert.equal(m.keepTypedPercent('12,5', '12'), '12,5');
  assert.equal(m.keepTypedPercent('12,5', ''), '12,5');
  assert.equal(m.keepTypedPercent('', ''), '');
});

test('identical override detection and missing defaults', () => {
  const rows = [{ id: '2', vertical: 'CAR', supplierOrganizationId: 'o1', rate: 0.1 }, { id: '1', vertical: 'HOTEL', supplierOrganizationId: null, rate: 0.12 }];
  assert.equal(m.hasIdenticalOverride(rows, 'CAR', 'o1', 0.1), true);
  assert.equal(m.hasIdenticalOverride(rows, 'CAR', 'o1', 0.11), false);
  assert.equal(m.hasIdenticalOverride(rows, 'CAR', 'o2', 0.1), false);
  assert.deepEqual(m.verticalsWithoutDefault(rows), ['CAR', 'FLIGHT', 'BUS']);
});
