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

test('admin publication detail: media is carried through and defaults to []', async () => {
  const { flattenPublicationDetail } = await mod();
  const base = { request: { id: 'r3', hotelId: 'h3', status: 'SUBMITTED', version: 1 }, hotel: { name: 'C' }, revision: null, diff: [], decisions: [] };
  const media = [{ id: 'm1', reviewStatus: 'PENDING' }];
  assert.deepEqual(flattenPublicationDetail({ ...base, media }).media, media);
  assert.deepEqual(flattenPublicationDetail(base).media, []);
  assert.deepEqual(flattenPublicationDetail({ ...base, media: null }).media, []);
});

async function partnerMod() {
  const source = await readFile(new URL('../src/lib/partner.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'class ApiError extends Error { constructor(m, status, code) { super(m); this.status = status; this.code = code; } }\nconst api = {};\n')
    .replace(/(\nexport \{ ApiError)/, '$1');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  const m = await import(`data:text/javascript;base64,${Buffer.from(outputText + '\nexport { ApiError as __ApiError };').toString('base64')}`);
  return m;
}

test('pendingPhotoIds: only non-deleted PENDING photos, sorted by sortOrder', async () => {
  const { pendingPhotoIds, MAX_REQUEST_MEDIA } = await partnerMod();
  assert.equal(MAX_REQUEST_MEDIA, 20);
  const ids = pendingPhotoIds([
    { id: 'c', sortOrder: 2, reviewStatus: 'PENDING' },
    { id: 'a', sortOrder: 0, reviewStatus: 'PENDING' },
    { id: 'x', sortOrder: 1, reviewStatus: 'APPROVED' },
    { id: 'y', sortOrder: 1, reviewStatus: 'REJECTED' },
    { id: 'd', sortOrder: 3, reviewStatus: 'PENDING', deletedAt: '2026-11-24T10:00:00Z' },
    { id: 'b', sortOrder: 1, reviewStatus: 'PENDING' },
  ]);
  assert.deepEqual(ids, ['a', 'b', 'c']);
  assert.deepEqual(pendingPhotoIds([]), []);
});

test('publicationMediaErrorMessage maps MEDIA_NOT_FOUND and MEDIA_NOT_PENDING', async () => {
  const { publicationMediaErrorMessage, __ApiError: ApiError } = await partnerMod();
  assert.match(publicationMediaErrorMessage(new ApiError('x', 404, 'MEDIA_NOT_FOUND'), 'f'), /bulunamadi/);
  assert.match(publicationMediaErrorMessage(new ApiError('x', 409, 'MEDIA_NOT_PENDING'), 'f'), /incelemede degil/);
  assert.equal(publicationMediaErrorMessage(new ApiError('baska', 500, 'X'), 'f'), 'baska');
  assert.equal(publicationMediaErrorMessage('nope', 'f'), 'f');
});
