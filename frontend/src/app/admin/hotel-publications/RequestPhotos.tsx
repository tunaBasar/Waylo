'use client';

import { useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast';
import { adminMediaApi } from '@/lib/admin';
import { MEDIA_STATUS_LABEL, MEDIA_STATUS_TONE, type HotelMediaItem } from '@/lib/partner';
import { Button, StatusPill, inputClass } from '@/components/partner/ui';

const MIN_REASON = 5;

function Thumb({ media }: { media: HotelMediaItem }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    adminMediaApi.file(media.id).then(b => {
      if (cancelled) return;
      url = URL.createObjectURL(b);
      setSrc(url);
    }).catch(() => { /* no preview */ });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [media.id]);
  if (!src) return <div className="h-28 w-full rounded-lg bg-surface-container" />;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={media.caption ?? 'Otel fotografi'} className="h-28 w-full rounded-lg object-cover" />;
}

function Photo({ media, onChanged }: { media: HotelMediaItem; onChanged: () => void }) {
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);

  const reject = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await adminMediaApi.decide(media.id, { decision: 'REJECT', reason: reason.trim() });
      toast.success('Fotograf reddedildi');
      setReason('');
      onChanged();
    } catch (e) {
      toast.error(e instanceof ApiError || e instanceof Error ? e.message : 'Islem basarisiz');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <div className="space-y-2 rounded-lg border border-outline-variant/40 p-2">
      <Thumb media={media} />
      <div className="flex items-center justify-between gap-2">
        <p className="min-w-0 truncate text-xs text-on-surface-variant">{media.caption || `${media.width}x${media.height}`}</p>
        <StatusPill tone={MEDIA_STATUS_TONE[media.reviewStatus]}>{MEDIA_STATUS_LABEL[media.reviewStatus]}</StatusPill>
      </div>
      {media.reviewStatus === 'REJECTED' && media.reviewReason && <p className="text-xs text-error">Red gerekcesi: {media.reviewReason}</p>}
      {media.reviewStatus === 'PENDING' && (
        <>
          <input className={inputClass} maxLength={1000} placeholder="Red gerekcesi (en az 5 karakter)" value={reason} onChange={e => setReason(e.target.value)} />
          <Button icon="close" variant="danger" disabled={busy || reason.trim().length < MIN_REASON} onClick={() => void reject()}>Reddet</Button>
        </>
      )}
    </div>
  );
}

export function RequestPhotos({ media, onChanged }: { media: HotelMediaItem[]; onChanged: () => void }) {
  if (media.length === 0) return null;
  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold text-on-surface-variant">Fotograflar ({media.length})</p>
      <p className="text-xs text-on-surface-variant">Onayla, hala incelemede olan fotograflari da onaylar. Istemediginiz fotografi once tek tek reddedin.</p>
      <div className="grid gap-2 sm:grid-cols-3">
        {media.map(m => <Photo key={m.id} media={m} onChanged={onChanged} />)}
      </div>
    </div>
  );
}
