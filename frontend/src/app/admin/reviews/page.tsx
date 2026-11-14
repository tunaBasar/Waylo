'use client';

import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { reviewApi, type HotelReview, type HotelReviewPage } from '@/lib/reviews';

const PAGE_SIZE = 20;

function Row({ review, onDone }: { review: HotelReview; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const target = review.status === 'PUBLISHED' ? 'HIDDEN' : 'PUBLISHED';

  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      await reviewApi.moderate(review.id, target, reason.trim());
      setReason('');
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Islem basarisiz');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-sm p-4 space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-sm text-warning">{'★'.repeat(review.score)}<span className="text-outline">{'★'.repeat(5 - review.score)}</span></p>
        <span className={`text-xs font-semibold ${review.status === 'PUBLISHED' ? 'text-secondary' : 'text-error'}`}>
          {review.status === 'PUBLISHED' ? 'Yayinda' : 'Gizli'}
        </span>
      </div>
      {review.text && <p className="text-sm text-on-surface">{review.text}</p>}
      {review.partnerResponse && <p className="text-xs text-on-surface-variant">Isletme yaniti: {review.partnerResponse}</p>}
      {review.moderationReason && <p className="text-xs text-on-surface-variant">Son moderasyon gerekcesi: {review.moderationReason}</p>}
      <div className="flex flex-wrap items-center gap-2">
        <input value={reason} maxLength={500} onChange={e => setReason(e.target.value)} placeholder="Gerekce (zorunlu)"
          className="flex-1 min-w-[12rem] rounded-lg bg-surface-container p-2 text-sm text-on-surface" />
        <button type="button" disabled={busy || !reason.trim()} onClick={() => void run()}
          className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-on-primary disabled:opacity-50">
          {target === 'HIDDEN' ? 'Gizle' : 'Yayinla'}
        </button>
      </div>
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  );
}

export default function AdminReviewsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<HotelReviewPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!isAdmin) return;
    reviewApi.adminList(status, page, PAGE_SIZE)
      .then(r => { setData(r.data); setError(null); })
      .catch(e => setError(e instanceof ApiError ? e.message : 'Degerlendirmeler yuklenemedi'));
  }, [isAdmin, status, page]);

  useEffect(() => { load(); }, [load]);

  if (!isAdmin) return <p className="py-12 text-center text-sm text-on-surface-variant">Bu sayfa yalnizca yoneticiler icindir</p>;
  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">Otel Degerlendirmeleri</h2>
        <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }} className="rounded-lg bg-surface-container p-2 text-sm">
          <option value="">Hepsi</option>
          <option value="PUBLISHED">Yayinda</option>
          <option value="HIDDEN">Gizli</option>
        </select>
      </div>
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {data && data.items.length === 0 && !error && <p className="text-sm text-on-surface-variant">Degerlendirme bulunamadi</p>}
      {data?.items.map(r => <Row key={r.id} review={r} onDone={load} />)}
      {data && totalPages > 1 && (
        <div className="flex items-center gap-2 text-sm">
          <button type="button" disabled={page === 0} onClick={() => setPage(p => p - 1)} className="rounded-lg bg-surface-container px-3 py-1 disabled:opacity-50">Onceki</button>
          <span>{page + 1} / {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)} className="rounded-lg bg-surface-container px-3 py-1 disabled:opacity-50">Sonraki</button>
        </div>
      )}
    </div>
  );
}
