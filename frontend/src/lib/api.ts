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

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('waylo_token');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  return headers;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...getAuthHeaders(),
      ...options?.headers,
    },
  });

  // Handle 401 - expired/invalid token
  if (res.status === 401 && typeof window !== 'undefined' && !path.startsWith('/api/auth/')) {
    const refreshed = await refreshAccessToken();

    if (refreshed) {
      // Retry the original request once with the new token
      const retryRes = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
          ...getAuthHeaders(),
          ...options?.headers,
        },
      });

      if (retryRes.status === 401) {
        localStorage.removeItem('waylo_user');
        localStorage.removeItem('waylo_token');
        localStorage.removeItem('waylo_refresh');
        window.location.href = '/login';
      }

      if (!retryRes.ok) {
        const err = await retryRes.json().catch(() => ({ message: retryRes.statusText }));
        throw new Error(err.message || `HTTP ${retryRes.status}`);
      }

      return retryRes.json();
    }

    // Refresh failed - clear session and redirect
    localStorage.removeItem('waylo_user');
    localStorage.removeItem('waylo_token');
    localStorage.removeItem('waylo_refresh');
    window.location.href = '/login';
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || `HTTP ${res.status}`);
  }

  return res.json();
}

export const api = {
  get: <T>(path: string) => request<ApiResponse<T>>(path),
  post: <T>(path: string, body: unknown) =>
    request<ApiResponse<T>>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<ApiResponse<T>>(path, { method: 'PUT', body: JSON.stringify(body) }),
  del: (path: string) => request(path, { method: 'DELETE' }),
};
