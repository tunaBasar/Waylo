import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/order-text.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { displayItemText } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('old ISO hotel range is rewritten', () => {
  assert.equal(displayItemText('2026-11-27-2026-11-29, 2 gece, 1 oda'), '27.11.2026-29.11.2026, 2 gece, 1 oda');
});

test('new descriptions and other text stay unchanged', () => {
  const nu = 'Waylo Boutique Antalya · Standart oda · 27.11.2026-29.11.2026, 2 gece, 1 oda';
  assert.equal(displayItemText(nu), nu);
  assert.equal(displayItemText('Fiat Egea (Ekonomi), 3 gun'), 'Fiat Egea (Ekonomi), 3 gun');
  assert.equal(displayItemText('2026-11-27'), '2026-11-27');
  assert.equal(displayItemText(null), '');
});
