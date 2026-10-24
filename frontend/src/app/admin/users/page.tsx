'use client';

import { FormEvent, useState } from 'react';
import { useToast } from '@/components/Toast';
import { useAuth } from '@/lib/auth';
import { adminApi, type AdminUser } from '@/lib/admin';
import { Button, Card, EmptyState, Field, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

export default function AdminUsersPage() {
  const toast = useToast();
  const { user: me } = useAuth();
  const [email, setEmail] = useState('');
  const [found, setFound] = useState<AdminUser | null>(null);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function lookup(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() || busy) return;
    setBusy(true);
    setFound(null);
    setError(null);
    try {
      setFound((await adminApi.userByEmail(email.trim())).data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Kullanici bulunamadi');
    } finally {
      setSearched(true);
      setBusy(false);
    }
  }

  async function changeRole(role: 'USER' | 'ADMIN') {
    if (!found || busy) return;
    setBusy(true);
    try {
      setFound((await adminApi.changeRole(found.id, role)).data);
      toast.success('Rol guncellendi');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Rol guncellenemedi');
    } finally {
      setBusy(false);
    }
  }

  const target = found?.role === 'ADMIN' ? 'USER' : 'ADMIN';
  return (
    <div className="space-y-space-md">
      <PageHeader title="Kullanicilar" subtitle="E-posta ile kullanici bulun ve platform rolunu degistirin." />
      <Card className="p-4">
        <form onSubmit={lookup} className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[220px]">
            <Field label="E-posta"><input type="email" required className={inputClass} value={email} onChange={e => setEmail(e.target.value)} /></Field>
          </div>
          <Button type="submit" icon="search" disabled={busy || !email.trim()}>Ara</Button>
        </form>
      </Card>
      {found && (
        <Card className="p-4 flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="font-semibold text-on-surface">{found.fullName}</p>
            <p className="text-sm text-on-surface-variant">{found.email}</p>
            <div className="mt-1 flex gap-2">
              <StatusPill tone={found.role === 'ADMIN' ? 'warning' : 'neutral'}>{found.role === 'ADMIN' ? 'Yonetici' : 'Kullanici'}</StatusPill>
              <StatusPill tone={found.verified ? 'success' : 'neutral'}>{found.verified ? 'E-posta dogrulandi' : 'Dogrulanmadi'}</StatusPill>
            </div>
          </div>
          <Button variant={target === 'ADMIN' ? 'primary' : 'danger'} disabled={busy || found.id === me?.id}
            title={found.id === me?.id ? 'Kendi rolunuzu degistiremezsiniz' : undefined}
            onClick={() => void changeRole(target)}>
            {target === 'ADMIN' ? 'Yonetici yap' : 'Yoneticilikten cikar'}
          </Button>
        </Card>
      )}
      {searched && !found && <EmptyState icon="person_search" title="Kullanici bulunamadi" description={error ?? undefined} />}
    </div>
  );
}
