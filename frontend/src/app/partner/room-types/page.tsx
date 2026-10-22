'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import { isVersionConflict, partnerApi, partnerErrorMessage, type RoomType } from '@/lib/partner';
import { Button, Card, EmptyState, Field, LoadingRows, NeedsHotel, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

function RoomTypeForm({
  roomType,
  hotelId,
  onDone,
}: {
  roomType?: RoomType | null;
  hotelId: string;
  onDone: (saved: boolean) => void;
}) {
  const toast = useToast();
  const isEdit = !!roomType;

  const [code, setCode] = useState(roomType?.code ?? '');
  const [name, setName] = useState(roomType?.name ?? '');
  // Number fields are kept as strings so the user can clear and retype them.
  const [maxAdults, setMaxAdults] = useState(String(roomType?.maxAdults ?? 2));
  const [maxChildren, setMaxChildren] = useState(String(roomType?.maxChildren ?? 0));
  const [maxOccupancy, setMaxOccupancy] = useState(String(roomType?.maxOccupancy ?? 2));
  const [sizeSqm, setSizeSqm] = useState(roomType?.sizeSqm != null ? String(roomType.sizeSqm) : '');
  const [description, setDescription] = useState(roomType?.description ?? '');
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  function upper(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '');
    setCode(v);
  }

  const digits = (set: (v: string) => void) => (e: React.ChangeEvent<HTMLInputElement>) =>
    set(e.target.value.replace(/\D/g, ''));

  const inRange = (raw: string, min: number, max: number) =>
    raw !== '' && Number(raw) >= min && Number(raw) <= max;

  const adults = Number(maxAdults);
  const children = Number(maxChildren);
  const occupancy = Number(maxOccupancy);

  const codeValid = /^[A-Z0-9_]{2,30}$/.test(code);
  const nameValid = name.trim().length > 0;
  const adultsValid = inRange(maxAdults, 1, 20);
  const childrenValid = inRange(maxChildren, 0, 10);
  const occupancyRangeValid = inRange(maxOccupancy, 1, 30);
  const sizeValid = sizeSqm === '' ? !(isEdit && roomType?.sizeSqm != null) : inRange(sizeSqm, 1, 2000);
  const occValid = occupancy >= adults && occupancy <= adults + children;

  const valid = codeValid && nameValid && adultsValid && childrenValid && occupancyRangeValid && sizeValid && occValid;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      if (isEdit && roomType) {
        await partnerApi.updateRoomType(hotelId, roomType.id, {
          name: name.trim(),
          // PATCH ignores null; an empty string clears the description.
          description: description.trim(),
          maxAdults: adults,
          maxChildren: children,
          maxOccupancy: occupancy,
          // sizeSqm cannot be cleared via PATCH (backend treats null as unchanged); omit when empty.
          ...(sizeSqm !== '' ? { sizeSqm: Number(sizeSqm) } : {}),
          expectedVersion: roomType.version,
        });
        toast.success('Oda tipi guncellendi');
      } else {
        await partnerApi.createRoomType(hotelId, {
          code,
          name: name.trim(),
          description: description.trim() || null,
          maxAdults: adults,
          maxChildren: children,
          maxOccupancy: occupancy,
          sizeSqm: sizeSqm === '' ? null : Number(sizeSqm),
        });
        toast.success('Oda tipi olusturuldu');
      }
      onDone(true);
    } catch (err) {
      toast.error(partnerErrorMessage(err, isEdit ? 'Guncellenemedi' : 'Olusturulamadi'));
      // On a version conflict the form holds stale data: close it and show the fresh list.
      if (isEdit && isVersionConflict(err)) onDone(true);
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Kod"
          error={codeValid ? undefined : code.length > 0 ? 'Gecerli kod: A-Z, 0-9, _ (2-30 karakter)' : undefined}
        >
          <input
            className={inputClass}
            value={code}
            onChange={upper}
            maxLength={30}
            disabled={isEdit}
            autoFocus={!isEdit}
            readOnly={isEdit}
          />
        </Field>

        <Field
          label="Oda adi"
          error={nameValid ? undefined : 'Oda adi gerekli'}
        >
          <input
            className={inputClass}
            value={name}
            onChange={e => setName(e.target.value)}
            maxLength={120}
            autoFocus={isEdit}
          />
        </Field>

        <Field label="Yetiskin" error={adultsValid ? undefined : '1-20 arasi'}>
          <input className={inputClass} inputMode="numeric" value={maxAdults} onChange={digits(setMaxAdults)} />
        </Field>

        <Field label="Cocuk" error={childrenValid ? undefined : '0-10 arasi'}>
          <input className={inputClass} inputMode="numeric" value={maxChildren} onChange={digits(setMaxChildren)} />
        </Field>

        <Field
          label="Maksimum kapasite"
          error={!occupancyRangeValid ? '1-30 arasi'
            : occValid ? undefined : 'Kapasite, yetiskin sayisi ile yetiskin+cocuk toplami arasinda olmali'}
        >
          <input className={inputClass} inputMode="numeric" value={maxOccupancy} onChange={digits(setMaxOccupancy)} />
        </Field>

        <Field label="Boyut (m2)" hint={isEdit && roomType?.sizeSqm != null ? 'Istege bagli, kaydedilmis deger silinemez' : 'Istege bagli'}
          error={sizeValid ? undefined : sizeSqm === '' ? 'Kaydedilmis boyut bos birakilamaz' : '1-2000 arasi'}>
          <input className={inputClass} inputMode="numeric" value={sizeSqm} onChange={digits(setSizeSqm)} placeholder="Ornek: 28" />
        </Field>

        <div className="sm:col-span-2">
          <Field label="Aciklama" hint="Istege bagli">
            <textarea
              className={inputClass}
              rows={3}
              value={description}
              onChange={e => setDescription(e.target.value)}
              maxLength={2000}
            />
          </Field>
        </div>

        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !valid}>
            {saving ? 'Kaydediliyor' : isEdit ? 'Guncelle' : 'Olustur'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

function StatusToggle({ roomType, hotelId, onUpdated }: {
  roomType: RoomType;
  hotelId: string;
  onUpdated: () => void;
}) {
  const toast = useToast();
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  async function toggle() {
    const nextStatus = roomType.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (inFlight.current || saving) return;
    inFlight.current = true;
    setSaving(true);
    try {
      await partnerApi.updateRoomType(hotelId, roomType.id, {
        status: nextStatus,
        expectedVersion: roomType.version,
      });
      toast.success(nextStatus === 'ACTIVE' ? 'Oda tipi aktif edildi' : 'Oda tipi pasif yapildi');
      onUpdated();
    } catch (err) {
      toast.error(partnerErrorMessage(err, 'Durum guncellenemedi'));
      onUpdated();
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Button
      variant="ghost"
      onClick={toggle}
      disabled={saving}
    >
      {roomType.status === 'ACTIVE' ? 'Pasif yap' : 'Aktif yap'}
    </Button>
  );
}

export default function RoomTypesPage() {
  const { hotelId, loading: workspaceLoading } = usePartner();
  const [items, setItems] = useState<RoomType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<RoomType | null>(null);

  const load = useCallback(async (id: string) => {
    try {
      const res = await partnerApi.roomTypes(id, true);
      setItems(res.data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!hotelId) return;
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(hotelId); });
    return () => { cancelled = true; };
  }, [hotelId, load]);

  if (workspaceLoading) return <LoadingRows />;
  if (!hotelId) return <NeedsHotel />;
  if (loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Oda tipleri"
        subtitle="Secili otelin satilan oda tipleri"
        actions={!showForm && !editing && (
          <Button icon="add" onClick={() => setShowForm(true)}>Yeni oda tipi</Button>
        )}
      />

      {showForm && (
        <RoomTypeForm
          hotelId={hotelId}
          onDone={saved => {
            setShowForm(false);
            if (saved) void load(hotelId);
          }}
        />
      )}

      {editing && (
        <RoomTypeForm
          key={editing.id}
          roomType={editing}
          hotelId={hotelId}
          onDone={saved => {
            setEditing(null);
            if (saved) void load(hotelId);
          }}
        />
      )}

      {items.length === 0 ? (
        !showForm && !editing && (
          <EmptyState
            icon="bed"
            title="Bu otelde oda tipi yok"
            description="Satis yapabilmek icin once oda tiplerini tanimlayin."
            action={<Button icon="add" onClick={() => setShowForm(true)}>Oda tipi ekle</Button>}
          />
        )
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {items.map(rt => {
            const tone = rt.status === 'ACTIVE' ? ('success' as const) : ('neutral' as const);
            const label = rt.status === 'ACTIVE' ? 'Aktif' : 'Pasif';
            const capLine = `${rt.maxAdults} yetiskin, ${rt.maxChildren} cocuk, en fazla ${rt.maxOccupancy} kisi`;

            return (
              <Card key={rt.id} className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-on-surface truncate">{rt.name}</p>
                    <p className="text-xs text-outline mt-0.5">{rt.code}</p>
                  </div>
                  <StatusPill tone={tone}>{label}</StatusPill>
                </div>

                <div className="mt-3 space-y-1 text-sm text-on-surface-variant">
                  <p>{capLine}</p>
                  {rt.sizeSqm != null && <p>{rt.sizeSqm} m2</p>}
                  {rt.description && <p className="mt-1 text-xs text-outline line-clamp-2">{rt.description}</p>}
                </div>

                <div className="mt-3 flex gap-2">
                  <Button variant="ghost" onClick={() => { setShowForm(false); setEditing(rt); }}>Duzenle</Button>
                  <StatusToggle roomType={rt} hotelId={hotelId} onUpdated={() => void load(hotelId)} />
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
