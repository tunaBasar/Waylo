'use client';

import { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';

export default function ResetPasswordPage() {
  const { resetPassword } = useAuth();
  const searchParams = useSearchParams();
  const token = searchParams.get('token') || '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!token) { setError('Gecersiz sifirlama linki'); return; }
    if (password !== confirm) { setError('Sifreler uyusmuyor'); return; }
    if (password.length < 6) { setError('Sifre en az 6 karakter olmali'); return; }

    setLoading(true);
    try {
      await resetPassword(token, password);
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sifre sifirlanamadi');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">
      <VideoBackground />

      <div className="relative w-full max-w-md mx-4 z-10">
        <div className="bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl shadow-2xl p-6 sm:p-8 flex flex-col items-center border border-white/10">
          <div className="flex items-center gap-2 mb-6">
            <span className="material-symbols-outlined text-primary text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
            <span className="text-2xl font-bold text-on-surface">Waylo</span>
          </div>

          {success ? (
            <div className="text-center">
              <div className="w-16 h-16 rounded-full bg-tertiary-container flex items-center justify-center mx-auto mb-4">
                <span className="material-symbols-outlined text-on-tertiary-container text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
              </div>
              <h2 className="text-lg font-semibold text-on-surface mb-2">Sifre Guncellendi!</h2>
              <p className="text-sm text-on-surface-variant mb-6">Yeni sifrenizle giris yapabilirsiniz.</p>
              <Link href="/login"
                className="px-6 py-2.5 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg transition-all">
                Giris Yap
              </Link>
            </div>
          ) : (
            <>
              <h1 className="text-lg font-semibold text-on-surface mb-1">Yeni Sifre Belirle</h1>
              <p className="text-sm text-on-surface-variant mb-6">Yeni sifrenizi girin</p>

              {error && (
                <div className="w-full mb-4 p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
                  <span className="material-symbols-outlined text-base">error</span> {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="w-full flex flex-col gap-4">
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-medium text-on-surface-variant">Yeni Sifre</label>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">lock</span>
                    <input type={showPass ? 'text' : 'password'} value={password}
                      onChange={e => setPassword(e.target.value)} required
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                      placeholder="En az 6 karakter" />
                    <button type="button" onClick={() => setShowPass(!showPass)} className="absolute right-3 top-1/2 -translate-y-1/2">
                      <span className="material-symbols-outlined text-outline text-[18px]">{showPass ? 'visibility_off' : 'visibility'}</span>
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-medium text-on-surface-variant">Sifre Tekrar</label>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">lock</span>
                    <input type="password" value={confirm}
                      onChange={e => setConfirm(e.target.value)} required
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                      placeholder="Sifreyi tekrar girin" />
                  </div>
                </div>

                <button type="submit" disabled={loading}
                  className="w-full py-2.5 rounded-xl bg-primary text-on-primary text-sm font-semibold shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50">
                  {loading ? (
                    <div className="w-5 h-5 border-2 border-on-primary/30 border-t-on-primary rounded-full animate-spin mx-auto" />
                  ) : 'Sifreyi Guncelle'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
