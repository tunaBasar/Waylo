import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setSessionExpiredListener, type AuthData, type Profile } from '@/api';

interface AuthState {
  user: Profile | null;
  /** true while the stored session is being validated on app start */
  loading: boolean;
  login(email: string, password: string): Promise<void>;
  register(d: { firstName: string; lastName: string; email: string; password: string }): Promise<{ email: string }>;
  logout(): Promise<void>;
  forgotPassword(email: string): Promise<void>;
  refreshProfile(): Promise<void>;
  /** Store a fresh token pair (after password change the server revokes other sessions). */
  applyAuth(d: { token: string; refreshToken: string }): Promise<void>;
  /** Local sign-out without calling the server (account deleted). */
  clearSession(): Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshProfile = useCallback(async () => {
    setUser(await api.get<Profile>('/api/me'));
  }, []);

  useEffect(() => {
    setSessionExpiredListener(() => setUser(null));
    let alive = true;
    (async () => {
      try {
        if (await api.tokens.get()) await refreshProfile();
      } catch {
        // offline or expired: stay signed out in UI, tokens are kept unless the client cleared them
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; setSessionExpiredListener(null); };
  }, [refreshProfile]);

  const login = useCallback(async (email: string, password: string) => {
    const d = await api.publicPost<AuthData>('/api/auth/login', { email, password });
    await api.tokens.set({ token: d.token, refreshToken: d.refreshToken });
    await refreshProfile();
  }, [refreshProfile]);

  const register = useCallback(async (body: { firstName: string; lastName: string; email: string; password: string }) =>
    api.publicPost<{ email: string }>('/api/auth/register', body), []);

  const logout = useCallback(async () => {
    const t = await api.tokens.get();
    if (t) api.publicPost('/api/auth/logout', { refreshToken: t.refreshToken }).catch(() => {});
    await api.tokens.clear();
    setUser(null);
  }, []);

  const applyAuth = useCallback(async (d: { token: string; refreshToken: string }) => {
    await api.tokens.set({ token: d.token, refreshToken: d.refreshToken });
  }, []);
  const clearSession = useCallback(async () => { await api.tokens.clear(); setUser(null); }, []);

  const forgotPassword = useCallback(async (email: string) => {
    await api.publicPost('/api/auth/forgot-password', { email });
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, forgotPassword, refreshProfile, applyAuth, clearSession }),
    [user, loading, login, register, logout, forgotPassword, refreshProfile, applyAuth, clearSession],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('AuthProvider missing');
  return v;
}
