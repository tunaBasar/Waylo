import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

async function load() {
  const source = await readFile(new URL('../src/lib/email-verification.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('verifyEmailSentPath encodes the email', async () => {
  const { verifyEmailSentPath } = await load();
  assert.equal(verifyEmailSentPath('a+b@x.com'), '/verify-email-sent?email=a%2Bb%40x.com');
});

test('isEmailNotVerified matches only AuthError with the code', async () => {
  const { AuthError, isEmailNotVerified, EMAIL_NOT_VERIFIED } = await load();
  assert.equal(isEmailNotVerified(new AuthError('x', EMAIL_NOT_VERIFIED)), true);
  assert.equal(isEmailNotVerified(new AuthError('x')), false);
  assert.equal(isEmailNotVerified(new Error('x')), false);
});

test('verifiedLoginPath points to login with a message', async () => {
  const { verifiedLoginPath } = await load();
  assert.match(verifiedLoginPath(), /^\/login\?message=/);
});
