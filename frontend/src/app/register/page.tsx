'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';

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
    } catch {
      setError('Kayit olusturulamadi. Bu e-posta zaten kullanilmis olabilir.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface relative overflow-hidden px-4">
      {/* Decorative */}
      <div className="absolute -top-20 -right-20 w-72 h-72 rounded-full bg-secondary/10 blur-3xl" />
      <div className="absolute -bottom-32 -left-32 w-96 h-96 rounded-full bg-primary/8 blur-3xl" />

      <div className="w-full max-w-sm relative z-10 space-y-6">
        {/* Logo */}
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary shadow-lg mb-4">
            <span className="material-symbols-outlined text-on-primary text-2xl">flight</span>
          </div>
          <h1 className="text-2xl font-bold text-on-surface tracking-tight">Hesap Olustur</h1>
          <p className="text-sm text-on-surface-variant mt-1">Waylo&apos;ya katilmak icin bilgilerinizi girin</p>
        </div>

        {/* Form card */}
        <div className="bg-surface-container-lowest rounded-2xl shadow-md p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-error-container text-on-error-container text-xs">
                <span className="material-symbols-outlined text-[16px]">error</span> {error}
              </div>
            )}

            <div className="space-y-1">
              <label className="text-xs font-medium text-on-surface-variant">Ad</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">person</span>
                <input type="text" value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="Adiniz" />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-on-surface-variant">Soyad</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">badge</span>
                <input type="text" value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="Soyadiniz" />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-on-surface-variant">E-posta</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">mail</span>
                <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="ornek@waylo.com" />
              </div>
            </div>

            <div className="space-y-1">
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

            <div className="space-y-1">
              <label className="text-xs font-medium text-on-surface-variant">Sifre Tekrar</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">lock</span>
                <input type="password" value={form.confirm} onChange={e => setForm(f => ({ ...f, confirm: e.target.value }))} required
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="Sifreyi tekrar girin" />
              </div>
            </div>

            <button type="submit" disabled={loading}
              className="w-full py-2.5 rounded-xl bg-primary text-on-primary text-sm font-semibold shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50">
              {loading ? 'Kaydediliyor...' : 'Kayit Ol'}
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-on-surface-variant">
          Zaten hesabiniz var mi?{' '}
          <Link href="/login" className="text-primary font-semibold hover:underline">Giris Yap</Link>
        </p>
      </div>
    </div>
  );
}
