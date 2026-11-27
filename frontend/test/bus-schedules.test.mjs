import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/bus.ts', import.meta.url), 'utf8');
  const stub = "class ApiError extends Error { constructor(m, s, c) { super(m); this.status = s; this.code = c; } }\nconst api = {};\nconst SALES_CLOSED_MESSAGE = 'closed';\nexport { ApiError };\n";
  const stripped = stub + source.replace(/import \{[^}]+\} from '\.\/api';\n?/, '');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const m = await mod();
const stop = (arrival, departure, an = false, dn = false) =>
  ({ terminalId: 't', arrival, arrivalNextDay: an, departure, departureNextDay: dn });

test('scheduleOffsets: first stop is 0/0, others relative to the departure time', () => {
  const r = m.scheduleOffsets('22:30', [stop('', ''), stop('06:15', '06:15', true, true)]);
  assert.deepEqual(r.map(x => [x.arrivalOffsetMinutes, x.departureOffsetMinutes]), [[0, 0], [465, 465]]);
});

test('scheduleOffsets crossing midnight with +1 gun', () => {
  const r = m.scheduleOffsets('22:30', [stop('', ''), stop('00:30', '00:45', true, true), stop('06:00', '06:00', true, true)]);
  assert.deepEqual(r.map(x => x.arrivalOffsetMinutes), [0, 120, 450]);
  assert.deepEqual(r.map(x => x.departureOffsetMinutes), [0, 135, 450]);
});

test('scheduleOffsets: same day times need no marker, invalid times give null', () => {
  const r = m.scheduleOffsets('08:00', [stop('', ''), stop('12:00', '12:10'), stop('14:00', '14:00')]);
  assert.deepEqual(r.map(x => x.arrivalOffsetMinutes), [0, 240, 360]);
  assert.equal(m.scheduleOffsets('08:00', [stop('', ''), stop('', '')]), null);
  assert.equal(m.scheduleOffsets('25:00', [stop('', ''), stop('10:00', '10:00')]), null);
  assert.equal(m.scheduleOffsets('', [stop('', '')]), null);
});

test('weekdayLabel uses week order and short Turkish names', () => {
  assert.equal(m.weekdayLabel(['SUNDAY', 'MONDAY', 'FRIDAY']), 'Pzt Cum Paz');
  assert.equal(m.weekdayLabel([]), '');
  assert.equal(m.weekdayLabel(['MONDAY', 'MONDAY', 'NOPE']), 'Pzt');
});

test('schedule error codes map to Turkish text', () => {
  assert.match(m.busErrorMessage(new m.ApiError('m', 422, 'SCHEDULE_CODE_TAKEN'), 'f'), /plan kodu/i);
  assert.match(m.busErrorMessage(new m.ApiError('m', 422, 'INVALID_SCHEDULE'), 'f'), /gecersiz/i);
});

test('sortTripsForPartner: next departure first, then cancelled/past newest first, input untouched', () => {
  const trip = (id, status, departureAt) => ({ id, status, stops: [{ departureAt }] });
  const trips = [
    trip('far', 'PUBLISHED', '2026-12-26T22:30:00'),
    trip('past', 'PUBLISHED', '2026-11-20T22:30:00'),
    trip('next', 'PUBLISHED', '2026-11-27T22:30:00'),
    trip('cancelled', 'CANCELLED', '2026-11-28T22:30:00'),
    trip('soon', 'PUBLISHED', '2026-11-28T22:30:00'),
  ];
  const copy = JSON.stringify(trips);
  const got = m.sortTripsForPartner(trips, '2026-11-27T11:00:00').map(t => t.id);
  assert.deepEqual(got, ['next', 'soon', 'far', 'cancelled', 'past']);
  assert.equal(JSON.stringify(trips), copy);
});

test('istanbulNowLocal: UTC+3 wall time without offset', () => {
  assert.equal(m.istanbulNowLocal(Date.parse('2026-11-27T21:30:00Z')), '2026-11-28T00:30:00');
});
