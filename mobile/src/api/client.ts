import type { ApiEnvelope, AuthData } from './types';
import type { TokenStore } from './tokenStore';

/** Non-2xx or success:false response. status 0 + code NETWORK means the server was unreachable. */
export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code: string | null) {
    super(message);
    this.name = 'ApiError';
  }
  get isNetwork() { return this.status === 0; }
}

export interface ApiClientOptions {
  baseUrl: string;
  tokens: TokenStore;
  fetchImpl?: typeof fetch;
  /** Called when refresh failed and the session was cleared. */
  onSessionExpired?: () => void;
}

export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  patch<T>(path: string, body?: unknown): Promise<T>;
  del<T>(path: string, body?: unknown): Promise<T>;
  /** Unauthenticated envelope call (login/register/...), returns data. */
  publicPost<T>(path: string, body?: unknown): Promise<T>;
  /** Unauthenticated envelope GET (email verification). */
  publicGet<T>(path: string): Promise<T>;
  refresh(): Promise<boolean>;
  tokens: TokenStore;
}

/** Gateway 502/503/504 without a backend message (a service is down or restarting): never show the English reason phrase. */
export const SERVICE_UNAVAILABLE_MESSAGE = 'Servis su an yanit vermiyor (yeniden basliyor olabilir). Biraz sonra tekrar deneyin.';

async function toError(res: Response): Promise<ApiError> {
  const body = (await res.json().catch(() => null)) as { message?: string; error?: string; code?: string } | null;
  if ((res.status === 502 || res.status === 503 || res.status === 504) && !body?.message) {
    return new ApiError(SERVICE_UNAVAILABLE_MESSAGE, res.status, 'SERVICE_UNAVAILABLE');
  }
  return new ApiError(body?.message || res.statusText || `HTTP ${res.status}`, res.status, body?.code ?? body?.error ?? null);
}

export function createApiClient(opts: ApiClientOptions): ApiClient {
  const doFetch: typeof fetch = opts.fetchImpl ?? ((...a) => fetch(...a));
  let refreshPromise: Promise<boolean> | null = null;

  async function send(path: string, method: string, body: unknown, auth: boolean): Promise<Response> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (auth) {
      const t = await opts.tokens.get();
      if (t) headers.Authorization = `Bearer ${t.token}`;
    }
    try {
      return await doFetch(`${opts.baseUrl}${path}`, {
        method,
        headers,
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
    } catch {
      throw new ApiError('Sunucuya baglanilamiyor. Baglantinizi kontrol edin.', 0, 'NETWORK');
    }
  }

  /** Single-flight refresh, same contract as the web client: POST /api/auth/refresh {refreshToken}. */
  function refresh(): Promise<boolean> {
    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      try {
        const t = await opts.tokens.get();
        if (!t) return false;
        const res = await send('/api/auth/refresh', 'POST', { refreshToken: t.refreshToken }, false);
        if (!res.ok) return false;
        const env = (await res.json()) as ApiEnvelope<AuthData | null>;
        if (env.success && env.data?.token && env.data.refreshToken) {
          await opts.tokens.set({ token: env.data.token, refreshToken: env.data.refreshToken });
          return true;
        }
        return false;
      } catch {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
    return refreshPromise;
  }

  async function parse<T>(res: Response): Promise<T> {
    if (!res.ok) throw await toError(res);
    const text = await res.text();
    if (!text) return undefined as T;
    const env = JSON.parse(text) as ApiEnvelope<T>;
    if (env.success === false) throw new ApiError(env.message || 'Islem basarisiz', res.status, env.code ?? null);
    return env.data;
  }

  async function request<T>(path: string, method: string, body?: unknown): Promise<T> {
    let res = await send(path, method, body, true);
    if (res.status === 401) {
      if (await refresh()) {
        res = await send(path, method, body, true);
      }
      if (res.status === 401) {
        await opts.tokens.clear();
        opts.onSessionExpired?.();
      }
    }
    return parse<T>(res);
  }

  return {
    tokens: opts.tokens,
    refresh,
    get: (p) => request(p, 'GET'),
    post: (p, b) => request(p, 'POST', b ?? {}),
    patch: (p, b) => request(p, 'PATCH', b ?? {}),
    del: (p, b) => request(p, 'DELETE', b),
    publicPost: async (p, b) => parse(await send(p, 'POST', b ?? {}, false)),
    publicGet: async (p) => parse(await send(p, 'GET', undefined, false)),
  };
}
