'use client';

import { useState } from 'react';
import { ApiError } from '@/lib/api';
import { reviewApi, type ReviewableItem } from '@/lib/reviews';

/** "Degerlendir" button and 1-5 + text form for one eligible hotel order item. */
export function ReviewForm({ item, onDone }: { item: ReviewableItem; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [score, setScore] = useState(0);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const submit = async () => {
    if (score < 1) { setMsg('Lutfen 1-5 arasi puan secin'); return; }
    setBusy(true);
    setMsg(null);
    try {
      await reviewApi.create(item.orderItemId, score, text);
      onDone();
    } catch (err) {
      setMsg(err instanceof ApiError ? err.message : 'Degerlendirme gonderilemedi');
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} data-testid="review-open"
        className="mt-2 rounded-xl bg-secondary-container px-4 py-2 text-sm font-semibold text-on-secondary-container">Degerlendir</button>
    );
  }
  return (
    <div className="mt-2 space-y-2 rounded-xl bg-surface-container p-3" data-testid="review-form">
      <p className="text-sm font-semibold text-on-surface">{item.hotelName}</p>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map(n => (
          <button key={n} type="button" aria-label={`${n} puan`} onClick={() => setScore(n)}
            className={`text-2xl ${n <= score ? 'text-warning' : 'text-outline'}`}>★</button>
        ))}
      </div>
      <textarea value={text} maxLength={2000} onChange={e => setText(e.target.value)} rows={3} placeholder="Yorumunuz (istege bagli)"
        className="w-full rounded-lg bg-surface-container-lowest p-2 text-sm text-on-surface" />
      <div className="flex gap-2">
        <button type="button" disabled={busy} onClick={() => void submit()}
          className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-on-primary disabled:opacity-50">Gonder</button>
        <button type="button" disabled={busy} onClick={() => setOpen(false)}
          className="rounded-xl bg-surface-container-lowest px-4 py-2 text-sm font-semibold text-on-surface">Vazgec</button>
      </div>
      {msg && <p className="text-xs text-error">{msg}</p>}
    </div>
  );
}
