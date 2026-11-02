import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/checkin.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const m = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

const dep = '2026-11-10T12:00:00Z';
const t = Date.parse(dep);
const H = 3600_000;

test('window edges are inclusive', () => {
  assert.equal(m.checkinWindow(dep, t - 24 * H).open, true);
  assert.equal(m.checkinWindow(dep, t - 24 * H - 1).open, false);
  assert.equal(m.checkinWindow(dep, t - H).open, true);
  assert.equal(m.checkinWindow(dep, t - H + 1).open, false);
});

test('closed states carry a reason', () => {
  assert.match(m.checkinWindow(dep, t - 48 * H).reason, /acilir/);
  assert.match(m.checkinWindow(dep, t).reason, /kapanir/);
  assert.match(m.checkinWindow(dep, t - 5 * H, 'DUFFEL').reason, /havayolu/);
  assert.match(m.checkinWindow(dep, t - 5 * H, 'INTERNAL', true).reason, /Iptal/);
});

test('done text', () => {
  assert.equal(m.checkinDoneText('3A'), 'Check-in yapildi, koltuk 3A');
});
