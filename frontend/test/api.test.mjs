import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

// Exercise the actual transport source, compiled with the project's TypeScript dependency.
// Each import has isolated module state, including the single-flight refresh promise.
let sequence = 0;
async function transport() {
  const source = await readFile(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}#${sequence++}`);
}

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

async function withBrowser(run) {
  const originals = { fetch: globalThis.fetch, window: globalThis.window, localStorage: globalThis.localStorage };
  const storage = new Map([['waylo_token', 'old-access'], ['waylo_refresh', 'old-refresh']]);
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key),
  };
  globalThis.window = { location: { href: '' } };
  try { await run(await transport(), storage); }
  finally {
    for (const [key, value] of Object.entries(originals)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  }
}

test('DELETE accepts a successful 204 with no JSON body', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async (_url, options) => {
      assert.equal(options.method, 'DELETE');
      return new Response(null, { status: 204 });
    };
    assert.equal(await api.del('/api/example/1'), undefined);
  });
});

test('PATCH preserves payload and bearer header', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async (url, options) => {
      assert.ok(url.endsWith('/api/example/1'));
      assert.equal(options.method, 'PATCH');
      assert.equal(options.headers.Authorization, 'Bearer old-access');
      assert.deepEqual(JSON.parse(options.body), { description: '' });
      return json({ success: true, data: { description: '' } });
    };
    assert.equal((await api.patch('/api/example/1', { description: '' })).data.description, '');
  });
});

test('typed errors retain backend status/code without message guessing', async () => {
  await withBrowser(async ({ api, ApiError }) => {
    globalThis.fetch = async () => json({ message: 'Resource was modified concurrently', error: 'CONCURRENT_MODIFICATION' }, 409);
    await assert.rejects(api.get('/api/example'), error => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 409);
      assert.equal(error.code, 'CONCURRENT_MODIFICATION');
      return true;
    });
    globalThis.fetch = async () => json({ message: 'Unsupported version format', error: 'VALIDATION_ERROR' }, 400);
    await assert.rejects(api.get('/api/example'), error => {
      assert.equal(error.status, 400);
      assert.equal(error.code, 'VALIDATION_ERROR');
      return true;
    });
  });
});

test('non-JSON error responses still produce a typed error', async () => {
  await withBrowser(async ({ api, ApiError }) => {
    globalThis.fetch = async () => new Response('upstream unavailable', { status: 503, statusText: 'Service Unavailable' });
    await assert.rejects(api.get('/api/example'), error => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 503);
      assert.equal(error.code, null);
      assert.equal(error.message, 'Service Unavailable');
      return true;
    });
  });
});

test('concurrent 401s refresh once and retry using rotated access token', async () => {
  await withBrowser(async ({ api }, storage) => {
    let refreshes = 0;
    let retries = 0;
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/api/auth/refresh')) {
        refreshes++;
        assert.equal(JSON.parse(options.body).refreshToken, 'old-refresh');
        await new Promise(resolve => setTimeout(resolve, 10));
        return json({ success: true, data: { token: 'new-access', refreshToken: 'new-refresh' } });
      }
      if (options.headers.Authorization === 'Bearer old-access') return json({}, 401);
      assert.equal(options.headers.Authorization, 'Bearer new-access');
      retries++;
      return json({ success: true, data: url });
    };
    const results = await Promise.all([api.get('/api/one'), api.get('/api/two')]);
    assert.equal(results.length, 2);
    assert.equal(refreshes, 1);
    assert.equal(retries, 2);
    assert.equal(storage.get('waylo_refresh'), 'new-refresh');
  });
});

test('a retried failure retains its typed status and code', async () => {
  await withBrowser(async ({ api, ApiError }) => {
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/api/auth/refresh')) return json({ success: true, data: { token: 'new-access', refreshToken: 'new-refresh' } });
      if (options.headers.Authorization === 'Bearer old-access') return json({}, 401);
      return json({ message: 'Conflict', error: 'CONCURRENT_MODIFICATION' }, 409);
    };
    await assert.rejects(api.put('/api/example', {}), error => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 409);
      assert.equal(error.code, 'CONCURRENT_MODIFICATION');
      return true;
    });
  });
});

test('failed refresh clears stored session and redirects to login', async () => {
  await withBrowser(async ({ api }, storage) => {
    storage.set('waylo_user', '{}');
    globalThis.fetch = async () => json({ message: 'Unauthorized' }, 401);
    await assert.rejects(api.get('/api/example'));
    assert.equal(storage.has('waylo_token'), false);
    assert.equal(storage.has('waylo_refresh'), false);
    assert.equal(storage.has('waylo_user'), false);
    assert.equal(window.location.href, '/login');
  });
});

test('403 ADMIN_CANNOT_PURCHASE maps to the customer-account message', async () => {
  await withBrowser(async ({ api, ApiError, ADMIN_CANNOT_PURCHASE_MESSAGE }) => {
    globalThis.fetch = async () => json({ message: 'raw backend text', error: 'ADMIN_CANNOT_PURCHASE' }, 403);
    await assert.rejects(api.post('/api/orders/hotel', {}), err => {
      assert.ok(err instanceof ApiError);
      assert.equal(err.status, 403);
      assert.equal(err.code, 'ADMIN_CANNOT_PURCHASE');
      assert.equal(err.message, ADMIN_CANNOT_PURCHASE_MESSAGE);
      return true;
    });
  });
});
