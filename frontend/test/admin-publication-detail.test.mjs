import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// lib/admin.ts imports the api client; only the pure flattenPublicationDetail helper is exercised here.
async function mod() {
  const source = await readFile(new URL('../src/lib/admin.ts', import.meta.url), 'utf8');
  const stripped = source
    .replace(/import \{ api \} from '\.\/api';\n?/, 'const api = {};\n')
    .replace(/import type [^;]+;\n/g, '');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('admin publication detail: nested backend shape is flattened for the decision panel', async () => {
  const { flattenPublicationDetail } = await mod();
  const wire = {
    request: { id: 'r1', hotelId: 'h1', organizationId: 'o1', kind: 'INITIAL', status: 'SUBMITTED', revisionId: null,
      submittedBy: 'u1', submittedAt: '2026-11-24T10:00:00Z', note: null, version: 3, hotelPublicationStatus: 'SUBMITTED' },
    hotel: { name: 'Otel A', city: 'Antalya' },
    revision: null,
    diff: null,
    decisions: [{ decision: 'SUBMIT', fromStatus: 'DRAFT', toStatus: 'SUBMITTED', actorRole: 'PARTNER', reason: null, createdAt: 'x' }],
  };
  const d = flattenPublicationDetail(wire);
  assert.equal(d.requestId, 'r1');
  assert.equal(d.status, 'SUBMITTED'); // drives the Onayla / Reddet buttons
  assert.equal(d.version, 3);          // sent back as expectedVersion
  assert.equal(d.hotelId, 'h1');
  assert.equal(d.hotelName, 'Otel A');
  assert.deepEqual(d.diff, []);
  assert.equal(d.decisions.length, 1);
  assert.equal('request' in d, false);
});

test('admin publication detail: an already flat object is returned unchanged', async () => {
  const { flattenPublicationDetail } = await mod();
  const flat = { requestId: 'r2', status: 'APPROVED', version: 1, hotelId: 'h2', hotelName: 'B', diff: [], decisions: [] };
  assert.equal(flattenPublicationDetail(flat), flat);
});
