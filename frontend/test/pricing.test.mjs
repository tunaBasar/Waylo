import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/pricing.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{[^}]+\} from '\.\/api';\n?/, 'const api = {};\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}
const m = await mod();

test('totalDiscount sums lines and tolerates empty', () => {
  assert.equal(m.totalDiscount([{ amount: 10 }, { amount: 2.5 }]), 12.5);
  assert.equal(m.totalDiscount(null), 0);
});

test('campaignActive is start inclusive, end exclusive', () => {
  const c = { startAt: '2026-11-01T00:00:00', endAt: '2026-11-02T00:00:00' };
  assert.equal(m.campaignActive(c, new Date('2026-11-01T00:00:00')), true);
  assert.equal(m.campaignActive(c, new Date('2026-11-02T00:00:00')), false);
  assert.equal(m.campaignActive(c, new Date('2026-10-31T23:59:59')), false);
});

test('withPromo adds the normalized code only when typed', () => {
  assert.deepEqual(m.withPromo({ a: 1 }, '  save10 '), { a: 1, promoCode: 'SAVE10' });
  assert.deepEqual(m.withPromo({ a: 1 }, '   '), { a: 1 });
});

test('discountLabel', () => {
  assert.equal(m.discountLabel({ discountType: 'PERCENTAGE', discountValue: 10 }), '%10');
  assert.equal(m.discountLabel({ discountType: 'FIXED', discountValue: 50 }, 'TRY'), '50.00 TRY');
});

test('promoErrorMessage maps promo codes and ignores others', () => {
  assert.match(m.promoErrorMessage('PROMO_CODE_INVALID'), /gecersiz/);
  assert.match(m.promoErrorMessage('PRICING_UNAVAILABLE'), /dogrulanamiyor/);
  assert.equal(m.promoErrorMessage('SOMETHING_ELSE'), null);
  assert.equal(m.promoErrorMessage(null), null);
});
