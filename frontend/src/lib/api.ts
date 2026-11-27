const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
}

let refreshPromise: Promise<boolean> | null = null;

export async function refreshAccessToken(): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const refreshToken = localStorage.getItem('waylo_refresh');
      if (!refreshToken) {
        return false;
      }

      const res = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!res.ok) {
        return false;
      }

      const data = await res.json();

      if (data.success && data.data) {
        localStorage.setItem('waylo_token', data.data.token);
        localStorage.setItem('waylo_refresh', data.data.refreshToken);
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

function getAuthHeaders(json = true): Record<string, string> {
  const headers: Record<string, string> = json ? { 'Content-Type': 'application/json' } : {};
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('waylo_token');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  return headers;
}

/** Sends with the auth headers; a 401 refreshes the token once and retries. Non-2xx answers throw ApiError. */
async function send(path: string, options?: RequestInit): Promise<Response> {
  // Multipart bodies must not carry the JSON Content-Type: the browser sets multipart/form-data with the boundary.
  const json = !(typeof FormData !== 'undefined' && options?.body instanceof FormData);
  const res = await safeFetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...getAuthHeaders(json),
      ...options?.headers,
    },
  });

  // Handle 401 - expired/invalid token
  if (res.status === 401 && typeof window !== 'undefined' && !path.startsWith('/api/auth/')) {
    const refreshed = await refreshAccessToken();

    if (refreshed) {
      // Retry the original request once with the new token
      const retryRes = await safeFetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
          ...getAuthHeaders(json),
          ...options?.headers,
        },
      });

      if (retryRes.status === 401) {
        localStorage.removeItem('waylo_user');
        localStorage.removeItem('waylo_token');
        localStorage.removeItem('waylo_refresh');
        Object.assign(window.location, { href: '/login' });
      }

      if (!retryRes.ok) {
        throw await toApiError(retryRes);
      }

      return retryRes;
    }

    // Refresh failed - clear session and redirect
    localStorage.removeItem('waylo_user');
    localStorage.removeItem('waylo_token');
    localStorage.removeItem('waylo_refresh');
    Object.assign(window.location, { href: '/login' });
  }

  if (!res.ok) {
    throw await toApiError(res);
  }

  return res;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  return parseBody<T>(await send(path, options));
}

/**
 * Error thrown for non-2xx responses. Keeps the HTTP status and the backend error code so callers
 * can branch on facts (e.g. 409 CONCURRENT_MODIFICATION) instead of matching message text.
 * Still an Error, so existing `err.message` callers keep working.
 */
export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code: string | null, readonly body: Record<string, unknown> | null = null) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Same text as ADMIN_PURCHASE_MESSAGE in surface.ts (duplicated: api.ts must stay import-free for its unit test). */
export const ADMIN_CANNOT_PURCHASE_MESSAGE = 'Platform yoneticisi hesabi ile satin alma yapilamaz, musteri hesabi kullanin.';

/** Shown for COMMISSION_RATE_MISSING: customers must never see internal commission wording. */
export const SALES_CLOSED_MESSAGE = 'Bu urun su anda satisa kapali. Lutfen daha sonra tekrar deneyin veya destek ile iletisime gecin.';

/** Gateway 502/503/504 without a backend message (a service is down or restarting): never show the English reason phrase. */
export const SERVICE_UNAVAILABLE_MESSAGE = 'Servis su an yanit vermiyor (yeniden basliyor olabilir). Biraz sonra tekrar deneyin.';

async function toApiError(res: Response): Promise<ApiError> {
  const body = await res.json().catch(() => null) as { message?: string; error?: string } | null;
  if (res.status === 403 && body?.error === 'ADMIN_CANNOT_PURCHASE') {
    return new ApiError(ADMIN_CANNOT_PURCHASE_MESSAGE, 403, 'ADMIN_CANNOT_PURCHASE');
  }
  if (body?.error === 'COMMISSION_RATE_MISSING') {
    return new ApiError(SALES_CLOSED_MESSAGE, res.status, 'COMMISSION_RATE_MISSING');
  }
  if ((res.status === 502 || res.status === 503 || res.status === 504) && !body?.message) {
    return new ApiError(SERVICE_UNAVAILABLE_MESSAGE, res.status, 'SERVICE_UNAVAILABLE', body as Record<string, unknown> | null);
  }
  return new ApiError(body?.message || res.statusText || `HTTP ${res.status}`, res.status, body?.error ?? null, body as Record<string, unknown> | null);
}

/** 204 No Content (e.g. DELETE) has no body; res.json() would throw on it. */
async function parseBody<T>(res: Response): Promise<T> {
  const text = await res.text();
  const body = text ? JSON.parse(text) : undefined;
  // A 2xx envelope with success=false is a failure, never a success.
  if (body && typeof body === 'object' && (body as { success?: unknown }).success === false) {
    const b = body as { message?: string; error?: string } & Record<string, unknown>;
    throw new ApiError(b.message || 'Islem tamamlanamadi', res.status, b.error ?? 'UNSUCCESSFUL', b);
  }
  return body as T;
}

/** fetch that turns a thrown network failure into a typed ApiError. */
async function safeFetch(url: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch {
    throw new ApiError('Sunucuya ulasilamadi. Baglantinizi kontrol edip tekrar deneyin.', 0, 'NETWORK');
  }
}

export const api = {
  get: <T>(path: string) => request<ApiResponse<T>>(path),
  /**
   * GET of a paged list plus its X-Total-Count header (flight list). Same auth, refresh and error handling as get();
   * total is null when the header is missing or not a number. The gateway exposes the header to the browser (CORS).
   */
  getWithTotal: async <T>(path: string): Promise<{ body: ApiResponse<T[]> | undefined; total: number | null }> => {
    const res = await send(path);
    const header = res.headers.get('X-Total-Count');
    const body = await parseBody<ApiResponse<T[]> | undefined>(res);
    const total = header === null || header.trim() === '' ? NaN : Number(header);
    return { body, total: Number.isFinite(total) ? total : null };
  },
  post: <T>(path: string, body: unknown, headers?: Record<string, string>) =>
    request<ApiResponse<T>>(path, { method: 'POST', body: JSON.stringify(body), ...(headers ? { headers } : {}) }),
  /**
   * Multipart upload (documents). Never pass FormData to post(): JSON.stringify turns it into "{}" and the JSON
   * Content-Type makes the service answer 415 (car and bus partners could not upload any document, 2026-11-26).
   */
  postForm: <T>(path: string, form: FormData) =>
    request<ApiResponse<T>>(path, { method: 'POST', body: form }),
  /** Binary download (documents, files). api.get() parses JSON and would break on PDF bytes. */
  getBlob: async (path: string): Promise<Blob> => {
    const res = await safeFetch(`${API_BASE}${path}`, { headers: getAuthHeaders(false) });
    if (!res.ok) throw await toApiError(res);
    return res.blob();
  },
  put: <T>(path: string, body: unknown) =>
    request<ApiResponse<T>>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<ApiResponse<T>>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del: (path: string, body?: unknown) => request(path, { method: 'DELETE', ...(body === undefined ? {} : { body: JSON.stringify(body) }) }),
};
