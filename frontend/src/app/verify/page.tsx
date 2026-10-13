'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import VideoBackground from '@/components/VideoBackground';

export default function VerifyPage() {
  const { verifyEmail } = useAuth();
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('Dogrulama tokeni bulunamadi');
      return;
    }

    verifyEmail(token).then(result => {
      setStatus(result.success ? 'success' : 'error');
      setMessage(result.message);
    }).catch(() => {
      setStatus('error');
      setMessage('Dogrulama sirasinda bir hata olustu');
    });
  }, [token, verifyEmail]);

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">
      <VideoBackground />

      <div className="relative w-full max-w-md mx-4 z-10">
        <div className="bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl shadow-2xl p-8 flex flex-col items-center text-center border border-white/10">
          <div className="flex items-center gap-2 mb-6">
            <span className="material-symbols-outlined text-primary text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
            <span className="text-2xl font-bold text-on-surface">Waylo</span>
          </div>

          {status === 'loading' && (
            <>
              <div className="w-12 h-12 border-3 border-primary/30 border-t-primary rounded-full animate-spin mb-4" />
              <h2 className="text-lg font-semibold text-on-surface">E-posta Dogrulaniyor...</h2>
              <p className="text-sm text-on-surface-variant mt-2">Lutfen bekleyin</p>
            </>
          )}

          {status === 'success' && (
            <>
              <div className="w-16 h-16 rounded-full bg-tertiary-container flex items-center justify-center mb-4">
                <span className="material-symbols-outlined text-on-tertiary-container text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
              </div>
              <h2 className="text-lg font-semibold text-on-surface">E-posta Dogrulandi!</h2>
              <p className="text-sm text-on-surface-variant mt-2 mb-6">{message}</p>
              <Link href="/login"
                className="px-6 py-2.5 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg transition-all">
                Giris Yap
              </Link>
            </>
          )}

          {status === 'error' && (
            <>
              <div className="w-16 h-16 rounded-full bg-error-container flex items-center justify-center mb-4">
                <span className="material-symbols-outlined text-on-error-container text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>error</span>
              </div>
              <h2 className="text-lg font-semibold text-on-surface">Dogrulama Basarisiz</h2>
              <p className="text-sm text-on-surface-variant mt-2 mb-6">{message}</p>
              <Link href="/login"
                className="px-6 py-2.5 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg transition-all">
                Girise Don
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
