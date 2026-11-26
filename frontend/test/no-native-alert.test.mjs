import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

// The panel uses toasts (components/Toast) for errors; a native alert() blocks the page and is not Turkish-styled.
// The last three call sites (partner bus toggle, car VehicleForm, car one-way routes) were replaced on 26.11.
const SRC = fileURLToPath(new URL('../src', import.meta.url));

async function sourceFiles(dir) {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await sourceFiles(path)));
    else if (/\.(ts|tsx)$/.test(entry.name)) out.push(path);
  }
  return out;
}

test('no native alert() in the web app source', async () => {
  const offenders = [];
  for (const file of await sourceFiles(SRC)) {
    const lines = (await readFile(file, 'utf8')).split('\n');
    lines.forEach((line, i) => {
      if (/(^|[^.\w])alert\(/.test(line) && !/^\s*(\/\/|\*)/.test(line)) offenders.push(`${file.slice(SRC.length + 1)}:${i + 1}`);
    });
  }
  assert.deepEqual(offenders, []);
});
