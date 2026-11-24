'use client';

/**
 * REFERENCE PAGE for the partner panel. Other /partner pages follow this exact pattern:
 *  - read the selected organization/hotel from usePartner(), never fetch it again
 *  - call the backend only through partnerApi (src/lib/partner.ts)
 *  - compose UI from components/partner/ui.tsx
 *  - show loading, empty and error states; report results with useToast()
 *  - keep form state local, validate before submit, disable the button while saving
 *  - guard submits with a ref (inFlight): `saving` state only applies after a re-render, so a
 *    fast double click would otherwise send the request twice and create duplicates
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import {
  partnerApi, ORGANIZATION_STATUS_LABEL, ROLE_LABEL, PUBLICATION_STATUS_LABEL, PUBLICATION_STATUS_TONE, REVISION_STATUS_LABEL,
  canManageHotel, submitBlockReason, contentDiff, isErrorCode,
  type HotelRequest, type OrganizationStatus, type BusinessType, type LegalType, type PartnerHotel,
  type PublicationRequestSummary, type HotelContentView, type HotelContent,
} from '@/lib/partner';
import { HotelPhotosPanel } from './HotelPhotosPanel';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const STATUS_TONE: Record<OrganizationStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  ACTIVE: 'success',
  PENDING: 'warning',
  SUSPENDED: 'error',
  ARCHIVED: 'neutral',
};

function NewOrganizationForm({ onDone }: { onDone: () => void }) {
  const toast = useToast();
  const { reload } = usePartner();
  const [name, setName] = useState('');
  const [businessType, setBusinessType] = useState<BusinessType>('HOTEL');
  const [legalType, setLegalType] = useState<LegalType>('COMPANY');
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim() || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      const res = await partnerApi.createOrganization({ name: name.trim(), businessType, legalType: businessType === 'BUS' ? 'COMPANY' : legalType });
      toast.success('Isletme olusturuldu. Platform onayindan sonra oteller yayina alinir.');
      await reload({ organizationId: res.data.id });
      onDone();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Isletme olusturulamadi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
        <div className="flex-1 min-w-[220px]">
          <Field label="Isletme adi">
            <input className={inputClass} value={name} maxLength={255} autoFocus
              onChange={e => setName(e.target.value)} placeholder="Ornek: Ege Otelcilik A.S." />
          </Field>
        </div>
        <Field label="Isletme tipi">
          <select className={inputClass} value={businessType}
            onChange={e => setBusinessType(e.target.value as BusinessType)}>
            <option value="HOTEL">Otel</option>
            <option value="CAR_RENTAL">Araclar</option>
            <option value="BUS">Otobus firmasi</option>
          </select>
        </Field>
        {businessType !== 'BUS' && (
          <Field label="Hukuki sekil">
            <select className={inputClass} value={legalType}
              onChange={e => setLegalType(e.target.value as LegalType)}>
              <option value="COMPANY">Sirket</option>
              <option value="INDIVIDUAL">Mukerrat</option>
            </select>
          </Field>
        )}
        <Button type="submit" icon="check" disabled={saving || !name.trim()}>{saving ? 'Kaydediliyor' : 'Olustur'}</Button>
        <Button type="button" variant="ghost" onClick={onDone}>Vazgec</Button>
      </form>
    </Card>
  );
}

const EMPTY_HOTEL = { name: '', city: '', country: '', address: '', starRating: 3, description: '' };

function NewHotelForm({ organizationId, onDone }: { organizationId: string; onDone: () => void }) {
  const toast = useToast();
  const { reload } = usePartner();
  const [form, setForm] = useState(EMPTY_HOTEL);
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);
  const set = (k: keyof typeof EMPTY_HOTEL) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm(f => ({ ...f, [k]: k === 'starRating' ? Number(e.target.value) : e.target.value }));
  const valid = form.name.trim() && form.city.trim() && form.country.trim() && form.address.trim();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    const body: HotelRequest = {
      name: form.name.trim(),
      city: form.city.trim(),
      country: form.country.trim(),
      address: form.address.trim(),
      starRating: form.starRating,
      description: form.description.trim() || null,
      organizationId,
    };
    try {
      const res = await partnerApi.createHotel(body);
      toast.success('Otel taslak olarak kaydedildi. Yayin icin gonderip platform onayi alana kadar musterilere gorunmez.');
      await reload({ organizationId, hotelId: res.data.id });
      onDone();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Otel eklenemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Otel adi"><input className={inputClass} value={form.name} onChange={set('name')} autoFocus /></Field>
        <Field label="Yildiz">
          <select className={inputClass} value={form.starRating} onChange={set('starRating')}>
            {[1, 2, 3, 4, 5].map(s => <option key={s} value={s}>{s} yildiz</option>)}
          </select>
        </Field>
        <Field label="Sehir"><input className={inputClass} value={form.city} onChange={set('city')} /></Field>
        <Field label="Ulke"><input className={inputClass} value={form.country} onChange={set('country')} /></Field>
        <div className="sm:col-span-2">
          <Field label="Adres"><input className={inputClass} value={form.address} onChange={set('address')} /></Field>
        </div>
        <div className="sm:col-span-2">
          <Field label="Aciklama" hint="Istege bagli">
            <textarea className={inputClass} rows={3} value={form.description} onChange={set('description')} />
          </Field>
        </div>
        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !valid}>{saving ? 'Kaydediliyor' : 'Oteli ekle'}</Button>
          <Button type="button" variant="ghost" onClick={onDone}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

const MISSING_LABEL: Record<string, string> = {
  ROOM_TYPE: 'Aktif bir oda tipi ekleyin',
  RATE_PLAN: 'Aktif bir fiyat plani ekleyin',
  INVENTORY: 'Kontenjan ve fiyat girin',
};

function toContent(h: PartnerHotel): HotelContent {
  return {
    name: h.name, city: h.city, country: h.country, address: h.address, starRating: h.starRating,
    description: h.description, latitude: h.latitude, longitude: h.longitude,
  };
}

/** Edit form: DRAFT/REJECTED edit the hotel directly, PUBLISHED/SUSPENDED edit a content revision (contract 3.3, 4.2). */
function ContentForm({ hotel, view, onSaved, onCancel }: {
  hotel: PartnerHotel; view: HotelContentView | null; onSaved: () => Promise<void>; onCancel: () => void;
}) {
  const toast = useToast();
  const revisionMode = hotel.publicationStatus === 'PUBLISHED' || hotel.publicationStatus === 'SUSPENDED';
  const base: HotelContent = revisionMode && view ? (view.revision?.content ?? view.live) : toContent(hotel);
  const [form, setForm] = useState({
    name: base.name, city: base.city, country: base.country, address: base.address,
    starRating: base.starRating, description: base.description ?? '',
  });
  const [saving, setSaving] = useState(false);
  const [stale, setStale] = useState(false);
  const inFlight = useRef(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm(f => ({ ...f, [k]: k === 'starRating' ? Number(e.target.value) : e.target.value }));
  const valid = form.name.trim() && form.city.trim() && form.country.trim() && form.address.trim();

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    const next: HotelContent = {
      ...base, name: form.name.trim(), city: form.city.trim(), country: form.country.trim(),
      address: form.address.trim(), starRating: form.starRating, description: form.description.trim() || null,
    };
    try {
      if (revisionMode && view) {
        const patch = contentDiff(base, next);
        if (Object.keys(patch).length === 0) { toast.error('Degisiklik yok'); return; }
        await partnerApi.patchContent(hotel.id, { expectedVersion: view.revision ? view.revision.version : view.contentVersion, content: patch });
        toast.success('Degisiklik taslak olarak kaydedildi. Incelemeye gondermeden yayina alinmaz.');
      } else if (hotel.organizationId) {
        await partnerApi.updateHotel(hotel.id, {
          name: next.name, city: next.city, country: next.country, address: next.address, starRating: next.starRating,
          description: next.description, latitude: next.latitude, longitude: next.longitude, organizationId: hotel.organizationId,
        });
        toast.success('Otel bilgileri kaydedildi');
      }
      await onSaved();
      onCancel();
    } catch (err) {
      if (isErrorCode(err, 409, 'VERSION_CONFLICT')) {
        setStale(true);
        toast.error('Icerik baska biri tarafindan degistirildi. Sayfayi yenileyip tekrar deneyin.');
      } else if (isErrorCode(err, 409, 'CONTENT_REQUIRES_REVISION')) {
        toast.error('Yayindaki otelin icerigi degisiklik talebi ile guncellenir. Guncel hali yukleniyor.');
        await onSaved();
        onCancel();
      } else {
        toast.error(err instanceof Error ? err.message : 'Kaydedilemedi');
      }
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <form onSubmit={save} className="grid gap-3 sm:grid-cols-2 border-t border-outline-variant/40 pt-3">
      {revisionMode && (
        <p className="sm:col-span-2 text-xs text-on-surface-variant">Degisiklikler incelemeden sonra yayinlanir. Su an yayindaki icerik degismez.</p>
      )}
      <Field label="Otel adi"><input className={inputClass} value={form.name} onChange={set('name')} /></Field>
      <Field label="Yildiz (beyan edilen)">
        <select className={inputClass} value={form.starRating} onChange={set('starRating')}>
          {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n} yildiz</option>)}
        </select>
      </Field>
      <Field label="Sehir"><input className={inputClass} value={form.city} onChange={set('city')} /></Field>
      <Field label="Ulke"><input className={inputClass} value={form.country} onChange={set('country')} /></Field>
      <div className="sm:col-span-2"><Field label="Adres"><input className={inputClass} value={form.address} onChange={set('address')} /></Field></div>
      <div className="sm:col-span-2"><Field label="Aciklama"><textarea className={inputClass} rows={3} value={form.description} onChange={set('description')} /></Field></div>
      {stale && <p className="sm:col-span-2 text-xs text-error">Kayit eskidi. Formu kapatip yeniden acin.</p>}
      <div className="sm:col-span-2 flex gap-2">
        <Button type="submit" icon="check" disabled={saving || !valid || stale}>{saving ? 'Kaydediliyor' : 'Kaydet'}</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Vazgec</Button>
      </div>
    </form>
  );
}

