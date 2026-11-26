import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

async function mod() {
  const source = await readFile(new URL('../src/lib/hotel-media.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('hotelCoverUrl builds the public media url', async () => {
  const { hotelCoverUrl } = await mod();
  assert.equal(hotelCoverUrl('http://h:8080', { id: 'a', coverMediaId: 'b' }), 'http://h:8080/api/hotels/a/media/b');
  assert.equal(hotelCoverUrl('http://h/', { id: 'a', coverMediaId: 'b' }), 'http://h/api/hotels/a/media/b');
});

test('hotelCoverUrl is null without a cover', async () => {
  const { hotelCoverUrl } = await mod();
  assert.equal(hotelCoverUrl('http://h', { id: 'a' }), null);
  assert.equal(hotelCoverUrl('http://h', { id: 'a', coverMediaId: null }), null);
});
