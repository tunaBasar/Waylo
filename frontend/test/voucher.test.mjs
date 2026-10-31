import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/voucher.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
});
const m = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

const ID = '123e4567-e89b-12d3-a456-426614174000';
const CODE = 'AbCdEfGhIjKlMnOpQrStUv';

test('parses a valid payload and trims whitespace', () => {
  assert.deepEqual(m.parseVoucherPayload(`  WAYLO1:${ID}:${CODE}\n`), { voucherId: ID, code: CODE });
});

test('rejects malformed payloads', () => {
  for (const bad of [
    '', null, undefined, 'hello', `WAYLO2:${ID}:${CODE}`, `waylo1:${ID}:${CODE}`,
    `WAYLO1:${ID}`, `WAYLO1:${ID}:${CODE}:x`, `WAYLO1:not-a-uuid:${CODE}`,
    `WAYLO1:${ID}:short`, `WAYLO1:${ID}:${CODE}!`, `WAYLO1:${ID}:${CODE}extra`,
  ]) {
    assert.equal(m.parseVoucherPayload(bad), null, String(bad));
  }
});

test('maps error codes to Turkish messages with fallback', () => {
  for (const c of ['INVALID_VOUCHER', 'WRONG_SUPPLIER', 'NOT_YET_VALID', 'EXPIRED', 'VOID', 'ALREADY_REDEEMED']) {
    assert.notEqual(m.redeemErrorMessage(c), 'Kupon okutulamadi.', c);
  }
  assert.equal(m.redeemErrorMessage('SOMETHING_ELSE'), 'Kupon okutulamadi.');
  assert.equal(m.redeemErrorMessage(null, 'x'), 'x');
});
