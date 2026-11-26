'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { adminApi, type AdminOrganization, type OrgStatus } from '@/lib/admin';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const STATUSES: Array<{ key: OrgStatus; label: string }> = [
  { key: 'PENDING', label: 'Onay bekleyen' },
  { key: 'ACTIVE', label: 'Aktif' },
  { key: 'SUSPENDED', label: 'Askiya alinmis' },
  { key: 'ARCHIVED', label: 'Arsiv' },
];
const TONE: Record<OrgStatus, 'success' | 'warning' | 'error' | 'neutral'> = { ACTIVE: 'success', PENDING: 'warning', SUSPENDED: 'error', ARCHIVED: 'neutral' };

const BUSINESS_LABEL: Record<string, string> = { HOTEL: 'Otel', CAR_RENTAL: 'Arac kiralama', BUS: 'Otobus firmasi' };
const LEGAL_LABEL: Record<string, string> = { COMPANY: 'Sirket', INDIVIDUAL: 'Sahis' };

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Only what the API returns: business type, legal type, created date, owner. */
function orgDetails(o: AdminOrganization): string {
  const parts: string[] = [];
  if (o.businessType) parts.push(BUSINESS_LABEL[o.businessType] ?? o.businessType);
  if (o.legalType) parts.push(LEGAL_LABEL[o.legalType] ?? o.legalType);
  if (o.createdAt) parts.push(`Basvuru: ${fmtDateTime(o.createdAt)}`);
  if (o.ownerName || o.ownerEmail) parts.push(`Sahip: ${[o.ownerName, o.ownerEmail].filter(Boolean).join(' · ')}`);
  return parts.join(' · ');
}

export default function AdminOrganizationsPage() {
  const toast = useToast();
  const [status, setStatus] = useState<OrgStatus>('PENDING');
  const [orgs, setOrgs] = useState<AdminOrganization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [suspending, setSuspending] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  const load = useCallback(async (s: OrgStatus) => {
    setLoading(true);
    try {
      setOrgs((await adminApi.organizations(s)).data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Isletmeler yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(status); });
    return () => { cancelled = true; };
  }, [status, load]);

  async function run(id: string, action: () => Promise<unknown>, done: string) {
    if (busy) return;
    setBusy(id);
    try {
      await action();
      toast.success(done);
      setSuspending(null);
      setReason('');
      await load(status);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Islem basarisiz');
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Isletmeler" subtitle="Partner isletme basvurularini onaylayin veya askiya alin." />
      <p className="text-xs text-on-surface-variant">Isletme onayi oteli yayinlamaz, otel yayin onayi ayri verilir.</p>
      <div className="flex flex-wrap gap-1.5">
        {STATUSES.map(s => (
          <button key={s.key} onClick={() => setStatus(s.key)}
            className={`px-3 py-1 rounded-full text-xs font-medium ${status === s.key ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-on-surface-variant'}`}>
            {s.label}
          </button>
        ))}
      </div>
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : orgs.length === 0 ? <EmptyState icon="domain" title="Bu durumda isletme yok" />
        : (
          <div className="space-y-2">
            {orgs.map(o => (
              <Card key={o.id} className="p-4 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-on-surface truncate">{o.name}</p>
                    <p className="text-xs text-on-surface-variant" data-testid="org-details">{orgDetails(o)}</p>
                    <p className="text-[10px] text-outline" title={o.id}>{o.id}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusPill tone={TONE[o.status]}>{STATUSES.find(s => s.key === o.status)?.label ?? o.status}</StatusPill>
                    {o.status === 'PENDING' && (
                      <Button icon="check" disabled={busy === o.id}
                        onClick={() => void run(o.id, () => adminApi.approveOrganization(o.id), 'Isletme onaylandi')}>Onayla</Button>
                    )}
                    {(o.status === 'PENDING' || o.status === 'ACTIVE') && (
                      <Button variant="danger" icon="block" disabled={busy === o.id}
                        onClick={() => { setSuspending(suspending === o.id ? null : o.id); setReason(''); }}>Askiya al</Button>
                    )}
                  </div>
                </div>
                {suspending === o.id && (
                  <form className="flex flex-wrap items-end gap-3" onSubmit={e => {
                    e.preventDefault();
                    if (reason.trim()) void run(o.id, () => adminApi.suspendOrganization(o.id, reason.trim()), 'Isletme askiya alindi');
                  }}>
                    <div className="flex-1 min-w-[220px]">
                      <Field label="Askiya alma nedeni">
                        <input className={inputClass} value={reason} maxLength={500} autoFocus onChange={e => setReason(e.target.value)} />
                      </Field>
                    </div>
                    <Button type="submit" variant="danger" disabled={!reason.trim() || busy === o.id}>Onayla</Button>
                  </form>
                )}
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
