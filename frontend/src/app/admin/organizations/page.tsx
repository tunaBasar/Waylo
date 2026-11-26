'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { useSingleFlight } from '@/lib/use-single-flight';
import { api } from '@/lib/api';
import {
  VERTICAL_TR, hasIdenticalOverride, keepTypedPercent, parseCommissionPercent, prefillPercent, verticalOf, type RateRow,
} from '@/lib/commission';
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
  const flight = useSingleFlight();
  const [status, setStatus] = useState<OrgStatus>('PENDING');
  const [orgs, setOrgs] = useState<AdminOrganization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [suspending, setSuspending] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [approving, setApproving] = useState<string | null>(null);
  const [percent, setPercent] = useState('');
  const [approveError, setApproveError] = useState<string | null>(null);
  const [rateRows, setRateRows] = useState<RateRow[]>([]);

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

  async function run(id: string, action: () => Promise<unknown>, done: string, key = `org-action:${id}`) {
    if (busy) return;
    await flight(key, async () => {
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
    });
  }

  async function openApprove(o: AdminOrganization) {
    if (approving === o.id) { setApproving(null); return; }
    setApproveError(null);
    setPercent('');
    setApproving(o.id);
    try {
      const rows = (await api.get<RateRow[]>('/api/admin/commission-rates')).data ?? [];
      setRateRows(rows);
      // The rates arrive after the form is open: only prefill an untouched field, never overwrite what the admin
      // already typed (the full Playwright run caught "12,5" being wiped and the button staying disabled).
      const suggestion = prefillPercent(rows, verticalOf(o.businessType), o.id);
      setPercent(current => keepTypedPercent(current, suggestion));
    } catch {
      setRateRows([]);
    }
  }

  async function confirmApprove(o: AdminOrganization) {
    if (busy) return;
    const vertical = verticalOf(o.businessType);
    const fraction = parseCommissionPercent(percent);
    if (!vertical) { setApproveError('Isletme tipi bilinmiyor, komisyon orani belirlenemiyor.'); return; }
    if (fraction == null) { setApproveError('Komisyon orani 0 ile 50 arasinda olmali. Ornek: 10 veya 12,5'); return; }
    await flight(`org-approve:${o.id}`, async () => {
      setBusy(o.id);
      setApproveError(null);
      try {
        if (!hasIdenticalOverride(rateRows, vertical, o.id, fraction)) {
          try {
            await api.post('/api/admin/commission-rates', { vertical, supplierOrganizationId: o.id, rate: fraction });
          } catch (e) {
            setApproveError(`Komisyon orani kaydedilemedi, isletme onaylanmadi: ${e instanceof Error ? e.message : 'hata'}`);
            return;
          }
        }
        try {
          await adminApi.approveOrganization(o.id);
        } catch (e) {
          setApproveError(`Komisyon orani kaydedildi ancak onay basarisiz: ${e instanceof Error ? e.message : 'hata'}. Tekrar Onayla deneyebilirsiniz.`);
          const rows = (await api.get<RateRow[]>('/api/admin/commission-rates').catch(() => null))?.data;
          if (rows) setRateRows(rows);
          return;
        }
        toast.success('Isletme onaylandi');
        setApproving(null);
        await load(status);
      } finally {
        setBusy(null);
      }
    });
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
                        onClick={() => void openApprove(o)}>Onayla</Button>
                    )}
                    {(o.status === 'PENDING' || o.status === 'ACTIVE') && (
                      <Button variant="danger" icon="block" disabled={busy === o.id}
                        onClick={() => { setSuspending(suspending === o.id ? null : o.id); setReason(''); }}>Askiya al</Button>
                    )}
                  </div>
                </div>
                {approving === o.id && (
                  <form className="flex flex-wrap items-end gap-3" data-testid="approve-form" onSubmit={e => { e.preventDefault(); void confirmApprove(o); }}>
                    <div className="min-w-[220px]">
                      <Field label={`Komisyon orani (%) - ${verticalOf(o.businessType) ? VERTICAL_TR[verticalOf(o.businessType)!] : 'tip bilinmiyor'}`}
                        hint="Bu isletmeye ozel oran kaydedilir, sonra isletme onaylanir.">
                        <input className={inputClass} inputMode="decimal" value={percent} autoFocus placeholder="Ornek: 10"
                          onChange={e => setPercent(e.target.value)} />
                      </Field>
                    </div>
                    <Button type="submit" icon="check" disabled={busy === o.id || !percent.trim()}>Kaydet ve onayla</Button>
                    <Button type="button" variant="ghost" onClick={() => setApproving(null)}>Vazgec</Button>
                    {approveError && <p className="basis-full text-xs text-error">{approveError}</p>}
                  </form>
                )}
                {suspending === o.id && (
                  <form className="flex flex-wrap items-end gap-3" onSubmit={e => {
                    e.preventDefault();
                    if (reason.trim()) void run(o.id, () => adminApi.suspendOrganization(o.id, reason.trim()), 'Isletme askiya alindi', `org-suspend:${o.id}`);
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
