import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// scoreLabel is a tiny pure helper, checked on the source to avoid a TS toolchain in node --test.
const src = readFileSync(new URL('../src/lib/reviews.ts', import.meta.url), 'utf8');

test('scoreLabel renders null for no reviews and one decimal otherwise', () => {
  const fn = new Function(`${src.slice(src.indexOf('export function scoreLabel')).replace('export function scoreLabel(score: number | null | undefined): string | null', 'function scoreLabel(score)')}; return scoreLabel;`)();
  assert.equal(fn(null), null);
  assert.equal(fn(undefined), null);
  assert.equal(fn(4.5), '4.5');
  assert.equal(fn(4), '4.0');
});
