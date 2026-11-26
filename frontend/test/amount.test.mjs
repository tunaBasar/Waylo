import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/amount.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { parseTrAmount } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('accepted forms', () => {
  const ok = { '2400': 2400, '2400,5': 2400.5, '2400,50': 2400.5, '2400.50': 2400.5, '2400.5': 2400.5, '2.400': 2400,
    '2.400,50': 2400.5, '12.345.678,90': 12345678.9, '2 400': 2400, '  2400  ': 2400, '0,5': 0.5, '1.000': 1000 };
  for (const [raw, v] of Object.entries(ok)) assert.equal(parseTrAmount(raw), v, raw);
});

test('rejected forms', () => {
  for (const raw of ['2,400.50', '2.40.0', 'abc', '', '   ', '0', '0,00', '-5', '-2.400', '2400,555', '2400.555.1', '1,2,3', '2.4000', '.5', ',5', '12.34.567', '1e5', null, undefined]) {
    assert.equal(parseTrAmount(raw), null, String(raw));
  }
});
