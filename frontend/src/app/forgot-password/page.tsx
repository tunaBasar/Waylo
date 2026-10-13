'use client';

import { useState } from 'react';
import Link from 'next/link';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    await new Promise(r => setTimeout(r, 1500));
    setSent(true);
    setLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface relative overflow-hidden px-4">
      <div className="absolute -top-32 -left-32 w-80 h-80 rounded-full bg-primary/8 blur-3xl" />
      <div className="absolute -bottom-20 -right-20 w-64 h-64 rounded-full bg-tertiary/10 blur-3xl" />

      <div className="w-full max-w-sm relative z-10 space-y-6">
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary shadow-lg mb-4">
            <span className="material-symbols-outlined text-on-primary text-2xl">{sent ? 'mark_email_read' : 'lock_reset'}</span>
          </div>
          <h1 className="text-2xl font-bold text-on-surface tracking-tight">{sent ? 'E-posta Gonderildi' : 'Sifremi Unuttum'}</h1>
          <p className="text-sm text-on-surface-variant mt-1">
            {sent ? `${email} adresine sifre sifirlama baglantisi gonderildi.` : 'E-posta adresinizi girin, sifre sifirlama baglantisi gonderelim.'}
          </p>
        </div>

        <div className="bg-surface-container-lowest rounded-2xl shadow-md p-6">
          {sent ? (
            <div className="text-center space-y-4">
              <div className="w-16 h-16 mx-auto rounded-full bg-secondary-container flex items-center justify-center">
                <span className="material-symbols-outlined text-secondary text-3xl">check</span>
              </div>
              <p className="text-xs text-on-surface-variant">E-postanizi kontrol edin. Spam klasorune de bakmasi unutmayin.</p>
              <Link href="/login"
                className="inline-flex items-center gap-1 px-4 py-2 rounded-xl bg-primary text-on-primary text-sm font-medium shadow-md">
                <span className="material-symbols-outlined text-[16px]">arrow_back</span> Giris Sayfasina Don
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-medium text-on-surface-variant">E-posta Adresi</label>
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">mail</span>
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                    placeholder="ornek@waylo.com" />
                </div>
              </div>
              <button type="submit" disabled={loading}
                className="w-full py-2.5 rounded-xl bg-primary text-on-primary text-sm font-semibold shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50">
                {loading ? 'Gonderiliyor...' : 'Sifirlama Baglantisi Gonder'}
              </button>
            </form>
          )}
        </div>

        <p className="text-center text-xs text-on-surface-variant">
          <Link href="/login" className="text-primary font-semibold hover:underline">Giris sayfasina don</Link>
        </p>
      </div>
    </div>
  );
}
