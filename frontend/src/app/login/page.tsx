'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';
import LoginMessage from './LoginMessage';

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

          <Suspense fallback={null}>
            <LoginMessage />
          </Suspense>

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
                  placeholder="ornek@eposta.com" required
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
