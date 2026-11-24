'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast';
import { adminMediaApi, type AdminHotelMedia } from '@/lib/admin';
import { MEDIA_STATUS_LABEL, MEDIA_STATUS_TONE, type MediaReviewStatus } from '@/lib/partner';
import { Button, Card, EmptyState, Field, LoadingRows, StatusPill, inputClass } from '@/components/partner/ui';

const FILTERS: MediaReviewStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];
const MIN_REASON = 5;

function Preview({ item }: { item: AdminHotelMedia }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    adminMediaApi.file(item.media.id).then(b => {
      if (cancelled) return;
      url = URL.createObjectURL(b);
      setSrc(url);
    }).catch(() => { /* no preview */ });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [item.media.id]);
  if (!src) return <div className="h-40 w-full rounded-lg bg-surface-container" />;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={item.media.caption ?? 'Otel fotografi'} className="h-40 w-full rounded-lg object-cover" />;
}

function Row({ item, onChanged }: { item: AdminHotelMedia; onChanged: () => void }) {
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const m = item.media;

  const decide = async (decision: 'APPROVE' | 'REJECT') => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await adminMediaApi.decide(m.id, { decision, reason: decision === 'REJECT' ? reason.trim() : null });
      toast.success(decision === 'APPROVE' ? 'Fotograf onaylandi' : 'Fotograf reddedildi');
      onChanged();
    } catch (e) {
      toast.error(e instanceof ApiError || e instanceof Error ? e.message : 'Islem basarisiz');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Card className="p-4 space-y-3">
      <Preview item={item} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-on-surface truncate">{item.hotelName ?? m.hotelId}</p>
          <p className="text-xs text-outline">Isletme: {item.organizationId}</p>
          <p className="text-xs text-on-surface-variant">
            {m.width}x{m.height} &middot; {(m.sizeBytes / 1024).toFixed(0)} KB &middot; {m.contentType} &middot; {new Date(m.createdAt).toLocaleString('tr-TR')}
          </p>
          {m.caption && <p className="text-xs text-on-surface-variant">Aciklama: {m.caption}</p>}
          <p className="text-xs text-on-surface-variant">Hak beyani: {m.rightsSource}{m.rightsNote ? ` (${m.rightsNote})` : ''}</p>
        </div>
        <StatusPill tone={MEDIA_STATUS_TONE[m.reviewStatus]}>{MEDIA_STATUS_LABEL[m.reviewStatus]}</StatusPill>
      </div>
      {m.reviewStatus === 'REJECTED' && m.reviewReason && <p className="text-xs text-error">Red gerekcesi: {m.reviewReason}</p>}
      {item.decisions.length > 0 && (
        <ul className="text-xs text-on-surface-variant space-y-0.5">
          {item.decisions.map(d => (
            <li key={d.id}>{new Date(d.createdAt).toLocaleString('tr-TR')}: {d.fromStatus} &rarr; {d.toStatus}{d.reason ? ` (${d.reason})` : ''}</li>
          ))}
        </ul>
      )}
      <Field label="Gerekce (reddetmek icin en az 5 karakter)">
        <textarea className={inputClass} rows={2} maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} />
      </Field>
      <div className="flex gap-2">
        <Button icon="check" disabled={busy || m.reviewStatus === 'APPROVED'} onClick={() => void decide('APPROVE')}>Onayla</Button>
        <Button icon="close" variant="danger" disabled={busy || m.reviewStatus === 'REJECTED' || reason.trim().length < MIN_REASON}
          onClick={() => void decide('REJECT')}>Reddet</Button>
      </div>
    </Card>
  );
}

export function MediaReview() {
  const [status, setStatus] = useState<MediaReviewStatus>('PENDING');
  const [items, setItems] = useState<AdminHotelMedia[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (s: MediaReviewStatus, p: number) => {
    setLoading(true);
    try {
      const res = (await adminMediaApi.list(s, p)).data;
      setItems(res?.items ?? []);
      setTotal(res?.total ?? 0);
      setSize(res?.size || 20);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Fotograflar yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(status, page); });
    return () => { cancelled = true; };
  }, [status, page, load]);

  const pages = Math.max(1, Math.ceil(total / size));

  return (
    <div className="space-y-space-md">
      <div className="flex flex-wrap gap-1.5">
        {FILTERS.map(f => (
          <button key={f} onClick={() => { setStatus(f); setPage(0); }}
            className={`px-3 py-1 rounded-full text-xs font-medium ${status === f ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-on-surface-variant'}`}>
            {MEDIA_STATUS_LABEL[f]}
          </button>
        ))}
      </div>
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : items.length === 0 ? <EmptyState icon="photo_library" title="Bu durumda fotograf yok" />
        : (
          <div className="grid gap-3 md:grid-cols-2">
            {items.map(it => <Row key={it.media.id} item={it} onChanged={() => void load(status, page)} />)}
          </div>
        )}
      {pages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-2">
          <Button variant="ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Onceki</Button>
          <span className="text-xs text-on-surface-variant">{page + 1} / {pages}</span>
          <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)}>Sonraki</Button>
        </div>
      )}
    </div>
  );
}
