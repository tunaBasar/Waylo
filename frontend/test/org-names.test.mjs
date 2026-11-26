import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/org-names.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const m = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('org label uses the name, else the short id', () => {
  const map = m.orgNameMap([[{ id: 'aaaaaaaa-1111', name: 'Kaya Otelcilik' }], [{ id: 'bbbbbbbb-2222', name: 'Ege' }]]);
  assert.equal(m.orgLabel(map, 'aaaaaaaa-1111'), 'Kaya Otelcilik');
  assert.equal(m.orgLabel(map, 'cccccccc-3333-4444'), 'cccccccc...');
});
