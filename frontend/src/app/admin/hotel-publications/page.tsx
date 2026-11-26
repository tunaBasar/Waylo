'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import { orgLabel, orgNameMap } from '@/lib/org-names';
import { useToast } from '@/components/Toast';
import {
  adminApi, type HotelPublicationDetail, type HotelPublicationItem, type HotelStatusDecision, type PublicationReviewDecision,
} from '@/lib/admin';
import {
  PUBLICATION_STATUS_LABEL, PUBLICATION_STATUS_TONE, type PublicationRequestStatus,
} from '@/lib/partner';
import { MediaReview } from './MediaReview';
import { RequestPhotos } from './RequestPhotos';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const TABS: Array<{ key: PublicationRequestStatus; label: string }> = [
  { key: 'SUBMITTED', label: 'Incelenecek' },
  { key: 'APPROVED', label: 'Onaylanan' },
  { key: 'REJECTED', label: 'Reddedilen' },
  { key: 'WITHDRAWN', label: 'Geri cekilen' },
];
const KIND_LABEL = { INITIAL: 'Ilk yayin', CONTENT_CHANGE: 'Icerik degisikligi' } as const;
const FIELD_LABEL: Record<string, string> = {
  name: 'Otel adi', city: 'Sehir', country: 'Ulke', address: 'Adres', starRating: 'Yildiz (beyan edilen)',
  description: 'Aciklama', latitude: 'Enlem', longitude: 'Boylam',
};
const MIN_REASON = 5;
const fmtDate = (s: string) => new Date(s).toLocaleString('tr-TR');
const show = (v: unknown) => (v === null || v === undefined || v === '' ? '-' : String(v));

/** 409 responses carry a server message. A stale version needs a reload hint on top. */
function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError && e.status === 409) {
    if (e.code === 'VERSION_CONFLICT') return 'Kayit baska biri tarafindan degistirildi. Listeyi yenileyip tekrar deneyin.';
    return e.message || fallback;
  }
  return e instanceof Error && e.message ? e.message : fallback;
}

