'use client';

import { useEffect, useState } from 'react';
import { reviewApi, scoreLabel, type HotelReviewPage } from '@/lib/reviews';

const PAGE = 10;

/** Public review list of one hotel. Renders nothing while there is no data (no placeholder reviews). */
export function HotelReviews({ hotelId }: { hotelId: string }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<HotelReviewPage | null>(null);

  useEffect(() => {
    let alive = true;
    reviewApi.publicList(hotelId, page, PAGE)
      .then(r => { if (alive) setData(r.data); })
      .catch(() => { if (alive) setData(null); });
    return () => { alive = false; };
  }, [hotelId, page]);

  if (!data || data.total === 0) return null;
  const pages = Math.max(1, Math.ceil(data.total / PAGE));
  return (
    <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm space-y-3" data-testid="hotel-reviews">
      <h3 className="text-lg font-semibold text-on-surface">Degerlendirmeler</h3>
      <p className="text-sm text-on-surface-variant" data-testid="review-summary">
        <span className="font-bold text-on-surface">{scoreLabel(data.averageScore)}</span> / 5 · {data.reviewCount} degerlendirme
      </p>
      {data.items.map(r => (
        <div key={r.id} className="border-t border-outline-variant/40 pt-3 space-y-1">
          <p className="text-sm text-warning">{'★'.repeat(r.score)}<span className="text-outline">{'★'.repeat(5 - r.score)}</span></p>
          {r.text && <p className="text-sm text-on-surface">{r.text}</p>}
          <p className="text-xs text-on-surface-variant">{new Date(r.createdAt).toLocaleDateString('tr-TR')} · Dogrulanmis konaklama</p>
          {r.partnerResponse && (
            <div className="rounded-lg bg-surface-container p-2 text-xs text-on-surface">
              <p className="font-semibold">Isletme yaniti</p>
              <p>{r.partnerResponse}</p>
            </div>
          )}
        </div>
      ))}
      {pages > 1 && (
        <div className="flex items-center gap-2 text-xs">
          <button type="button" disabled={page === 0} onClick={() => setPage(p => p - 1)} className="rounded-lg bg-surface-container px-3 py-1 disabled:opacity-50">Onceki</button>
          <span>{page + 1} / {pages}</span>
          <button type="button" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} className="rounded-lg bg-surface-container px-3 py-1 disabled:opacity-50">Sonraki</button>
        </div>
      )}
    </div>
  );
}
