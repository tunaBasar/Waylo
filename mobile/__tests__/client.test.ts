import { ApiError, createApiClient } from '../src/api/client';
import { createMemoryTokenStore } from '../src/api/tokenStore';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const env = (data: unknown) => ({ success: true, data, message: null });

function setup(responses: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fetchImpl = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return responses(url, init);
  }) as unknown as typeof fetch;
  const tokens = createMemoryTokenStore({ token: 'old', refreshToken: 'r1' });
  const onSessionExpired = jest.fn();
  const client = createApiClient({ baseUrl: 'http://x', tokens, fetchImpl, onSessionExpired });
  return { client, calls, tokens, onSessionExpired };
}

describe('api client', () => {
  it('unwraps the {success,data} envelope and sends the bearer token', async () => {
    const { client, calls } = setup(() => json(200, env({ a: 1 })));
    await expect(client.get('/api/me')).resolves.toEqual({ a: 1 });
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe('Bearer old');
  });

  it('refreshes once on 401, stores new tokens, retries the request', async () => {
    let n = 0;
    const { client, calls, tokens } = setup((url) => {
      if (url.endsWith('/api/auth/refresh')) return json(200, env({ token: 'new', refreshToken: 'r2' }));
      return n++ === 0 ? json(401, { message: 'expired' }) : json(200, env('ok'));
    });
    await expect(client.get('/api/orders')).resolves.toBe('ok');
    expect(calls.map((c) => c.url)).toEqual(['http://x/api/orders', 'http://x/api/auth/refresh', 'http://x/api/orders']);
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ refreshToken: 'r1' });
    expect(await tokens.get()).toEqual({ token: 'new', refreshToken: 'r2' });
    expect((calls[2].init.headers as Record<string, string>).Authorization).toBe('Bearer new');
  });

  it('clears the session and signals expiry when refresh fails', async () => {
    const { client, tokens, onSessionExpired } = setup((url) =>
      url.endsWith('/api/auth/refresh') ? json(401, { message: 'bad' }) : json(401, { message: 'expired' }));
    await expect(client.get('/api/orders')).rejects.toMatchObject({ status: 401 });
    expect(await tokens.get()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it('shares a single refresh between concurrent 401s', async () => {
    let refreshes = 0;
    const { client } = setup((url, init) => {
      if (url.endsWith('/api/auth/refresh')) { refreshes++; return json(200, env({ token: 'new', refreshToken: 'r2' })); }
      return (init.headers as Record<string, string>).Authorization === 'Bearer old' ? json(401, {}) : json(200, env(1));
    });
    await Promise.all([client.get('/a'), client.get('/b')]);
    expect(refreshes).toBe(1);
  });

  it('maps error envelopes to ApiError with status and code', async () => {
    const { client } = setup(() => json(409, { message: 'Dolu', error: 'CONFLICT' }));
    const err: unknown = await client.post('/api/orders/package', {}).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ message: 'Dolu', status: 409, code: 'CONFLICT' });
  });

  it('turns success:false on a 200 into an ApiError', async () => {
    const { client } = setup(() => json(200, { success: false, message: 'Hatali sifre', data: null, code: 'BAD_CREDENTIALS' }));
    await expect(client.publicPost('/api/auth/login', {})).rejects.toMatchObject({ message: 'Hatali sifre', code: 'BAD_CREDENTIALS' });
  });

  it('reports network failures as status 0', async () => {
    const tokens = createMemoryTokenStore(null);
    const client = createApiClient({ baseUrl: 'http://x', tokens, fetchImpl: (async () => { throw new TypeError('fail'); }) as unknown as typeof fetch });
    const err = (await client.get("/x").catch((e: unknown) => e)) as ApiError;
    expect(err.isNetwork).toBe(true);
  });
});
