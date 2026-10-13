'use client';

import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  emailVerified: boolean;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => void;
  forgotPassword: (email: string) => Promise<void>;
  resetPassword: (token: string, newPassword: string) => Promise<void>;
  verifyEmail: (token: string) => Promise<{ success: boolean; message: string }>;
  resendVerification: (email: string) => Promise<void>;
}

interface RegisterData {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}

interface AuthData {
  token: string;
  refreshToken: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  emailVerified: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

const PUBLIC_PATHS = ['/login', '/register', '/forgot-password', '/reset-password', '/verify'];

async function authFetch<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    const data: ApiResponse<T> = await res.json();
    return data;
  } catch {
    return { success: false, message: 'Sunucuya baglanilamiyor. Lutfen daha sonra tekrar deneyin.', data: null };
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  // Load persisted session
  useEffect(() => {
    const storedUser = localStorage.getItem('waylo_user');
    const storedToken = localStorage.getItem('waylo_token');
    if (storedUser && storedToken) {
      try {
        setUser(JSON.parse(storedUser));
        setToken(storedToken);
      } catch {
        localStorage.removeItem('waylo_user');
        localStorage.removeItem('waylo_token');
        localStorage.removeItem('waylo_refresh');
      }
    }
    setLoading(false);
  }, []);

  // Redirect unauthenticated users
  useEffect(() => {
    if (!loading && !user && !PUBLIC_PATHS.some(p => pathname.startsWith(p))) {
      router.replace('/login');
    }
  }, [user, loading, pathname, router]);

  const persistAuth = (authData: AuthData) => {
    const userData: User = {
      id: authData.userId,
      firstName: authData.firstName,
      lastName: authData.lastName,
      email: authData.email,
      role: authData.role,
      emailVerified: authData.emailVerified,
    };
    setUser(userData);
    setToken(authData.token);
    localStorage.setItem('waylo_user', JSON.stringify(userData));
    localStorage.setItem('waylo_token', authData.token);
    localStorage.setItem('waylo_refresh', authData.refreshToken);
  };

  const login = useCallback(async (email: string, password: string) => {
    const res = await authFetch<AuthData>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });

    if (!res.success || !res.data) {
      throw new Error(res.message || 'Giris basarisiz');
    }

    persistAuth(res.data);
    router.push('/');
  }, [router]);

  const register = useCallback(async (data: RegisterData) => {
    const res = await authFetch<AuthData>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });

    if (!res.success || !res.data) {
      throw new Error(res.message || 'Kayit basarisiz');
    }

    persistAuth(res.data);
    router.push('/');
  }, [router]);

  const logout = useCallback(() => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('waylo_user');
    localStorage.removeItem('waylo_token');
    localStorage.removeItem('waylo_refresh');
    router.push('/login');
  }, [router]);

  const forgotPassword = useCallback(async (email: string) => {
    const res = await authFetch<void>('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });

    if (!res.success) {
      throw new Error(res.message);
    }
  }, []);

  const resetPassword = useCallback(async (resetToken: string, newPassword: string) => {
    const res = await authFetch<void>('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token: resetToken, newPassword }),
    });

    if (!res.success) {
      throw new Error(res.message);
    }
  }, []);

  const verifyEmail = useCallback(async (verificationToken: string) => {
    const res = await authFetch<void>(`/api/auth/verify?token=${encodeURIComponent(verificationToken)}`);
    return { success: res.success, message: res.message };
  }, []);

  const resendVerification = useCallback(async (email: string) => {
    const res = await authFetch<void>(`/api/auth/resend-verification?email=${encodeURIComponent(email)}`, {
      method: 'POST',
    });

    if (!res.success) {
      throw new Error(res.message);
    }
  }, []);

  return (
    <AuthContext.Provider value={{
      user, loading, token,
      login, register, logout,
      forgotPassword, resetPassword, verifyEmail, resendVerification,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
