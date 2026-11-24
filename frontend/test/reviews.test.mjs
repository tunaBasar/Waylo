import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// Pure helpers, loaded by transpiling the source (no TS toolchain needed beyond the typescript package).
const src = readFileSync(new URL('../src/lib/reviews.ts', import.meta.url), 'utf8');

async function reviewsModule() {
  const ts = (await import('typescript')).default;
  const stripped = src.replace(/import \{[^}]+\} from '@\/lib\/api';\n?/, 'const api = {};\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('scoreLabel renders null for no reviews and one decimal otherwise', async () => {
  const { scoreLabel: fn } = await reviewsModule();
  assert.equal(fn(null), null);
  assert.equal(fn(undefined), null);
  assert.equal(fn(4.5), '4.5');
  assert.equal(fn(4), '4.0');
});

test('review rejection codes map to Turkish reasons, unknown codes keep the server text', async () => {
  const m = await reviewsModule();
  assert.match(m.reviewErrorMessage('REVIEW_NOT_CHECKED_OUT', 'raw'), /check-out/);
  assert.match(m.reviewErrorMessage('REVIEW_NOT_CHECKED_OUT', 'raw'), /kaydedildikten sonra/);
  assert.match(m.reviewErrorMessage('REVIEW_NOT_CHECKED_IN', 'raw'), /check-in/);
  assert.match(m.reviewErrorMessage('REVIEW_CANCELLED_OR_NO_SHOW', 'raw'), /Iptal/);
  assert.match(m.reviewErrorMessage('REVIEW_ORDER_NOT_CONFIRMED', 'raw'), /onaylanmadigi/);
  assert.match(m.reviewErrorMessage('ORG_MEMBER_CANNOT_REVIEW', 'raw'), /isletmesinde calisan/);
  assert.equal(m.reviewErrorMessage('SOMETHING_NEW', 'Sunucu mesaji'), 'Sunucu mesaji');
  assert.equal(m.reviewErrorMessage(null, ''), 'Degerlendirme gonderilemedi');
  assert.equal(Object.keys(m.REVIEW_REASON_MESSAGE).length, 5);
});
