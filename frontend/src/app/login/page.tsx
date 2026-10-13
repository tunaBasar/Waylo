'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(email, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Giris basarisiz');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">
      <VideoBackground />

      {/* Login Card */}
      <div className="relative w-full max-w-md mx-4 z-10">
        <div className="bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl shadow-2xl p-6 sm:p-8 flex flex-col border border-white/10">
          {/* Logo */}
          <div className="flex flex-col items-center text-center mb-6">
            <div className="flex items-center gap-2 mb-2">
              <span className="material-symbols-outlined text-primary text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
              <span className="text-2xl font-bold text-on-surface">Waylo</span>
            </div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-container mb-3">
              <span className="material-symbols-outlined text-secondary text-sm" style={{ fontVariationSettings: "'FILL' 1" }}>luggage</span>
              <span className="text-xs text-secondary uppercase tracking-wider font-semibold">Seyahatini Paketle</span>
            </div>
            <h1 className="text-lg font-semibold text-on-surface mb-1">Yonetim Paneline Giris Yap</h1>
            <p className="text-sm text-on-surface-variant max-w-xs">Ucus, otel ve seyahat paketlerini tek panelden yonetin</p>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
              <span className="material-symbols-outlined text-base">error</span>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-on-surface">E-posta</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-xl pointer-events-none">alternate_email</span>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                  placeholder="admin@waylo.com" required
                  className="w-full pl-11 pr-4 py-3 bg-surface-container-low rounded-xl text-on-surface text-sm placeholder:text-outline outline-none focus:ring-2 focus:ring-primary/30 transition-all" />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-on-surface">Sifre</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-xl pointer-events-none">lock</span>
                <input type={showPassword ? 'text' : 'password'} value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="Sifrenizi girin" required
                  className="w-full pl-11 pr-12 py-3 bg-surface-container-low rounded-xl text-on-surface text-sm placeholder:text-outline outline-none focus:ring-2 focus:ring-primary/30 transition-all" />
                <button type="button" onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors">
                  <span className="material-symbols-outlined text-xl">{showPassword ? 'visibility_off' : 'visibility'}</span>
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary/30" />
                <span className="text-sm text-on-surface-variant">Beni Hatirla</span>
              </label>
              <Link href="/forgot-password" className="text-sm text-primary font-medium hover:text-primary-container transition-colors">
                Sifremi Unuttum?
              </Link>
            </div>

            <button type="submit" disabled={loading}
              className="w-full py-3 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50 flex items-center justify-center gap-2">
              {loading ? (
                <div className="w-5 h-5 border-2 border-on-primary/30 border-t-on-primary rounded-full animate-spin" />
              ) : (
                <>Giris Yap <span className="material-symbols-outlined text-lg">arrow_forward</span></>
              )}
            </button>
          </form>

          <div className="flex items-center gap-3 my-5">
            <div className="flex-1 h-px bg-outline-variant" />
            <span className="text-xs text-outline uppercase tracking-wider font-medium">veya</span>
            <div className="flex-1 h-px bg-outline-variant" />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <button className="flex items-center justify-center gap-2 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface font-medium hover:bg-surface-container transition-colors">
              <svg className="w-5 h-5" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
              Google
            </button>
            <button className="flex items-center justify-center gap-2 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface font-medium hover:bg-surface-container transition-colors">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
              Apple
            </button>
          </div>

          <div className="mt-5 text-center">
            <p className="text-sm text-on-surface-variant">
              Hesabiniz yok mu?{' '}
              <Link href="/register" className="text-primary font-semibold hover:text-primary-container transition-colors">Kayit Ol</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