function PublicationPanel({ hotel, organizationStatus, canManage }: {
  hotel: PartnerHotel; organizationStatus: OrganizationStatus | undefined; canManage: boolean;
}) {
  const toast = useToast();
  const { reload } = usePartner();
  const status = hotel.publicationStatus;
  const [requests, setRequests] = useState<PublicationRequestSummary[]>([]);
  const [view, setView] = useState<HotelContentView | null>(null);
  const [note, setNote] = useState('');
  const [missing, setMissing] = useState<string[]>([]);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const inFlight = useRef(false);
  const revisionMode = status === 'PUBLISHED' || status === 'SUSPENDED';

  const refresh = useCallback(async () => {
    try {
      const reqs = await partnerApi.publicationRequests(hotel.id);
      setRequests(reqs.data ?? []);
      if (revisionMode && canManage) setView((await partnerApi.getContent(hotel.id)).data);
      else setView(null);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Yayin bilgileri yuklenemedi');
    }
  }, [hotel.id, revisionMode, canManage]);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return refresh(); });
    return () => { cancelled = true; };
  }, [refresh, status]);

  const reloadAll = useCallback(async () => {
    await reload({ organizationId: hotel.organizationId ?? undefined, hotelId: hotel.id });
    await refresh();
  }, [reload, hotel.organizationId, hotel.id, refresh]);

  async function run(action: () => Promise<unknown>, done: string) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setMissing([]);
    try {
      await action();
      toast.success(done);
      setNote('');
      await reloadAll();
    } catch (err) {
      const text = err instanceof Error ? err.message : 'Islem basarisiz';
      if (isErrorCode(err, 409, 'HOTEL_INCOMPLETE')) setMissing(Object.keys(MISSING_LABEL).filter(k => text.includes(k)));
      if (isErrorCode(err, 409, 'VERSION_CONFLICT')) toast.error('Icerik baska biri tarafindan degistirildi. Yayin bilgileri yenilendi, tekrar deneyin.');
      else toast.error(text);
      await refresh();
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  const blocked = submitBlockReason(organizationStatus, canManage);
  const latest = requests[0];
  const openRequest = requests.find(r => r.status === 'SUBMITTED');
  const rejection = status === 'REJECTED' ? requests.find(r => r.decision?.decision === 'REJECT') : undefined;
  const suspension = status === 'SUSPENDED' ? requests.find(r => r.decision?.decision === 'SUSPEND') : undefined;
  const revision = view?.revision ?? null;

  return (
    <Card className="p-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="font-semibold text-on-surface">Yayin durumu: {hotel.name}</h3>
        <StatusPill tone={PUBLICATION_STATUS_TONE[status]}>{PUBLICATION_STATUS_LABEL[status]}</StatusPill>
        {!hotel.active && <StatusPill tone="neutral">Satis duraklatildi</StatusPill>}
      </div>
      {loadError && <p className="text-xs text-error">{loadError}</p>}

      {rejection?.decision?.reason && (
        <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">Red gerekcesi: {rejection.decision.reason}</p>
      )}
      {suspension?.decision?.reason && (
        <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">Askiya alma gerekcesi: {suspension.decision.reason}</p>
      )}
      {status === 'APPROVED' && <p className="text-sm text-on-surface-variant">Otel onaylandi, platformun yayinlamasi bekleniyor.</p>}
      {status === 'ARCHIVED' && <p className="text-sm text-on-surface-variant">Bu otel arsivlendi ve duzenlenemez.</p>}

      {(status === 'DRAFT' || status === 'REJECTED') && (
        <div className="space-y-2">
          <Field label="Not" hint="Istege bagli, incelemeyi yapan yoneticiye iletilir">
            <input className={inputClass} value={note} maxLength={1000} onChange={e => setNote(e.target.value)} disabled={!!blocked} />
          </Field>
          <div className="flex flex-wrap items-center gap-2">
            <Button icon="send" disabled={busy || !!blocked}
              onClick={() => void run(() => partnerApi.submitPublication(hotel.id, note.trim() ? { note: note.trim() } : {}), 'Otel incelemeye gonderildi')}>
              Yayin icin gonder
            </Button>
            {canManage && !editing && <Button variant="ghost" icon="edit" onClick={() => setEditing(true)}>Duzenle</Button>}
          </div>
          {blocked && <p className="text-xs text-on-surface-variant">{blocked}</p>}
          {missing.length > 0 && (
            <ul className="list-disc pl-5 text-sm text-error">
              {missing.map(k => <li key={k}>{MISSING_LABEL[k]}</li>)}
            </ul>
          )}
        </div>
      )}

      {status === 'SUBMITTED' && (
        <div className="space-y-2">
          <p className="text-sm text-on-surface-variant">Otel platform incelemesinde. Bu sirada duzenleme yapilamaz.</p>
          {canManage && openRequest && (
            <Button variant="secondary" icon="undo" disabled={busy}
              onClick={() => void run(() => partnerApi.withdrawPublication(hotel.id, openRequest.id), 'Talep geri cekildi')}>Geri cek</Button>
          )}
        </div>
      )}

      {revisionMode && canManage && (
        <div className="space-y-2">
          <p className="text-xs text-on-surface-variant">Degisiklikler incelemeden sonra yayinlanir.</p>
          {revision && (
            <p className="text-sm">Degisiklik taslagi #{revision.revisionNo}: <StatusPill tone={revision.status === 'SUBMITTED' ? 'warning' : 'neutral'}>{REVISION_STATUS_LABEL[revision.status]}</StatusPill></p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            {!editing && revision?.status !== 'SUBMITTED' && (
              <Button variant="secondary" icon="edit" disabled={!view} onClick={() => setEditing(true)}>Icerigi duzenle</Button>
            )}
            {revision?.status === 'DRAFT' && (
              <Button icon="send" disabled={busy || !!blocked}
                onClick={() => void run(() => partnerApi.submitPublication(hotel.id, { note: note.trim() || undefined, revisionId: revision.id, expectedVersion: revision.version }), 'Degisiklik incelemeye gonderildi')}>
                Incelemeye gonder
              </Button>
            )}
            {openRequest && revision?.status === 'SUBMITTED' && (
              <Button variant="secondary" icon="undo" disabled={busy}
                onClick={() => void run(() => partnerApi.withdrawPublication(hotel.id, openRequest.id), 'Talep geri cekildi')}>Geri cek</Button>
            )}
          </div>
        </div>
      )}

      {editing && canManage && (revisionMode ? !!view : true) && (
        <ContentForm key={`${hotel.id}-${view?.revision?.version ?? hotel.contentVersion}`} hotel={hotel} view={view}
          onSaved={reloadAll} onCancel={() => setEditing(false)} />
      )}

      {requests.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-on-surface-variant mb-1">Talep gecmisi</p>
          <ul className="space-y-1 text-sm">
            {requests.map(r => (
              <li key={r.id} className="text-on-surface-variant">
                {r.kind === 'INITIAL' ? 'Ilk yayin' : 'Icerik degisikligi'} &middot; {new Date(r.submittedAt).toLocaleString('tr-TR')} &middot; {r.status}
                {r.decision?.reason && <span>: {r.decision.reason}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {latest === undefined && !loadError && <p className="text-xs text-outline">Henuz yayin talebi yok.</p>}
    </Card>
  );
}

export default function PartnerOrganizationsPage() {
  const { loading, error, memberships, membership, organizationId, hotels, hotelId, hotel, selectHotel, isBusOrg } = usePartner();
  const [showOrgForm, setShowOrgForm] = useState(false);
  const [showHotelForm, setShowHotelForm] = useState(false);

  const isOwner = membership?.roles.some(r => r.role === 'ORGANIZATION_OWNER') ?? false;

  if (loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <section className="space-y-space-sm">
        <PageHeader
          title="Isletmeler"
          subtitle="Uyesi oldugunuz isletmeler ve rolleriniz"
          actions={!showOrgForm && <Button icon="add" onClick={() => setShowOrgForm(true)}>Yeni isletme</Button>}
        />
        {showOrgForm && <NewOrganizationForm onDone={() => setShowOrgForm(false)} />}
        {memberships.length === 0 ? (
          !showOrgForm && (
            <EmptyState icon="storefront" title="Henuz bir isletmeniz yok"
              description="Otellerinizi yonetmek icin once isletmenizi olusturun. Isletme platform tarafindan onaylandiktan sonra otelleriniz musterilere gorunur."
              action={<Button icon="add" onClick={() => setShowOrgForm(true)}>Isletme olustur</Button>} />
          )
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {memberships.map(m => (
              <Card key={m.membershipId} className={m.organizationId === organizationId ? 'p-4 ring-2 ring-primary' : 'p-4'}>
                <div className="flex items-start justify-between gap-2">
                  <p className="font-semibold text-on-surface truncate">{m.organizationName}</p>
                  <StatusPill tone={STATUS_TONE[m.organizationStatus]}>{ORGANIZATION_STATUS_LABEL[m.organizationStatus]}</StatusPill>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {m.roles.map((r, i) => (
                    <StatusPill key={i} tone="neutral">
                      {ROLE_LABEL[r.role]}{r.scopeType === 'HOTEL' ? ' (otel)' : ''}
                    </StatusPill>
                  ))}
                </div>
                {m.organizationStatus === 'PENDING' && (
                  <p className="mt-3 text-xs text-on-surface-variant">
                    Onay bekleniyor. Otel ve fiyat girebilirsiniz ancak onaydan once musterilere gosterilmez.
                  </p>
                )}
              </Card>
            ))}
          </div>
        )}
      </section>

      {organizationId && !isBusOrg && (
        <section className="space-y-space-sm">
          <PageHeader
            title="Oteller"
            subtitle={membership ? `${membership.organizationName} isletmesine ait oteller` : undefined}
            actions={isOwner && !showHotelForm && <Button icon="add" variant="secondary" onClick={() => setShowHotelForm(true)}>Otel ekle</Button>}
          />
          {showHotelForm && <NewHotelForm organizationId={organizationId} onDone={() => setShowHotelForm(false)} />}
          {hotels.length === 0 ? (
            !showHotelForm && (
              <EmptyState icon="hotel" title="Bu isletmede otel yok"
                description={isOwner ? 'Ilk otelinizi ekleyerek baslayin.' : 'Otel eklemek icin isletme sahibi yetkisi gerekir.'} />
            )
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {hotels.map(h => (
                <button key={h.id} onClick={() => selectHotel(h.id)} className="text-left">
                  <Card className={h.id === hotelId ? 'p-4 ring-2 ring-primary' : 'p-4 hover:bg-surface-container-low transition-colors'}>
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-semibold text-on-surface truncate">{h.name}</p>
                      <StatusPill tone={PUBLICATION_STATUS_TONE[h.publicationStatus]}>{PUBLICATION_STATUS_LABEL[h.publicationStatus]}</StatusPill>
                    </div>
                    {!h.active && <div className="mt-1"><StatusPill tone="neutral">Satis duraklatildi</StatusPill></div>}
                    <p className="text-sm text-on-surface-variant mt-1">{h.city}, {h.country}</p>
                    <p className="text-xs text-outline mt-2">{'★'.repeat(h.starRating)} &middot; {h.address}</p>
                  </Card>
                </button>
              ))}
            </div>
          )}
          {hotel && (
            <PublicationPanel key={hotel.id} hotel={hotel} organizationStatus={membership?.organizationStatus}
              canManage={membership ? canManageHotel(membership.roles, hotel.id) : false} />
          )}
          {hotel && (
            <HotelPhotosPanel key={`photos-${hotel.id}`} hotel={hotel}
              canManage={membership ? canManageHotel(membership.roles, hotel.id) : false} />
          )}
        </section>
      )}
    </div>
  );
}
