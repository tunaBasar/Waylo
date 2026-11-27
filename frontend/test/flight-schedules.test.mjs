import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = (await readFile(new URL('../src/lib/flight-schedules.ts', import.meta.url), 'utf8'))
  .replace("import { api } from './api';", 'const api = {};');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { flightNumberPreview, weekdayLabel, weekdaysSummary, scheduleErrorText, summaryText, DEFAULT_CABINS, WEEKDAYS } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('flightNumberPreview: code + yy + 3 digit day of year', () => {
  assert.equal(flightNumberPreview('WD1', '2026-11-27'), 'WD126331');
  assert.equal(flightNumberPreview('wd1', '2026-11-27'), 'WD126331');
  assert.equal(flightNumberPreview('WD1', '2027-01-01'), 'WD127001');
  assert.equal(flightNumberPreview('WD1', '2026-12-31'), 'WD126365');
  assert.equal(flightNumberPreview('WD1', '2028-12-31'), 'WD128366');
  assert.equal(flightNumberPreview('WD123', '2026-11-27'), 'WD12326331');
});

test('flightNumberPreview: invalid input gives null', () => {
  assert.equal(flightNumberPreview('W1', '2026-11-27'), null);
  assert.equal(flightNumberPreview('WD1234', '2026-11-27'), null);
  assert.equal(flightNumberPreview('WD1', '2026-02-30'), null);
  assert.equal(flightNumberPreview('WD1', ''), null);
});

test('weekday labels and summary keep week order', () => {
  assert.equal(WEEKDAYS.length, 7);
  assert.equal(weekdayLabel('FRIDAY'), 'Cuma');
  assert.equal(weekdaysSummary(['SUNDAY', 'FRIDAY']), 'Cum, Paz');
  assert.equal(weekdaysSummary([]), '');
});

test('error texts are Turkish per code with fallbacks', () => {
  assert.match(scheduleErrorText('SCHEDULE_CODE_TAKEN'), /kullaniliyor/);
  assert.match(scheduleErrorText('INVALID_CABINS'), /Kabin/);
  assert.match(scheduleErrorText('SAME_AIRPORT'), /ayni/);
  assert.equal(scheduleErrorText('UNKNOWN_X', 'Sunucu mesaji'), 'Sunucu mesaji');
  assert.match(scheduleErrorText(null), /tekrar deneyin/);
});

test('default cabins and summary text', () => {
  assert.deepEqual(DEFAULT_CABINS.map(c => [c.seatType, c.fromRow, c.toRow, c.letters]),
    [['BUSINESS', '1', '2', 'AC'], ['ECONOMY', '3', '7', 'ABCD']]);
  assert.equal(summaryText(null), '');
  assert.match(summaryText({ created: 3, updated: 0, skippedExisting: 1, skippedManual: 0, skippedPast: 2 }), /3 ucus/);
});
