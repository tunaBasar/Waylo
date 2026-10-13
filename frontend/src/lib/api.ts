const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
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
  if (!res.ok) {
    // Handle 401 - expired/invalid token
    if (res.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('waylo_user');
      localStorage.removeItem('waylo_token');
      localStorage.removeItem('waylo_refresh');
      window.location.href = '/login';
    }
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
