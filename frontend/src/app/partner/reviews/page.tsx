'use client';

import { useCallback, useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { partnerErrorMessage } from '@/lib/partner';
import { reviewApi, type HotelReview, type HotelReviewPage } from '@/lib/reviews';
import { Button, Card, EmptyState, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const PAGE_SIZE = 20;

function ReviewRow({ review, orgId, onSaved }: { review: HotelReview; orgId: string; onSaved: () => void }) {
  const [text, setText] = useState(review.partnerResponse ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await reviewApi.respond(orgId, review.id, text.trim());
      onSaved();
    } catch (e) {
      setError(partnerErrorMessage(e, 'Yanit kaydedilemedi'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="p-4 space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-sm text-warning">{'★'.repeat(review.score)}<span className="text-outline">{'★'.repeat(5 - review.score)}</span></p>
        <div className="flex items-center gap-2">
          <span className="text-xs text-on-surface-variant">{new Date(review.createdAt).toLocaleDateString('tr-TR')}</span>
          <StatusPill tone={review.status === 'PUBLISHED' ? 'success' : 'error'}>{review.status === 'PUBLISHED' ? 'Yayinda' : 'Gizli'}</StatusPill>
        </div>
      </div>
      {review.text && <p className="text-sm text-on-surface">{review.text}</p>}
      <textarea className={inputClass} rows={2} maxLength={2000} value={text} onChange={e => setText(e.target.value)} placeholder="Isletme yaniti" />
      <div className="flex items-center gap-3">
        <Button disabled={busy || !text.trim()} onClick={() => void save()}>{review.partnerResponse ? 'Yaniti guncelle' : 'Yanitla'}</Button>
        {review.respondedAt && <span className="text-xs text-on-surface-variant">Yanit: {new Date(review.respondedAt).toLocaleDateString('tr-TR')}</span>}
      </div>
      {error && <p className="text-xs text-error">{error}</p>}
    </Card>
  );
}

export default function PartnerReviewsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<HotelReviewPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!organizationId) return;
    reviewApi.partnerList(organizationId, page, PAGE_SIZE)
      .then(r => { setData(r.data); setError(null); })
      .catch(e => setError(partnerErrorMessage(e, 'Degerlendirmeler yuklenemedi')))
      .finally(() => setLoading(false));
  }, [organizationId, page]);

  useEffect(() => { load(); }, [load]);

  if (workspaceLoading || loading) return <LoadingRows />;
  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Degerlendirmeler" subtitle="Konaklayan misafirlerin dogrulanmis yorumlari. Puan degistirilemez, yalnizca yanitlanabilir." />
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {data && data.items.length === 0 && !error && (
        <EmptyState icon="reviews" title="Henuz degerlendirme yok" />
      )}
      {data?.items.map(r => organizationId && <ReviewRow key={r.id} review={r} orgId={organizationId} onSaved={load} />)}
      {data && totalPages > 1 && (
        <div className="flex items-center gap-2 text-sm">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Onceki</Button>
          <span>{page + 1} / {totalPages}</span>
          <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}>Sonraki</Button>
        </div>
      )}
    </div>
  );
}
