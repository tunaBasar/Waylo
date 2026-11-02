'use client';

import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import {
  campaignActive, discountLabel, FUNDED_BY_LABEL, pricingApi, VERTICAL_LABEL,
  type Campaign, type DiscountType, type PricingVertical, type PromoCode, type TaxRate,
} from '@/lib/pricing';

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });
const inputCls = 'w-full rounded-lg bg-surface-container px-3 py-2 text-sm';

/** datetime-local gives "YYYY-MM-DDTHH:mm", the API wants seconds too. */
const withSeconds = (v: string) => (v.length === 16 ? `${v}:00` : v);

function errorText(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message || fallback : fallback;
}

function CodesPanel({ campaign }: { campaign: Campaign }) {
  const [codes, setCodes] = useState<PromoCode[] | null>(null);
  const [code, setCode] = useState('');
  const [maxUses, setMaxUses] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setCodes((await pricingApi.promoCodes(campaign.id)).data);
    } catch (cause) {
      setError(errorText(cause, 'Kodlar yuklenemedi'));
    }
  }, [campaign.id]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void load(); }, [load]);

  async function add(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      await pricingApi.createPromoCode(campaign.id, code.trim().toUpperCase(), campaign.startAt, campaign.endAt,
        maxUses ? Number(maxUses) : undefined);
      setCode('');
      setMaxUses('');
      await load();
    } catch (cause) {
      setError(errorText(cause, 'Kod olusturulamadi'));
    }
  }

  return (
    <div className="mt-3 space-y-2 rounded-lg bg-surface-container p-3">
      {error && <p className="text-xs text-error">{error}</p>}
      {codes === null ? <p className="text-xs text-on-surface-variant">Yukleniyor...</p> : codes.length === 0
        ? <p className="text-xs text-on-surface-variant">Bu kampanyada kod yok.</p>
        : (
          <ul className="text-xs text-on-surface">
            {codes.map(c => (
              <li key={c.id}>{c.code} · kullanim {c.usesCount}{c.maxUsesTotal != null ? ` / ${c.maxUsesTotal}` : ''}</li>
            ))}
          </ul>
        )}
      <form onSubmit={add} className="flex flex-wrap gap-2">
        <input value={code} onChange={e => setCode(e.target.value)} placeholder="KOD" required maxLength={50} className={`${inputCls} max-w-[160px]`} />
        <input value={maxUses} onChange={e => setMaxUses(e.target.value)} placeholder="Toplam limit" type="number" min="1" className={`${inputCls} max-w-[140px]`} />
        <button className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-on-primary">Kod ekle</button>
      </form>
    </div>
  );
}

