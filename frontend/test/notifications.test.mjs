import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

let sequence = 0;
async function mod() {
  const source = await readFile(new URL('../src/lib/notifications.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

test('canRetry only for FAILED and GAVE_UP', async () => {
  const m = await mod();
  assert.equal(m.canRetry('FAILED'), true);
  assert.equal(m.canRetry('GAVE_UP'), true);
  for (const s of ['SENT', 'PENDING', 'RETRYING', 'SUPPRESSED']) assert.equal(m.canRetry(s), false);
});

test('filterPath maps tabs to endpoints', async () => {
  const m = await mod();
  assert.equal(m.filterPath('ALL'), '/api/notifications');
  assert.equal(m.filterPath('FAILED'), '/api/notifications/status/FAILED');
  assert.equal(m.filterPath('GAVE_UP'), '/api/notifications/status/GAVE_UP');
  assert.equal(m.filterPath('SENT'), '/api/notifications/status/SENT');
  assert.equal(m.filterPath('SUPPRESSED'), '/api/notifications/status/SUPPRESSED');
});

test('nextAttemptText handles null, invalid and valid instants', async () => {
  const m = await mod();
  assert.equal(m.nextAttemptText(null), 'planlanmadı');
  assert.equal(m.nextAttemptText(undefined), 'planlanmadı');
  assert.equal(m.nextAttemptText('garbage'), 'planlanmadı');
  assert.equal(m.nextAttemptText('2026-11-24T09:00:00Z'), new Date('2026-11-24T09:00:00Z').toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }));
});

test('status map has GAVE_UP error tone and all tabs', async () => {
  const m = await mod();
  assert.equal(m.NOTIFICATION_STATUS.GAVE_UP.label, 'Vazgeçildi (5 deneme)');
  assert.equal(m.NOTIFICATION_STATUS.GAVE_UP.tone, 'error');
  assert.equal(Object.keys(m.NOTIFICATION_STATUS).length, 6);
  assert.deepEqual(m.NOTIFICATION_TABS.map((t) => t.key), ['ALL', 'FAILED', 'GAVE_UP', 'SENT', 'SUPPRESSED']);
});
