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
    globalThis.fetch = async () => new Response('upstream unavailable', { status: 500, statusText: 'Internal Server Error' });
    await assert.rejects(api.get('/api/example'), error => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 500);
      assert.equal(error.code, null);
      assert.equal(error.message, 'Internal Server Error');
      return true;
    });
  });
});

test('gateway 502/503/504 without a backend message gets the Turkish service-unavailable text, never the English phrase', async () => {
  await withBrowser(async ({ api, ApiError, SERVICE_UNAVAILABLE_MESSAGE }) => {
    // What the gateway really sends while a service restarts (2026-11-27 live: flight-service stopped).
    const gatewayBody = { timestamp: '2026-11-27T10:04:01.457Z', path: '/api/flights', status: 503, error: 'Service Unavailable', requestId: 'b801c890-259' };
    for (const [status, statusText] of [[502, 'Bad Gateway'], [503, 'Service Unavailable'], [504, 'Gateway Timeout']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ ...gatewayBody, status, error: statusText }), { status, statusText, headers: { 'Content-Type': 'application/json' } });
      await assert.rejects(api.get('/api/flights'), error => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.status, status);
        assert.equal(error.code, 'SERVICE_UNAVAILABLE');
        assert.equal(error.message, SERVICE_UNAVAILABLE_MESSAGE);
        return true;
      });
    }
    globalThis.fetch = async () => new Response('upstream unavailable', { status: 503, statusText: 'Service Unavailable' });
    await assert.rejects(api.get('/api/flights'), e => e instanceof ApiError && e.status === 503 && e.message === SERVICE_UNAVAILABLE_MESSAGE);
  });
});

test('a 503 that carries a backend message keeps that message and code', async () => {
  await withBrowser(async ({ api, ApiError }) => {
    globalThis.fetch = async () => json({ success: false, error: 'PAYMENT_SERVICE_UNAVAILABLE', message: 'Odeme servisi su an kullanilamiyor' }, 503);
    await assert.rejects(api.get('/api/example'), e => e instanceof ApiError && e.code === 'PAYMENT_SERVICE_UNAVAILABLE' && e.message === 'Odeme servisi su an kullanilamiyor');
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

test('422 COMMISSION_RATE_MISSING never shows commission wording to the customer', async () => {
  await withBrowser(async ({ api, ApiError, SALES_CLOSED_MESSAGE }) => {
    globalThis.fetch = async () => json({ message: 'No commission rate is configured for vertical HOTEL', error: 'COMMISSION_RATE_MISSING' }, 422);
    await assert.rejects(api.post('/api/payments/orders/x/intent', {}), err => {
      assert.ok(err instanceof ApiError);
      assert.equal(err.code, 'COMMISSION_RATE_MISSING');
      assert.equal(err.message, SALES_CLOSED_MESSAGE);
      assert.ok(!/komisyon|commission/i.test(err.message));
      return true;
    });
  });
});

test('postForm sends the FormData itself with no JSON Content-Type (car/bus document upload, 415 before)', async () => {
  await withBrowser(async ({ api }) => {
    const form = new FormData();
    form.append('type', 'TRANSPORT_AUTHORIZATION');
    form.append('file', new Blob(['%PDF-1.4'], { type: 'application/pdf' }), 'belge.pdf');
    globalThis.fetch = async (_url, options) => {
      assert.equal(options.method, 'POST');
      assert.equal(options.body, form, 'the FormData object must reach fetch, not a JSON string');
      assert.equal(options.headers['Content-Type'], undefined, 'the browser must set multipart/form-data with the boundary');
      assert.equal(options.headers.Authorization, 'Bearer old-access');
      return json({ success: true, data: { id: 'd1' } }, 201);
    };
    assert.equal((await api.postForm('/api/bus-partner/organizations/o1/documents', form)).data.id, 'd1');
  });
});

test('JSON requests keep the JSON Content-Type', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async (_url, options) => {
      assert.equal(options.headers['Content-Type'], 'application/json');
      return json({ success: true, data: null });
    };
    await api.post('/api/example', { a: 1 });
  });
});

test('getBlob returns the bytes of a PDF download instead of JSON-parsing them', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async (_url, options) => {
      assert.equal(options.headers['Content-Type'], undefined);
      assert.equal(options.headers.Authorization, 'Bearer old-access');
      return new Response(new Blob(['%PDF-1.4 demo'], { type: 'application/pdf' }), { status: 200 });
    };
    const blob = await api.getBlob('/api/partner/organizations/o1/documents/d1/file');
    assert.equal(await blob.text(), '%PDF-1.4 demo');
  });
});

