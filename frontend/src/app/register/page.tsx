'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';
import VideoBackground from '@/components/VideoBackground';

export default function RegisterPage() {
  const { register } = useAuth();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPass, setShowPass] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (form.password !== form.confirm) { setError('Sifreler uyusmuyor'); return; }
    if (form.password.length < 6) { setError('Sifre en az 6 karakter olmali'); return; }
    setLoading(true);
    try {
      await register({ firstName: form.firstName, lastName: form.lastName, email: form.email, password: form.password });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Kayit olusturulamadi. Bu e-posta zaten kullanilmis olabilir.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">
      <VideoBackground />

      <div className="relative w-full max-w-md mx-4 z-10">
        <div className="bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl shadow-2xl p-6 sm:p-8 flex flex-col border border-white/10">
          {/* Logo */}
          <div className="flex flex-col items-center text-center mb-5">
            <div className="flex items-center gap-2 mb-2">
              <span className="material-symbols-outlined text-primary text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
              <span className="text-2xl font-bold text-on-surface">Waylo</span>
            </div>
            <h1 className="text-lg font-semibold text-on-surface mb-1">Hesap Olustur</h1>
            <p className="text-sm text-on-surface-variant">Waylo&apos;ya katilmak icin bilgilerinizi girin</p>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
              <span className="material-symbols-outlined text-base">error</span> {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-on-surface-variant">Ad</label>
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">person</span>
                  <input type="text" value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} required
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                    placeholder="Adiniz" />
                </div>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-on-surface-variant">Soyad</label>
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">badge</span>
                  <input type="text" value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} required
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                    placeholder="Soyadiniz" />
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-on-surface-variant">E-posta</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">alternate_email</span>
                <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="ornek@waylo.com" />
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-on-surface-variant">Sifre</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">lock</span>
                <input type={showPass ? 'text' : 'password'} value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required
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
                <input type="password" value={form.confirm} onChange={e => setForm(f => ({ ...f, confirm: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="Sifreyi tekrar girin" />
              </div>
            </div>

            <button type="submit" disabled={loading}
              className="w-full py-2.5 rounded-xl bg-primary text-on-primary text-sm font-semibold shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50 mt-1">
              {loading ? (
                <div className="w-5 h-5 border-2 border-on-primary/30 border-t-on-primary rounded-full animate-spin mx-auto" />
              ) : 'Kayit Ol'}
            </button>
          </form>

          <div className="mt-4 text-center">
            <p className="text-sm text-on-surface-variant">
              Zaten hesabiniz var mi?{' '}
              <Link href="/login" className="text-primary font-semibold hover:text-primary-container transition-colors">Giris Yap</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
