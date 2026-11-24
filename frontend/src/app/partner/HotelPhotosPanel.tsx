'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import {
  hotelMediaApi, validatePhotoFile, moveId, MEDIA_STATUS_LABEL, MEDIA_STATUS_TONE, HOTEL_PHOTO_MAX_COUNT,
  type HotelMediaItem, type PartnerHotel,
} from '@/lib/partner';
import { Button, Card, Field, StatusPill, inputClass } from '@/components/partner/ui';

/** Own photo bytes need the bearer token, so each thumbnail is fetched as a blob. */
function Thumb({ hotelId, item }: { hotelId: string; item: HotelMediaItem }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    hotelMediaApi.file(hotelId, item.id).then(b => {
      if (cancelled) return;
      url = URL.createObjectURL(b);
      setSrc(url);
    }).catch(() => { /* no thumbnail: the card still shows its status */ });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [hotelId, item.id]);
  if (!src) return <div className="h-32 w-full rounded-lg bg-surface-container" />;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={item.caption ?? 'Otel fotografi'} className="h-32 w-full rounded-lg object-cover" />;
}

export function HotelPhotosPanel({ hotel, canManage }: { hotel: PartnerHotel; canManage: boolean }) {
  const toast = useToast();
  const [items, setItems] = useState<HotelMediaItem[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [caption, setCaption] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const refresh = useCallback(async () => {
    try {
      setItems((await hotelMediaApi.list(hotel.id)).data ?? []);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Fotograflar yuklenemedi');
    }
  }, [hotel.id]);

  useEffect(() => {
    if (!canManage) return;
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return refresh(); });
    return () => { cancelled = true; };
  }, [canManage, refresh]);

  const run = async (fn: () => Promise<unknown>, done?: string) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await fn();
      if (done) toast.success(done);
      await refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Islem basarisiz');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const onPick = (file: File | undefined) => {
    if (!file) return;
    const problem = validatePhotoFile(file, items.length);
    if (problem) {
      toast.error(problem);
      if (fileInput.current) fileInput.current.value = '';
      return;
    }
    void run(async () => {
      await hotelMediaApi.upload(hotel.id, file, caption);
      setCaption('');
      if (fileInput.current) fileInput.current.value = '';
    }, 'Fotograf yuklendi, incelemeye alindi');
  };

  const move = (id: string, delta: number) => {
    const ids = moveId(items.map(i => i.id), id, delta);
    void run(() => hotelMediaApi.reorder(hotel.id, ids));
  };

  if (!canManage) return null;

  return (
    <Card className="p-4 space-y-3">
      <div>
        <h3 className="font-semibold text-on-surface">Fotograflar</h3>
        <p className="text-xs text-on-surface-variant">
          JPEG, PNG veya WebP, en fazla 5 MB, en az 800x600. En fazla {HOTEL_PHOTO_MAX_COUNT} fotograf. Yayinlanmadan once yonetici onayi gerekir.
        </p>
      </div>
      {loadError && <p className="text-sm text-error">{loadError}</p>}
      <div className="flex flex-wrap items-end gap-2">
        <Field label="Aciklama (istege bagli)">
          <input className={inputClass} maxLength={200} value={caption} onChange={e => setCaption(e.target.value)} />
        </Field>
        <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy}
          onChange={e => onPick(e.target.files?.[0])} className="text-sm" />
      </div>
      {items.length === 0 && !loadError && <p className="text-xs text-outline">Henuz fotograf yok.</p>}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
        {items.map((it, idx) => (
          <div key={it.id} className="space-y-2 rounded-xl bg-surface-container-lowest p-2">
            <Thumb hotelId={hotel.id} item={it} />
            <div className="flex items-center justify-between gap-2">
              <StatusPill tone={MEDIA_STATUS_TONE[it.reviewStatus]}>{MEDIA_STATUS_LABEL[it.reviewStatus]}</StatusPill>
              <span className="text-xs text-outline">{it.width}x{it.height}</span>
            </div>
            {it.reviewStatus === 'REJECTED' && it.reviewReason && (
              <p className="text-xs text-error">Gerekce: {it.reviewReason}</p>
            )}
            <input className={inputClass} maxLength={200} defaultValue={it.caption ?? ''} placeholder="Aciklama"
              onBlur={e => {
                if (e.target.value.trim() !== (it.caption ?? '')) {
                  void run(() => hotelMediaApi.caption(hotel.id, it.id, e.target.value), 'Aciklama kaydedildi');
                }
              }} />
            <div className="flex gap-1">
              <Button variant="ghost" disabled={busy || idx === 0} onClick={() => move(it.id, -1)}>Yukari</Button>
              <Button variant="ghost" disabled={busy || idx === items.length - 1} onClick={() => move(it.id, 1)}>Asagi</Button>
              <Button variant="danger" disabled={busy}
                onClick={() => void run(() => hotelMediaApi.remove(hotel.id, it.id), 'Fotograf silindi')}>Sil</Button>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}
