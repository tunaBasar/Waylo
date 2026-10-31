'use client';

import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';

function SentContent() {
  const { resendVerification } = useAuth();
  const email = useSearchParams().get('email') ?? '';
  const [info, setInfo] = useState('');
  const [isError, setIsError] = useState(false);
  const [sending, setSending] = useState(false);

  async function handleResend() {
    if (!email) return;
    setSending(true);
    setInfo('');
    try {
      await resendVerification(email);
      setIsError(false);
      setInfo('Dogrulama linki tekrar gonderildi.');
    } catch (err) {
      setIsError(true);
      setInfo(err instanceof Error ? err.message : 'Gonderilemedi');
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="relative w-full max-w-md mx-4 z-10">
      <div className="bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl shadow-2xl p-8 flex flex-col items-center text-center border border-white/10">
        <div className="flex items-center gap-2 mb-6">
          <span className="material-symbols-outlined text-primary text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
          <span className="text-2xl font-bold text-on-surface">Waylo</span>
        </div>
        <div className="w-16 h-16 rounded-full bg-tertiary-container flex items-center justify-center mb-4">
          <span className="material-symbols-outlined text-on-tertiary-container text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>mail</span>
        </div>
        <h1 className="text-lg font-semibold text-on-surface">E-postanizi Dogrulayin</h1>
        <p className="text-sm text-on-surface-variant mt-2 mb-6">
          {email ? <><strong>{email}</strong> adresine bir dogrulama linki gonderdik.</> : 'E-posta adresinize bir dogrulama linki gonderdik.'}
          {' '}Hesabinizi kullanmak icin linke tiklayin.
        </p>

        {info && (
          <p role="status" className={`text-sm mb-4 ${isError ? 'text-error' : 'text-on-surface-variant'}`}>{info}</p>
        )}

        <button type="button" onClick={handleResend} disabled={sending || !email}
          className="px-6 py-2.5 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg transition-all disabled:opacity-50">
          Tekrar gonder
        </button>
        <Link href="/login" className="mt-4 text-sm text-primary font-medium hover:text-primary-container transition-colors">
          Giris sayfasina don
        </Link>
      </div>
    </div>
  );
}

export default function VerifyEmailSentPage() {
  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">
      <VideoBackground />
      <Suspense fallback={null}>
        <SentContent />
      </Suspense>
    </div>
  );
}
