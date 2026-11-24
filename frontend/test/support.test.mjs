import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

async function mod() {
  const source = await readFile(new URL('../src/lib/support.ts', import.meta.url), 'utf8');
  const stripped = source.replace(/import \{ api \} from '\.\/api';\n?/, 'const api = {};\n');
  const { outputText } = ts.transpileModule(stripped, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('support: status labels and tones', async () => {
  const { STATUS_LABEL, STATUS_TONE } = await mod();
  assert.equal(STATUS_LABEL.OPEN, 'Açık');
  assert.equal(STATUS_LABEL.ANSWERED, 'Yanıtlandı');
  assert.equal(STATUS_LABEL.CLOSED, 'Kapalı');
  assert.equal(STATUS_TONE.CLOSED, 'neutral');
});

test('support: canReply is false on closed cases, canClose excludes partners', async () => {
  const { canReply, canClose } = await mod();
  for (const role of ['CUSTOMER', 'PARTNER', 'ADMIN']) {
    assert.equal(canReply('OPEN', role), true);
    assert.equal(canReply('ANSWERED', role), true);
    assert.equal(canReply('CLOSED', role), false);
  }
  assert.equal(canClose('OPEN', 'PARTNER'), false);
  assert.equal(canClose('OPEN', 'CUSTOMER'), true);
  assert.equal(canClose('CLOSED', 'ADMIN'), false);
});

test('support: validation mirrors backend limits', async () => {
  const { validateNewCase, validateMessage } = await mod();
  assert.deepEqual(validateNewCase('Konu', 'Mesaj'), { subject: null, message: null });
  assert.ok(validateNewCase('  ', 'x').subject);
  assert.ok(validateNewCase('x', '').message);
  assert.equal(validateNewCase('a'.repeat(150), 'x').subject, null);
  assert.ok(validateNewCase('a'.repeat(151), 'x').subject);
  assert.equal(validateMessage('a'.repeat(4000)), null);
  assert.ok(validateMessage('a'.repeat(4001)));
});

test('support: capabilitiesText is honest when the decision is pending', async () => {
  const { capabilitiesText } = await mod();
  const t = capabilitiesText({ liveChat: false, channels: [], hours: null, decisionPending: true });
  assert.equal(t, 'Destek kanalları ve çalışma saatleri henüz belirlenmedi; talebinize bu sayfadan yanıt verilir.');
  assert.ok(!/canlı/i.test(t));
  const d = capabilitiesText({ liveChat: false, channels: ['E-posta'], hours: '09-18', decisionPending: false });
  assert.match(d, /E-posta/);
  assert.match(d, /09-18/);
});
