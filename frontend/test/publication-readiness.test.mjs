import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/publication-readiness.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { readinessLines } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('readiness lines count live photos and pending ones', () => {
  const photos = [{ reviewStatus: 'PENDING' }, { reviewStatus: 'APPROVED' }, { reviewStatus: 'PENDING', deleted: true }];
  assert.deepEqual(readinessLines(2, 1, photos), ['Oda tipi: 2', 'Fiyat plani: 1', 'Fotograf: 2 (incelemede 1)']);
});

test('unknown room/plan counts are omitted', () => {
  assert.deepEqual(readinessLines(null, null, []), ['Fotograf: 0 (incelemede 0)']);
});
