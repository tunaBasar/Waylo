'use client';

import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';

interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => void;
  forgotPassword: (email: string) => Promise<void>;
}

interface RegisterData {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
}

const AuthContext = createContext<AuthContextType | null>(null);

const PUBLIC_PATHS = ['/login', '/register', '/forgot-password'];

// Mock user for demo - replace with real API when auth service is ready
const MOCK_USERS: Record<string, User & { password: string }> = {
  'admin@waylo.com': {
    id: '1',
    firstName: 'Admin',
    lastName: 'Waylo',
    email: 'admin@waylo.com',
    password: 'admin123',
    role: 'ADMIN',
  },
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const stored = localStorage.getItem('waylo_user');
    if (stored) {
      try {
        setUser(JSON.parse(stored));
      } catch {
        localStorage.removeItem('waylo_user');
      }
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!loading && !user && !PUBLIC_PATHS.includes(pathname)) {
      router.replace('/login');
    }
  }, [user, loading, pathname, router]);

  const login = useCallback(async (email: string, password: string) => {
    // Mock auth - replace with real API call
    const mockUser = MOCK_USERS[email];
    if (mockUser && mockUser.password === password) {
      const { password: _, ...userData } = mockUser;
      setUser(userData);
      localStorage.setItem('waylo_user', JSON.stringify(userData));
      router.push('/');
      return;
    }
    throw new Error('Gecersiz e-posta veya sifre');
  }, [router]);

  const register = useCallback(async (data: RegisterData) => {
    // Mock register - just create user and login
    const newUser: User = {
      id: crypto.randomUUID(),
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
      role: 'USER',
    };
    // Add to mock users
    MOCK_USERS[data.email] = { ...newUser, password: data.password };
    setUser(newUser);
    localStorage.setItem('waylo_user', JSON.stringify(newUser));
    router.push('/');
  }, [router]);

  const logout = useCallback(() => {
    setUser(null);
    localStorage.removeItem('waylo_user');
    router.push('/login');
  }, [router]);

  const forgotPassword = useCallback(async (email: string) => {
    // Mock - just simulate delay
    await new Promise(resolve => setTimeout(resolve, 1000));
    if (!email.includes('@')) {
      throw new Error('Gecerli bir e-posta adresi girin');
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, forgotPassword }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
