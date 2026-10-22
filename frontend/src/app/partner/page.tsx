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
import { useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import { partnerApi, ORGANIZATION_STATUS_LABEL, ROLE_LABEL, type HotelRequest, type OrganizationStatus } from '@/lib/partner';
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
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim() || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      await partnerApi.createOrganization(name.trim());
      toast.success('Isletme olusturuldu. Platform onayindan sonra oteller yayina alinir.');
      await reload();
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
        <Button type="submit" icon="check" disabled={saving || !name.trim()}>{saving ? 'Kaydediliyor' : 'Olustur'}</Button>
        <Button type="button" variant="ghost" onClick={onDone}>Vazgec</Button>
      </form>
    </Card>
  );
}

const EMPTY_HOTEL = { name: '', city: '', country: 'Turkiye', address: '', starRating: 3, description: '' };

function NewHotelForm({ organizationId, onDone }: { organizationId: string; onDone: () => void }) {
  const toast = useToast();
  const { reload, selectHotel } = usePartner();
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
      toast.success('Otel eklendi');
      await reload();
      if (res.data?.id) selectHotel(res.data.id);
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

export default function PartnerOrganizationsPage() {
  const { loading, error, memberships, membership, organizationId, hotels, hotelId, selectHotel } = usePartner();
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

      {organizationId && (
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
                      <StatusPill tone={h.active ? 'success' : 'neutral'}>{h.active ? 'Aktif' : 'Pasif'}</StatusPill>
                    </div>
                    <p className="text-sm text-on-surface-variant mt-1">{h.city}, {h.country}</p>
                    <p className="text-xs text-outline mt-2">{'★'.repeat(h.starRating)} &middot; {h.roomCount} oda</p>
                  </Card>
                </button>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
