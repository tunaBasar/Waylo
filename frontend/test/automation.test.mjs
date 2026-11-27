import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/automation.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { deriveOccupancyPrices, automationErrorMessage, AUTOMATION_ERROR_TEXT, summaryMessage } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

const rule = { maxOccupancy: 4, baseOccupancy: 2, baseAmount: 4000, extraPersonAmount: 800, reducedPersonAmount: 500 };

test('deriveOccupancyPrices: extra above and reduced below the base occupancy', () => {
  const { prices, unpriced } = deriveOccupancyPrices(rule);
  assert.deepEqual(prices, [
    { occupancy: 1, amount: 3500 },
    { occupancy: 2, amount: 4000 },
    { occupancy: 3, amount: 4800 },
    { occupancy: 4, amount: 5600 },
  ]);
  assert.deepEqual(unpriced, []);
});

test('deriveOccupancyPrices: weekend base is used only when asked and set', () => {
  const r = { ...rule, weekendBaseAmount: 5000 };
  assert.equal(deriveOccupancyPrices(r).prices[1].amount, 4000);
  assert.equal(deriveOccupancyPrices(r, true).prices[1].amount, 5000);
  assert.equal(deriveOccupancyPrices(rule, true).prices[1].amount, 4000);
});

test('deriveOccupancyPrices: zero or negative amounts are listed as unpriced, not priced', () => {
  const { prices, unpriced } = deriveOccupancyPrices({ maxOccupancy: 3, baseOccupancy: 3, baseAmount: 100, extraPersonAmount: 0, reducedPersonAmount: 50 });
  assert.deepEqual(unpriced, [1]);
  assert.deepEqual(prices.map(p => p.occupancy), [2, 3]);
});

test('deriveOccupancyPrices: cents arithmetic has no floating point drift', () => {
  const { prices } = deriveOccupancyPrices({ maxOccupancy: 3, baseOccupancy: 1, baseAmount: 0.1, extraPersonAmount: 0.2, reducedPersonAmount: 0 });
  assert.deepEqual(prices.map(p => p.amount), [0.1, 0.3, 0.5]);
});

test('automationErrorMessage: every contract code has a Turkish text', () => {
  for (const code of ['RULE_OVERLAP', 'INVALID_DATE_RANGE', 'OCCUPANCY_RULE', 'CURRENCY_MISMATCH']) {
    assert.ok(AUTOMATION_ERROR_TEXT[code], code);
    assert.equal(automationErrorMessage({ code, message: 'English text' }, 'x'), AUTOMATION_ERROR_TEXT[code]);
  }
  assert.match(automationErrorMessage({ code: 'CONCURRENT_MODIFICATION', status: 409 }, 'x'), /degistirildi/);
});

test('automationErrorMessage: 403, unknown codes and non-errors', () => {
  assert.equal(automationErrorMessage({ status: 403, code: 'ACCESS_DENIED', message: 'Not allowed' }, 'x'), 'Bu islem icin yetkiniz yok.');
  assert.equal(automationErrorMessage({ code: 'SOMETHING', message: 'Backend text' }, 'x'), 'Backend text');
  assert.equal(automationErrorMessage(null, 'Yedek'), 'Yedek');
});

test('summaryMessage: matches the contract example and handles empty runs', () => {
  assert.equal(
    summaryMessage({ created: 1560, updated: 0, skippedManual: 12, inventoryCreated: 120, ratesCreated: 1440 }),
    '120 gece stok, 1.440 fiyat olusturuldu; 12 elle degistirilmis gun korundu',
  );
  assert.equal(summaryMessage({ created: 0, updated: 0, skippedManual: 0 }), 'Yeni olusturulacak gun yok, her sey guncel');
});

test('visibleFormErrors: nothing on a fresh form, touched fields only, everything after save', async () => {
  const { visibleFormErrors } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#v`);
  const errors = { name: 'Kural adi gerekli', base: 'Gecersiz tutar' };
  assert.deepEqual(visibleFormErrors(errors, new Set(), false), {});
  assert.deepEqual(visibleFormErrors(errors, new Set(['base']), false), { base: 'Gecersiz tutar' });
  assert.deepEqual(visibleFormErrors(errors, new Set(), true), errors);
  assert.deepEqual(visibleFormErrors({}, new Set(['name']), true), {});
});
