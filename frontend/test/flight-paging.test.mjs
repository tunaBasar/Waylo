import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/flight-paging.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { flightPagingText, hasNextFlightPage, filterFlightsByNumber } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('flightPagingText shows total and visible range', () => {
  assert.equal(flightPagingText(510, 1, 50, 50), 'Toplam 510 ucus (51-100)');
  assert.equal(flightPagingText(0, 0, 50, 0), 'Toplam 0 ucus');
});

test('hasNextFlightPage', () => {
  assert.equal(hasNextFlightPage(510, 0, 50), true);
  assert.equal(hasNextFlightPage(100, 1, 50), false);
  assert.equal(hasNextFlightPage(101, 1, 50), true);
});

test('filterFlightsByNumber is case-insensitive and keeps all on empty query', () => {
  const fl = [{ flightNumber: 'WD1001' }, { flightNumber: 'WA22' }];
  assert.deepEqual(filterFlightsByNumber(fl, ' wd1 '), [fl[0]]);
  assert.equal(filterFlightsByNumber(fl, ''), fl);
});

test('flightPickerHint asks to narrow only when the server found more than one page holds', async () => {
  const { flightPickerHint } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#hint`);
  assert.equal(flightPickerHint(496, 200), '496 ucustan ilk 200 tanesi listelendi. Ucus no yazarak daraltin.');
  assert.equal(flightPickerHint(31, 31), null);
  assert.equal(flightPickerHint(0, 0), null);
});

test('flightDateRangeProblem rejects an end date before the start, allows open ranges', async () => {
  const { flightDateRangeProblem } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#range`);
  assert.equal(flightDateRangeProblem('2026-12-05', '2026-12-01'), 'Bitis tarihi baslangictan once olamaz.');
  assert.equal(flightDateRangeProblem('2026-12-01', '2026-12-01'), null);
  assert.equal(flightDateRangeProblem('2026-12-01', ''), null);
  assert.equal(flightDateRangeProblem('', '2026-12-01'), null);
  assert.equal(flightDateRangeProblem('', ''), null);
});
