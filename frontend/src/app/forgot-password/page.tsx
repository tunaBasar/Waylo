'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';

export default function ForgotPasswordPage() {
  const { forgotPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bir hata olustu');
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

          {sent ? (
            <div className="text-center">
              <div className="w-16 h-16 mx-auto rounded-full bg-secondary-container flex items-center justify-center mb-4">
                <span className="material-symbols-outlined text-on-secondary-container text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>mark_email_read</span>
              </div>
              <h2 className="text-lg font-semibold text-on-surface mb-2">E-posta Gonderildi</h2>
              <p className="text-sm text-on-surface-variant mb-1"><strong>{email}</strong> adresine sifre sifirlama baglantisi gonderildi.</p>
              <p className="text-xs text-on-surface-variant mb-6">E-postanizi kontrol edin. Spam klasorune de bakmayi unutmayin.</p>
              <Link href="/login"
                className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-primary text-on-primary text-sm font-medium shadow-md hover:shadow-lg transition-all">
                <span className="material-symbols-outlined text-[16px]">arrow_back</span> Giris Sayfasina Don
              </Link>
            </div>
          ) : (
            <>
              <h1 className="text-lg font-semibold text-on-surface mb-1">Sifremi Unuttum</h1>
              <p className="text-sm text-on-surface-variant mb-6">E-posta adresinizi girin, sifre sifirlama baglantisi gonderelim.</p>

              {error && (
                <div className="w-full mb-4 p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
                  <span className="material-symbols-outlined text-base">error</span> {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="w-full flex flex-col gap-4">
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-medium text-on-surface-variant">E-posta Adresi</label>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">alternate_email</span>
                    <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface text-sm placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary/30"
                      placeholder="ornek@waylo.com" />
                  </div>
                </div>

                <button type="submit" disabled={loading}
                  className="w-full py-2.5 rounded-xl bg-primary text-on-primary text-sm font-semibold shadow-md hover:shadow-lg active:scale-[0.98] transition-all disabled:opacity-50">
                  {loading ? (
                    <div className="w-5 h-5 border-2 border-on-primary/30 border-t-on-primary rounded-full animate-spin mx-auto" />
                  ) : 'Sifirlama Baglantisi Gonder'}
                </button>
              </form>

              <div className="mt-4 text-center">
                <Link href="/login" className="text-sm text-primary font-semibold hover:text-primary-container transition-colors">
                  Giris sayfasina don
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
