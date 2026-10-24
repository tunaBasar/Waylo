'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function SettingsPage() {
  const { logout } = useAuth();
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [password, setPassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const deleteAccount = async () => {
    setDeleteError(null);
    setDeleting(true);
    try {
      await api.del('/api/me', { password });
      logout('/login?message=Hesabiniz%20silindi');
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 400) {
          setDeleteError('Sifre hatali');
        } else if (error.status === 422 && error.message.startsWith('SOLE_OWNER')) {
          setDeleteError('Hesabinizi silmeden once organizasyonunuzun sahipligini devretmeli veya organizasyonunuza baska bir sahip eklemelisiniz.');
        } else {
          setDeleteError(error.message);
        }
      } else {
        setDeleteError('Hesap silinirken bir hata olustu');
      }
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-space-md max-w-lg mx-auto">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Ayarlar</h2>

      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-5">
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-primary text-lg">person</span>
          <h4 className="text-sm font-semibold text-on-surface">Profil</h4>
        </div>
        <p className="text-xs text-on-surface-variant mb-4">Adinizi ve sifrenizi profil sayfasindan degistirebilirsiniz.</p>
        <Link href="/profile" className="inline-flex items-center gap-1 px-4 py-2 rounded-xl bg-primary text-on-primary text-xs font-medium">
          Profile git <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
        </Link>
      </div>

      <div className="bg-error-container/30 rounded-2xl p-5">
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-error text-lg">warning</span>
          <h4 className="text-sm font-semibold text-error">Tehlikeli Bolge</h4>
        </div>
        <p className="text-xs text-on-surface-variant mb-3">Hesabinizi silmek geri alinamaz bir islemdir.</p>
        {!confirmingDelete ? (
          <button onClick={() => setConfirmingDelete(true)} className="px-4 py-2 rounded-xl bg-error text-on-error text-xs font-medium shadow-sm active:scale-95 transition-all">
            Hesabi Sil
          </button>
        ) : (
          <div className="space-y-3">
            <label className="block text-xs font-medium text-on-surface" htmlFor="delete-password">Mevcut sifreniz</label>
            <input id="delete-password" type="password" value={password} onChange={event => setPassword(event.target.value)} className="w-full px-3 py-2 rounded-xl bg-surface-container-lowest text-sm text-on-surface outline-none" autoComplete="current-password" />
            {deleteError && <p className="text-xs text-error">{deleteError}</p>}
            <div className="flex gap-2">
              <button onClick={deleteAccount} disabled={deleting || !password} className="px-4 py-2 rounded-xl bg-error text-on-error text-xs font-medium disabled:opacity-50">
                {deleting ? 'Siliniyor...' : 'Silmeyi onayla'}
              </button>
              <button onClick={() => { setConfirmingDelete(false); setPassword(''); setDeleteError(null); }} disabled={deleting} className="px-4 py-2 rounded-xl bg-surface-container text-on-surface text-xs font-medium">
                Vazgec
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
