import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/code.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { normalizeCode, codeHint } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('normalizeCode drops everything but A-Z 0-9 _', () => {
  assert.equal(normalizeCode('dbl-tas'), 'DBLTAS');
  assert.equal(normalizeCode('Kahvalti_Esnek'), 'KAHVALTI_ESNEK');
  assert.equal(normalizeCode('a b'), 'AB');
});

test('codeHint only when typing changes the code', () => {
  assert.equal(codeHint('DBL-TAS'), 'Kaydedilecek kod: DBLTAS');
  assert.equal(codeHint('dbl_tas'), null);
  assert.equal(codeHint('---'), 'Kaydedilecek kod: (bos)');
});