function Detail({ requestId, onChanged }: { requestId: string; onChanged: () => void }) {
  const toast = useToast();
  const [d, setD] = useState<HotelPublicationDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);

  const load = useCallback(async () => {
    try {
      setD((await adminApi.hotelPublication(requestId)).data);
      setError(null);
      setConflict(false);
    } catch (e) {
      setError(errorText(e, 'Detay yuklenemedi'));
    }
  }, [requestId]);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  async function run(action: () => Promise<unknown>, done: string) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await action();
      toast.success(done);
      setReason('');
      onChanged();
      await load();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && e.code === 'VERSION_CONFLICT') setConflict(true);
      toast.error(errorText(e, 'Islem basarisiz'));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (error) return <p className="text-sm text-error">{error}</p>;
  if (!d) return <LoadingRows rows={1} />;

  const open = d.status === 'SUBMITTED';
  const reasonOk = reason.trim().length >= MIN_REASON;
  const decide = (decision: PublicationReviewDecision, done: string) =>
    run(() => adminApi.decideHotelPublication(d.requestId, {
      decision, reason: decision === 'REJECT' ? reason.trim() : null, expectedVersion: d.version,
    }), done);
  const setStatus = (decision: HotelStatusDecision, done: string) =>
    run(() => adminApi.setHotelPublicationStatus(d.hotelId, { decision, reason: decision === 'PUBLISH' ? null : reason.trim() }), done);
  const hs = d.hotelPublicationStatus;
  const statusActions: Array<{ decision: HotelStatusDecision; label: string; done: string; variant: 'primary' | 'secondary' | 'danger'; needsReason: boolean }> = [
    ...(hs === 'APPROVED' ? [{ decision: 'PUBLISH' as const, label: 'Yayinla', done: 'Otel yayinlandi', variant: 'primary' as const, needsReason: false }] : []),
    ...(hs === 'PUBLISHED' ? [{ decision: 'SUSPEND' as const, label: 'Askiya al', done: 'Otel askiya alindi', variant: 'danger' as const, needsReason: true }] : []),
    ...(hs === 'SUSPENDED' ? [{ decision: 'REINSTATE' as const, label: 'Yeniden ac', done: 'Otel yeniden acildi', variant: 'secondary' as const, needsReason: true }] : []),
    ...(['DRAFT', 'REJECTED', 'APPROVED', 'SUSPENDED'].includes(hs) ? [{ decision: 'ARCHIVE' as const, label: 'Arsivle', done: 'Otel arsivlendi', variant: 'danger' as const, needsReason: true }] : []),
  ];

  const rows: Array<{ field: string; from: unknown; to: unknown }> = d.kind === 'CONTENT_CHANGE' ? d.diff : [];

  return (
    <div className="space-y-4 border-t border-outline-variant/40 pt-3">
      <div>
        <p className="text-xs font-semibold text-on-surface-variant mb-1">Otelin yayindaki bilgileri</p>
        <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
          {(['name', 'city', 'country', 'address', 'starRating', 'description'] as const).map(k => (
            <div key={k}><dt className="inline text-on-surface-variant">{FIELD_LABEL[k]}: </dt><dd className="inline text-on-surface">{show(d.hotel?.[k])}</dd></div>
          ))}
        </dl>
      </div>

      {d.kind === 'CONTENT_CHANGE' && (
        <div>
          <p className="text-xs font-semibold text-on-surface-variant mb-1">Degisiklikler</p>
          {rows.length === 0 ? <p className="text-sm text-outline">Fark yok</p> : (
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs text-on-surface-variant"><th className="py-1 pr-3">Alan</th><th className="pr-3">Mevcut</th><th>Yeni</th></tr></thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.field} className="border-t border-outline-variant/30 align-top">
                    <td className="py-1 pr-3">{FIELD_LABEL[r.field] ?? r.field}</td>
                    <td className="pr-3 text-on-surface-variant">{show(r.from)}</td>
                    <td className="text-on-surface">{show(r.to)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <RequestPhotos media={d.media ?? []} onChanged={() => void load()} />

      {d.note && <p className="text-sm"><span className="text-on-surface-variant">Partner notu: </span>{d.note}</p>}

      <div>
        <p className="text-xs font-semibold text-on-surface-variant mb-1">Karar gecmisi</p>
        {(d.decisions ?? []).length === 0 ? <p className="text-sm text-outline">Henuz karar yok</p> : (
          <ul className="space-y-1 text-sm">
            {d.decisions.map((x, i) => (
              <li key={i}>
                <span className="font-medium">{x.decision}</span> <span className="text-outline">({x.actorRole}, {fmtDate(x.createdAt)})</span>
                {x.reason && <span className="text-on-surface-variant">: {x.reason}</span>}
              </li>
            ))}
          </ul>
        )}
      </div>

      {conflict && (
        <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">
          Bu talep baska biri tarafindan degistirildi. Guncel hali icin yenileyin.
          <Button variant="ghost" className="ml-2" onClick={() => void load()}>Yenile</Button>
        </p>
      )}

      {(open || statusActions.length > 0) && (
        <div className="space-y-3">
          <Field label={open ? 'Gerekce (reddetmek icin en az 5 karakter)' : 'Gerekce (askiya alma, yeniden acma ve arsivleme icin en az 5 karakter)'}>
            <textarea className={inputClass} rows={2} maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} />
          </Field>
          <div className="flex flex-wrap gap-2">
            {open && (
              <>
                <Button icon="check" disabled={busy} onClick={() => void decide('APPROVE', 'Talep onaylandi')}>Onayla</Button>
                <Button icon="publish" variant="secondary" disabled={busy} onClick={() => void decide('APPROVE_AND_PUBLISH', 'Otel yayinlandi')}>Onayla ve yayinla</Button>
                <Button icon="close" variant="danger" disabled={busy || !reasonOk} onClick={() => void decide('REJECT', 'Talep reddedildi')}>Reddet</Button>
              </>
            )}
            {statusActions.map(a => (
              <Button key={a.decision} variant={a.variant} disabled={busy || (a.needsReason && !reasonOk)}
                onClick={() => void setStatus(a.decision, a.done)}>{a.label}</Button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function AdminHotelPublicationsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [status, setStatus] = useState<PublicationRequestStatus>('SUBMITTED');
  const [items, setItems] = useState<HotelPublicationItem[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [section, setSection] = useState<'requests' | 'media'>('requests');
  const [orgNames, setOrgNames] = useState<Record<string, string>>({});

  // One-time id -> name lookup; a failure just leaves the short id in place
  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    void Promise.all((['PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'] as const).map(s => adminApi.organizations(s).then(r => r.data ?? [], () => [])))
      .then(lists => { if (!cancelled) setOrgNames(orgNameMap(lists)); });
    return () => { cancelled = true; };
  }, [isAdmin]);

  const load = useCallback(async (s: PublicationRequestStatus, p: number) => {
    setLoading(true);
    try {
      const res = (await adminApi.hotelPublications(s, p)).data;
      setItems(res?.items ?? []);
      setTotal(res?.total ?? 0);
      setSize(res?.size || 20);
      setError(null);
    } catch (e) {
      setError(errorText(e, 'Yayin talepleri yuklenemedi'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(status, page); });
    return () => { cancelled = true; };
  }, [isAdmin, status, page, load]);

  if (!isAdmin) {
    return <EmptyState icon="lock" title="Bu sayfa yalnizca yoneticiler icindir" />;
  }

  const pages = Math.max(1, Math.ceil(total / size));

  return (
    <div className="space-y-space-md">
      <PageHeader title="Otel Yayinlari" subtitle="Partner otellerinin yayin ve icerik degisikligi taleplerini inceleyin." />
      <div className="flex gap-2">
        <Button variant={section === 'requests' ? 'primary' : 'ghost'} onClick={() => setSection('requests')}>Yayin talepleri</Button>
        <Button variant={section === 'media' ? 'primary' : 'ghost'} onClick={() => setSection('media')}>Fotograflar</Button>
      </div>
      {section === 'media' ? <MediaReview /> : (<>
      <div className="flex flex-wrap gap-1.5">
        {TABS.map(t => (
          <button key={t.key} onClick={() => { setStatus(t.key); setPage(0); setSelected(null); }}
            className={`px-3 py-1 rounded-full text-xs font-medium ${status === t.key ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-on-surface-variant'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : items.length === 0 ? <EmptyState icon="storefront" title="Bu durumda talep yok" />
        : (
          <div className="space-y-2">
            {items.map(it => (
              <Card key={it.requestId} className="p-4 space-y-3">
                <button className="w-full text-left" onClick={() => setSelected(selected === it.requestId ? null : it.requestId)}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-semibold text-on-surface truncate">{it.hotelName}</p>
                      <p className="text-xs text-outline">Isletme: <span title={it.organizationId}>{orgLabel(orgNames, it.organizationId)}</span></p>
                      <p className="text-xs text-on-surface-variant">{KIND_LABEL[it.kind]} &middot; {fmtDate(it.submittedAt)}{(it.mediaCount ?? 0) > 0 && <> &middot; {it.mediaCount} fotograf</>}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <StatusPill tone={PUBLICATION_STATUS_TONE[it.hotelPublicationStatus]}>{PUBLICATION_STATUS_LABEL[it.hotelPublicationStatus]}</StatusPill>
                      <StatusPill tone="neutral">{TABS.find(t => t.key === it.status)?.label ?? it.status}</StatusPill>
                    </div>
                  </div>
                </button>
                {selected === it.requestId && <Detail requestId={it.requestId} onChanged={() => void load(status, page)} />}
              </Card>
            ))}
            {pages > 1 && (
              <div className="flex items-center justify-center gap-3 pt-2">
                <Button variant="ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Onceki</Button>
                <span className="text-xs text-on-surface-variant">{page + 1} / {pages}</span>
                <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)}>Sonraki</Button>
              </div>
            )}
          </div>
        )}
      </>)}
    </div>
  );
}
