import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/use-single-flight.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const js = outputText.replace("import { useState } from 'react';", 'const useState = () => [null];');
const { createSingleFlight } = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`);

test('two synchronous calls run fn once', async () => {
  const run = createSingleFlight();
  let n = 0;
  const fn = async () => { n++; await new Promise(r => setTimeout(r, 5)); return 'ok'; };
  const [a, b] = await Promise.all([run('k', fn), run('k', fn)]);
  assert.equal(n, 1);
  assert.equal(a, 'ok');
  assert.equal(b, undefined);
});

test('can run again after completion', async () => {
  const run = createSingleFlight();
  let n = 0;
  await run('k', async () => { n++; });
  await run('k', async () => { n++; });
  assert.equal(n, 2);
});

test('different keys run in parallel', async () => {
  const run = createSingleFlight();
  let n = 0;
  const fn = async () => { n++; await new Promise(r => setTimeout(r, 5)); };
  await Promise.all([run('a', fn), run('b', fn)]);
  assert.equal(n, 2);
});

test('rejection releases the key and rethrows', async () => {
  const run = createSingleFlight();
  await assert.rejects(run('k', async () => { throw new Error('boom'); }), /boom/);
  assert.equal(await run('k', async () => 1), 1);
});