test('network failure becomes ApiError status 0 NETWORK', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
    await assert.rejects(() => api.get('/api/example'), err => err.status === 0 && err.code === 'NETWORK' && err.name === 'ApiError');
    await assert.rejects(() => api.getBlob('/api/example'), err => err.status === 0 && err.code === 'NETWORK');
  });
});

test('network failure on the retry after refresh becomes NETWORK', async () => {
  await withBrowser(async ({ api }) => {
    let calls = 0;
    globalThis.fetch = async (url) => {
      calls++;
      if (url.endsWith('/api/auth/refresh')) return json({ success: true, data: { token: 'n', refreshToken: 'r' } });
      if (calls === 1) return json({ message: 'x' }, 401);
      throw new TypeError('Failed to fetch');
    };
    await assert.rejects(() => api.get('/api/example'), err => err.status === 0 && err.code === 'NETWORK');
  });
});

test('2xx with success=false throws with the backend code', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async () => json({ success: false, message: 'Olmadi', error: 'SOME_CODE' });
    await assert.rejects(() => api.get('/api/example'), err => err.status === 200 && err.code === 'SOME_CODE' && err.message === 'Olmadi');
    globalThis.fetch = async () => json({ success: false });
    await assert.rejects(() => api.post('/api/example', {}), err => err.code === 'UNSUCCESSFUL' && err.message === 'Islem tamamlanamadi');
  });
});

test('204 and empty body return undefined', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await api.get('/api/example'), undefined);
    globalThis.fetch = async () => new Response('', { status: 200 });
    assert.equal(await api.get('/api/example'), undefined);
  });
});

const paged = (data, total) => new Response(JSON.stringify({ success: true, data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json', ...(total === undefined ? {} : { 'X-Total-Count': total }) },
});

test('getWithTotal returns the list and the X-Total-Count header (admin flight paging)', async () => {
  await withBrowser(async ({ api }) => {
    let seen;
    globalThis.fetch = async (url, options) => { seen = { url, auth: options.headers.Authorization }; return paged([{ id: 'f1' }], '510'); };
    const r = await api.getWithTotal('/api/flights?size=50&upcoming=true');
    assert.deepEqual(r.body.data, [{ id: 'f1' }]);
    assert.equal(r.total, 510);
    assert.ok(seen.url.endsWith('/api/flights?size=50&upcoming=true'));
    assert.equal(seen.auth, 'Bearer old-access');
  });
});

test('getWithTotal gives total null when the header is missing or not a number', async () => {
  await withBrowser(async ({ api }) => {
    globalThis.fetch = async () => paged([{ id: 'f1' }]);
    assert.equal((await api.getWithTotal('/api/flights')).total, null);
    globalThis.fetch = async () => paged([], 'abc');
    assert.equal((await api.getWithTotal('/api/flights')).total, null);
    globalThis.fetch = async () => paged([], '');
    assert.equal((await api.getWithTotal('/api/flights')).total, null);
  });
});

test('getWithTotal refreshes an expired token once and reads the header of the retry', async () => {
  await withBrowser(async ({ api }, storage) => {
    let refreshes = 0;
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/api/auth/refresh')) { refreshes++; return json({ success: true, data: { token: 'new-access', refreshToken: 'new-refresh' } }); }
      if (options.headers.Authorization === 'Bearer old-access') return json({}, 401);
      return paged([{ id: 'f2' }], '7');
    };
    const r = await api.getWithTotal('/api/flights');
    assert.equal(refreshes, 1);
    assert.equal(r.total, 7);
    assert.deepEqual(r.body.data, [{ id: 'f2' }]);
    assert.equal(storage.get('waylo_token'), 'new-access');
  });
});

test('getWithTotal keeps the typed errors of get()', async () => {
  await withBrowser(async ({ api, ApiError }) => {
    globalThis.fetch = async () => json({ success: false, error: 'FORBIDDEN', message: 'Yetkiniz yok' }, 403);
    await assert.rejects(api.getWithTotal('/api/flights'), e => e instanceof ApiError && e.status === 403 && e.code === 'FORBIDDEN');
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
    await assert.rejects(api.getWithTotal('/api/flights'), e => e instanceof ApiError && e.status === 0 && e.code === 'NETWORK');
  });
});
