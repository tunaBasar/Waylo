import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/guest.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { guestLine, guestNameOrUndefined } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('name with adults and children', () => {
  assert.equal(guestLine('Ali', 'Yilmaz', 2, 1), 'Misafir: Ali Yilmaz · 2 yetiskin, 1 cocuk');
});
test('no children, no name', () => {
  assert.equal(guestLine(null, null, 2, 0), 'Misafir: 2 yetiskin');
  assert.equal(guestLine('Ali', null, null, null), 'Misafir: Ali');
});
test('nothing known gives empty line', () => {
  assert.equal(guestLine(null, null, null, null), '');
});
test('guestNameOrUndefined trims and omits blanks', () => {
  assert.equal(guestNameOrUndefined('  Ali '), 'Ali');
  assert.equal(guestNameOrUndefined('   '), undefined);
  assert.equal(guestNameOrUndefined(null), undefined);
});
