import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/partner.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error {}\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

test('status labels cover all publication statuses', async () => {
  const m = await mod();
  assert.equal(m.PUBLICATION_STATUS_LABEL.DRAFT, 'Taslak');
  assert.equal(m.PUBLICATION_STATUS_LABEL.PUBLISHED, 'Yayinda');
  assert.equal(Object.keys(m.PUBLICATION_STATUS_LABEL).length, 7);
  assert.equal(m.PUBLICATION_STATUS_TONE.PUBLISHED, 'success');
});

test('canManageHotel excludes staff and other hotels', async () => {
  const m = await mod();
  assert.equal(m.canManageHotel([{ role: 'ORGANIZATION_OWNER', scopeType: 'ORGANIZATION', scopeId: 'o' }], 'h1'), true);
  assert.equal(m.canManageHotel([{ role: 'HOTEL_MANAGER', scopeType: 'HOTEL', scopeId: 'h1' }], 'h1'), true);
  assert.equal(m.canManageHotel([{ role: 'HOTEL_MANAGER', scopeType: 'HOTEL', scopeId: 'h2' }], 'h1'), false);
  assert.equal(m.canManageHotel([{ role: 'HOTEL_STAFF', scopeType: 'HOTEL', scopeId: 'h1' }], 'h1'), false);
});

test('submitBlockReason and contentDiff', async () => {
  const m = await mod();
  assert.equal(m.submitBlockReason('ACTIVE', true), null);
  assert.ok(m.submitBlockReason('PENDING', true));
  assert.ok(m.submitBlockReason('ACTIVE', false));
  const base = { name: 'A', city: 'C', country: 'TR', address: 'x', starRating: 3, description: null, latitude: null, longitude: null };
  assert.deepEqual(m.contentDiff(base, { ...base, description: 'd', starRating: 5 }), { starRating: 5, description: 'd' });
  assert.deepEqual(m.contentDiff(base, base), {});
});