export default function CampaignsPage() {
  const [campaigns, setCampaigns] = useState<Campaign[] | null>(null);
  const [taxRates, setTaxRates] = useState<TaxRate[] | null>(null);
  const [error, setError] = useState('');
  const [open, setOpen] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [name, setName] = useState('');
  const [vertical, setVertical] = useState<PricingVertical>('ARAÇ');
  const [discountType, setDiscountType] = useState<DiscountType>('PERCENTAGE');
  const [discountValue, setDiscountValue] = useState('');
  const [startAt, setStartAt] = useState('');
  const [endAt, setEndAt] = useState('');
  const [maxGlobal, setMaxGlobal] = useState('');
  const [maxPerUser, setMaxPerUser] = useState('');
  const [minOrder, setMinOrder] = useState('');

  const load = useCallback(async () => {
    try {
      setCampaigns((await pricingApi.campaigns()).data);
    } catch (cause) {
      setError(errorText(cause, 'Kampanyalar yuklenemedi'));
    }
    try {
      setTaxRates((await pricingApi.taxRates()).data);
    } catch {
      setTaxRates(null);
    }
  }, []);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void load(); }, [load]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      // Partner funded campaigns need the partner's consent (PRICING-CONTRACT.md section 9): admin creates PLATFORM only.
      await pricingApi.createCampaign({
        name: name.trim(), vertical, discountType, discountValue: Number(discountValue), fundedBy: 'PLATFORM',
        startAt: withSeconds(startAt), endAt: withSeconds(endAt),
        ...(maxGlobal ? { maxUsesGlobal: Number(maxGlobal) } : {}),
        ...(maxPerUser ? { maxUsesPerUser: Number(maxPerUser) } : {}),
        ...(minOrder ? { minOrderAmount: Number(minOrder) } : {}),
      });
      setName(''); setDiscountValue(''); setMaxGlobal(''); setMaxPerUser(''); setMinOrder('');
      await load();
    } catch (cause) {
      setError(errorText(cause, 'Kampanya olusturulamadi'));
    } finally {
      setBusy(false);
    }
  }

  async function end(id: string) {
    setError('');
    try {
      await pricingApi.endCampaign(id);
      await load();
    } catch (cause) {
      setError(errorText(cause, 'Kampanya sonlandirilamadi'));
    }
  }

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Kampanyalar</h2>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {taxRates !== null && taxRates.length === 0 && (
        <p className="rounded-lg bg-tertiary-container px-3 py-2 text-sm text-on-tertiary-container">Vergi oranlari tanimli degil. Fiyatlar vergiler dahil gosterilir, fatura dokumu icin oran tanimlayin.</p>
      )}

      <form onSubmit={create} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-3">
        <div className="md:col-span-3 text-sm font-semibold text-on-surface">Yeni kampanya (platform fonlu)</div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Ad</label>
          <input value={name} onChange={e => setName(e.target.value)} required className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Dikey</label>
          <select value={vertical} onChange={e => setVertical(e.target.value as PricingVertical)} className={inputCls}>
            {(Object.keys(VERTICAL_LABEL) as PricingVertical[]).map(v => <option key={v} value={v}>{VERTICAL_LABEL[v]}</option>)}
          </select></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Indirim turu</label>
          <select value={discountType} onChange={e => setDiscountType(e.target.value as DiscountType)} className={inputCls}>
            <option value="PERCENTAGE">Yuzde</option><option value="FIXED">Sabit tutar</option>
          </select></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Deger</label>
          <input value={discountValue} onChange={e => setDiscountValue(e.target.value)} type="number" step="0.01" min="0.01" required className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Baslangic</label>
          <input value={startAt} onChange={e => setStartAt(e.target.value)} type="datetime-local" required className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Bitis</label>
          <input value={endAt} onChange={e => setEndAt(e.target.value)} type="datetime-local" required className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Toplam kullanim limiti</label>
          <input value={maxGlobal} onChange={e => setMaxGlobal(e.target.value)} type="number" min="1" className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Kullanici basina limit</label>
          <input value={maxPerUser} onChange={e => setMaxPerUser(e.target.value)} type="number" min="1" className={inputCls} /></div>
        <div><label className="mb-1 block text-xs text-on-surface-variant">Minimum tutar</label>
          <input value={minOrder} onChange={e => setMinOrder(e.target.value)} type="number" step="0.01" min="0" className={inputCls} /></div>
        <div className="md:col-span-3">
          <button disabled={busy} className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-60">
            {busy ? 'Kaydediliyor...' : 'Kampanya olustur'}
          </button>
        </div>
      </form>

      {campaigns === null ? <p className="text-sm text-on-surface-variant">Yukleniyor...</p> : campaigns.length === 0
        ? <p className="text-sm text-on-surface-variant">Kampanya yok.</p>
        : (
          <div className="space-y-space-sm">
            {campaigns.map(c => (
              <div key={c.id} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-on-surface">{c.name}</p>
                    <p className="text-xs text-on-surface-variant">
                      {VERTICAL_LABEL[c.vertical] ?? c.vertical} · {discountLabel(c)} · {c.scopeOrgId ? 'Isletmeye ozel' : 'Tum platform'} · {FUNDED_BY_LABEL[c.fundedBy] ?? c.fundedBy} fonlu
                    </p>
                    <p className="text-xs text-on-surface-variant">{fmt(c.startAt)} - {fmt(c.endAt)}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`rounded-full px-2 py-1 text-xs font-medium ${campaignActive(c) ? 'bg-tertiary-container text-on-tertiary-container' : 'bg-surface-container text-on-surface-variant'}`}>
                      {campaignActive(c) ? 'Aktif' : 'Pasif'}
                    </span>
                    <button onClick={() => setOpen(open === c.id ? null : c.id)} className="rounded-lg bg-secondary-container px-3 py-1 text-xs font-medium text-on-secondary-container">Kodlar</button>
                    {campaignActive(c) && <button onClick={() => void end(c.id)} className="rounded-lg bg-error-container px-3 py-1 text-xs font-medium text-on-error-container">Sonlandir</button>}
                  </div>
                </div>
                {open === c.id && <CodesPanel campaign={c} />}
              </div>
            ))}
          </div>
        )}
    </div>
  );
}
