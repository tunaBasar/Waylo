import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/partner.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error {}\nconst api = {};\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

test('validatePhotoFile accepts the three types within limits', async () => {
  const m = await mod();
  for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
    assert.equal(m.validatePhotoFile({ type, size: 1000 }, 0), null);
  }
  assert.equal(m.validatePhotoFile({ type: 'image/jpeg', size: 5 * 1024 * 1024 }, 19), null);
});

test('validatePhotoFile rejects type, size and count', async () => {
  const m = await mod();
  assert.match(m.validatePhotoFile({ type: 'application/pdf', size: 10 }, 0), /JPEG/);
  assert.match(m.validatePhotoFile({ type: 'image/gif', size: 10 }, 0), /JPEG/);
  assert.match(m.validatePhotoFile({ type: 'image/png', size: 5 * 1024 * 1024 + 1 }, 0), /5 MB/);
  assert.match(m.validatePhotoFile({ type: 'image/png', size: 0 }, 0), /bos/);
  assert.match(m.validatePhotoFile({ type: 'image/png', size: 10 }, 20), /20/);
});

test('moveId moves and clamps', async () => {
  const m = await mod();
  assert.deepEqual(m.moveId(['a', 'b', 'c'], 'c', -1), ['a', 'c', 'b']);
  assert.deepEqual(m.moveId(['a', 'b', 'c'], 'a', 1), ['b', 'a', 'c']);
  const same = ['a', 'b'];
  assert.equal(m.moveId(same, 'a', -1), same);
  assert.equal(m.moveId(same, 'zzz', 1), same);
});

test('status labels come from the three review states', async () => {
  const m = await mod();
  assert.equal(m.MEDIA_STATUS_LABEL.PENDING, 'Incelemede');
  assert.equal(m.MEDIA_STATUS_TONE.REJECTED, 'error');
  assert.equal(Object.keys(m.MEDIA_STATUS_LABEL).length, 3);
});
