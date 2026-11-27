'use client';

/**
 * "Otomatik takvim": rolling stock rule per room type and price rules per room type x rate plan.
 * Rules open future dates automatically every night. Days a partner typed by hand ("Elle") are never changed.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import { useSingleFlight } from '@/lib/use-single-flight';
import { AMOUNT_ERROR, formatTrAmountInput, parseTrAmount } from '@/lib/amount';
import { automationErrorMessage, deriveOccupancyPrices, summaryMessage } from '@/lib/automation';
import {
  partnerApi,
  sectionErrorMessage,
  type AutomationOverview,
  type InventoryRule,
  type IsoDate,
  type RatePlan,
  type RateRule,
  type RateRuleRequest,
  type RoomType,
} from '@/lib/partner';
import { Button, Card, EmptyState, Field, LoadingRows, NeedsHotel, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const HORIZONS = [90, 180, 365, 730];

function isoToday(): IsoDate {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function addDaysIso(iso: IsoDate, n: number): IsoDate {
  const [y, m, d] = iso.split('-').map(Number);
  const x = new Date(y, m - 1, d + n);
  return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
}

/** yyyy-MM-dd to dd.MM.yyyy */
function trDate(iso: IsoDate): string {
  const [y, m, d] = iso.split('-');
  return `${d}.${m}.${y}`;
}

const money = (n: number, currency: string) => `${formatTrAmountInput(n)} ${currency === 'TRY' ? 'TL' : currency}`;

interface RuleForm {
  id: string | null;
  version: number | null;
  roomTypeId: string;
  ratePlanId: string;
  name: string;
  season: boolean;
  validFrom: string;
  validTo: string;
  baseOccupancy: string;
  baseAmount: string;
  weekendBaseAmount: string;
  extraPersonAmount: string;
  reducedPersonAmount: string;
  active: boolean;
}

function emptyForm(roomType: RoomType, ratePlanId: string): RuleForm {
  return {
    id: null, version: null, roomTypeId: roomType.id, ratePlanId, name: '', season: false, validFrom: '', validTo: '',
    baseOccupancy: String(Math.min(2, roomType.maxOccupancy)), baseAmount: '', weekendBaseAmount: '',
    extraPersonAmount: '0', reducedPersonAmount: '0', active: true,
  };
}

function formFromRule(r: RateRule): RuleForm {
  return {
    id: r.id, version: r.version, roomTypeId: r.roomTypeId, ratePlanId: r.ratePlanId, name: r.name,
    season: r.validFrom != null, validFrom: r.validFrom ?? '', validTo: r.validTo ?? '',
    baseOccupancy: String(r.baseOccupancy), baseAmount: formatTrAmountInput(r.baseAmount),
    weekendBaseAmount: r.weekendBaseAmount != null ? formatTrAmountInput(r.weekendBaseAmount) : '',
    extraPersonAmount: formatTrAmountInput(r.extraPersonAmount), reducedPersonAmount: formatTrAmountInput(r.reducedPersonAmount),
    active: r.active,
  };
}

/** Zero is allowed for the per-person amounts, parseTrAmount rejects it. */
function parseOptionalZero(raw: string): number | null {
  if (/^\s*0+([.,]0+)?\s*$/.test(raw)) return 0;
  return parseTrAmount(raw);
}

export default function AutomationPage() {
  const toast = useToast();
  const run = useSingleFlight();
  const { membership, hotelId, loading: workspaceLoading } = usePartner();
  const today = useMemo(() => isoToday(), []);

  const [roomTypes, setRoomTypes] = useState<RoomType[]>([]);
  const [ratePlans, setRatePlans] = useState<RatePlan[]>([]);
  const [overview, setOverview] = useState<AutomationOverview | null>(null);
  const [currency, setCurrency] = useState('TRY');
  const [hotelCurrency, setHotelCurrency] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<{ err: unknown } | null>(null);

  const [stockDrafts, setStockDrafts] = useState<Record<string, { allotment: string; horizon: number; active: boolean }>>({});
  const [form, setForm] = useState<RuleForm | null>(null);
  const [resetOpen, setResetOpen] = useState(false);
  const [resetRoomType, setResetRoomType] = useState('');
  const [resetFrom, setResetFrom] = useState<IsoDate>(today);
  const [resetTo, setResetTo] = useState<IsoDate>(addDaysIso(today, 365));

  const load = useCallback(async (hid: string) => {
    try {
      const [rt, rp, ov, cur] = await Promise.all([
        partnerApi.roomTypes(hid), partnerApi.ratePlans(hid), partnerApi.automation(hid), partnerApi.rateCurrency(hid),
      ]);
      const types = (rt.data ?? []).filter(r => r.status === 'ACTIVE');
      setRoomTypes(types);
      setRatePlans((rp.data ?? []).filter(p => p.status === 'ACTIVE'));
      setOverview(ov.data ?? { inventoryRules: [], rateRules: [], coverage: [] });
      setHotelCurrency(cur.data?.currency ?? ov.data?.rateRules?.[0]?.currency ?? null);
      setStockDrafts({});
      setError(null);
    } catch (e) {
      setError({ err: e });
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

  const ruleFor = (roomTypeId: string): InventoryRule | undefined =>
    overview?.inventoryRules.find(r => r.roomTypeId === roomTypeId);
  const coverageFor = (roomTypeId: string) => overview?.coverage.find(c => c.roomTypeId === roomTypeId);
  const draftFor = (rt: RoomType) => {
    const rule = ruleFor(rt.id);
    return stockDrafts[rt.id] ?? {
      allotment: rule ? String(rule.defaultAllotment) : '',
      horizon: rule?.horizonDays ?? 365,
      active: rule?.active ?? true,
    };
  };
  const setDraft = (rt: RoomType, patch: Partial<{ allotment: string; horizon: number; active: boolean }>) =>
    setStockDrafts(d => ({ ...d, [rt.id]: { ...draftFor(rt), ...patch } }));

  async function saveStock(rt: RoomType) {
    if (!hotelId) return;
    const draft = draftFor(rt);
    const n = Number(draft.allotment);
    if (draft.allotment === '' || !Number.isInteger(n) || n < 0 || n > 10000) {
      toast.error('Gunluk oda sayisi 0-10000 arasi tam sayi olmali');
      return;
    }
    await run(`stock:${rt.id}`, async () => {
      try {
        const res = await partnerApi.saveInventoryRule(hotelId, rt.id, {
          defaultAllotment: n, horizonDays: draft.horizon, active: draft.active, expectedVersion: ruleFor(rt.id)?.version ?? null,
        });
        toast.success(`Stok kurali kaydedildi. ${res.data ? summaryMessage(res.data.summary) : ''}`.trim());
      } catch (e) {
        toast.error(automationErrorMessage(e, 'Stok kurali kaydedilemedi'));
      }
      await load(hotelId);
    });
  }

  // ---- rate rule form ----

  const formRoomType = form ? roomTypes.find(r => r.id === form.roomTypeId) ?? null : null;
  const parsed = useMemo(() => {
    if (!form) return null;
    return {
      base: parseTrAmount(form.baseAmount),
      weekend: form.weekendBaseAmount.trim() === '' ? null : parseTrAmount(form.weekendBaseAmount),
      extra: parseOptionalZero(form.extraPersonAmount),
      reduced: parseOptionalZero(form.reducedPersonAmount),
      baseOcc: Number(form.baseOccupancy),
    };
  }, [form]);

  const formErrors = useMemo(() => {
    if (!form || !parsed || !formRoomType) return {} as Record<string, string>;
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = 'Kural adi gerekli';
    if (parsed.base == null) e.base = AMOUNT_ERROR;
    if (form.weekendBaseAmount.trim() !== '' && parsed.weekend == null) e.weekend = AMOUNT_ERROR;
    if (parsed.extra == null) e.extra = AMOUNT_ERROR;
    if (parsed.reduced == null) e.reduced = AMOUNT_ERROR;
    if (!Number.isInteger(parsed.baseOcc) || parsed.baseOcc < 1 || parsed.baseOcc > formRoomType.maxOccupancy) {
      e.baseOcc = `1-${formRoomType.maxOccupancy} arasi olmali`;
    }
    if (form.season) {
      if (!form.validFrom || !form.validTo) e.dates = 'Baslangic ve bitis tarihi gerekli';
      else if (form.validTo < form.validFrom) e.dates = 'Bitis tarihi baslangictan once olamaz';
    }
    return e;
  }, [form, parsed, formRoomType]);

  const derived = useMemo(() => {
    if (!form || !parsed || !formRoomType || parsed.base == null || formErrors.baseOcc) return null;
    const input = {
      maxOccupancy: formRoomType.maxOccupancy, baseOccupancy: parsed.baseOcc, baseAmount: parsed.base,
      weekendBaseAmount: parsed.weekend, extraPersonAmount: parsed.extra ?? 0, reducedPersonAmount: parsed.reduced ?? 0,
    };
    return { weekday: deriveOccupancyPrices(input), weekend: parsed.weekend != null ? deriveOccupancyPrices(input, true) : null };
  }, [form, parsed, formRoomType, formErrors.baseOcc]);

  const effectiveCurrency = hotelCurrency ?? currency;

  async function saveRule() {
    if (!hotelId || !form || !parsed || Object.keys(formErrors).length > 0 || parsed.base == null) return;
    const body: RateRuleRequest = {
      roomTypeId: form.roomTypeId, ratePlanId: form.ratePlanId, name: form.name.trim(),
      validFrom: form.season ? form.validFrom : null, validTo: form.season ? form.validTo : null,
      currency: effectiveCurrency, baseOccupancy: parsed.baseOcc, baseAmount: parsed.base,
      weekendBaseAmount: parsed.weekend, extraPersonAmount: parsed.extra ?? 0, reducedPersonAmount: parsed.reduced ?? 0,
      active: form.active, expectedVersion: form.version,
    };
    await run('rule:save', async () => {
      try {
        const res = form.id ? await partnerApi.updateRateRule(hotelId, form.id, body) : await partnerApi.createRateRule(hotelId, body);
        toast.success(`Fiyat kurali kaydedildi. ${res.data ? summaryMessage(res.data.summary) : ''}`.trim());
        setForm(null);
      } catch (e) {
        toast.error(automationErrorMessage(e, 'Fiyat kurali kaydedilemedi'));
      }
      await load(hotelId);
    });
  }

  async function deleteRule(rule: RateRule) {
    if (!hotelId || !window.confirm(`"${rule.name}" kurali silinsin mi? Olusturulmus fiyatlar silinmez.`)) return;
    await run(`rule:del:${rule.id}`, async () => {
      try {
        await partnerApi.deleteRateRule(hotelId, rule.id);
        toast.success('Kural silindi. Daha once olusturulan fiyatlar korundu');
      } catch (e) {
        toast.error(automationErrorMessage(e, 'Kural silinemedi'));
      }
      await load(hotelId);
    });
  }

  async function applyNow() {
    if (!hotelId) return;
    await run('apply', async () => {
      try {
        const res = await partnerApi.applyAutomation(hotelId);
        toast.success(res.data ? summaryMessage(res.data) : 'Uygulandi');
      } catch (e) {
        toast.error(automationErrorMessage(e, 'Uygulanamadi'));
      }
      await load(hotelId);
    });
  }

  async function resetToRules() {
    if (!hotelId) return;
    if (resetFrom < today || resetTo < resetFrom) {
      toast.error(automationErrorMessage({ code: 'INVALID_DATE_RANGE' }, ''));
      return;
    }
    const scope = resetRoomType ? roomTypes.find(r => r.id === resetRoomType)?.name ?? 'secili oda tipi' : 'tum oda tipleri';
    if (!window.confirm(`${trDate(resetFrom)} - ${trDate(resetTo)} araligindaki (${scope}) elle girilen kontenjan, satis kapatma ve fiyatlar kurallara gore yeniden yazilacak. Emin misiniz?`)) return;
    await run('reset', async () => {
      try {
        const res = await partnerApi.resetAutomation(hotelId, { roomTypeId: resetRoomType || null, from: resetFrom, to: resetTo });
        toast.success(res.data ? summaryMessage(res.data) : 'Kurala donduruldu');
        setResetOpen(false);
      } catch (e) {
        toast.error(automationErrorMessage(e, 'Kurala dondurulemedi'));
      }
      await load(hotelId);
    });
  }

  // ---- render ----

  if (workspaceLoading) return <LoadingRows />;
  if (!hotelId) return <NeedsHotel />;
  if (loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={sectionErrorMessage(error.err, membership?.roles, 'Otomatik takvim yuklenemedi')} />;
  if (roomTypes.length === 0) {
    return <EmptyState icon="bed" title="Aktif oda tipi yok" description="Otomatik takvim icin once Oda tipleri sayfasindan bir oda tipi tanimlayin." />;
  }

  return (
    <div className="space-y-space-md">
      <PageHeader
        title="Otomatik takvim"
        subtitle="Kurallar her gece ileri tarihleri kendiliginden acar. Takvimde elle girdiginiz gunler (Elle) kurallarla degismez."
        actions={
          <>
            <Button variant="ghost" icon="restart_alt" onClick={() => setResetOpen(o => !o)}>Elle degisiklikleri kurala dondur</Button>
            <Button icon="autorenew" onClick={() => void applyNow()}>Simdi uygula</Button>
          </>
        }
      />

      {resetOpen && (
        <Card className="p-3 flex flex-wrap items-end gap-3">
          <Field label="Oda tipi">
            <select className={inputClass} value={resetRoomType} onChange={e => setResetRoomType(e.target.value)}>
              <option value="">Tum oda tipleri</option>
              {roomTypes.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
            </select>
          </Field>
          <Field label="Baslangic">
            <input type="date" className={inputClass} min={today} value={resetFrom} onChange={e => setResetFrom(e.target.value)} />
          </Field>
          <Field label="Bitis">
            <input type="date" className={inputClass} min={resetFrom} value={resetTo} onChange={e => setResetTo(e.target.value)} />
          </Field>
          <Button variant="danger" icon="restart_alt" onClick={() => void resetToRules()}>Kurala dondur</Button>
          <p className="w-full text-xs text-on-surface-variant">
            Secilen gunlerde elle girilen kontenjan ve fiyatlar kurala gore yeniden yazilir. Odalar servis disi sayisi korunur. En fazla 730 gun.
          </p>
        </Card>
      )}

      {roomTypes.map(rt => {
        const draft = draftFor(rt);
        const cov = coverageFor(rt.id);
        const rules = (overview?.rateRules ?? []).filter(r => r.roomTypeId === rt.id);
        return (
          <Card key={rt.id} className="p-4 space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-base font-semibold text-on-surface">{rt.name}</h2>
              <span className="text-xs text-outline">{rt.code}, en fazla {rt.maxOccupancy} kisi</span>
            </div>

            <div className="space-y-2">
              <h3 className="text-sm font-medium text-on-surface">Stok kurali</h3>
              <div className="flex flex-wrap items-end gap-3">
                <Field label="Gunluk oda sayisi">
                  <input className={`${inputClass} w-28`} inputMode="numeric" value={draft.allotment} placeholder="Orn. 5"
                    onChange={e => setDraft(rt, { allotment: e.target.value.replace(/\D/g, '') })} />
                </Field>
                <Field label="Kac gun ileri acik">
                  <select className={`${inputClass} w-32`} value={draft.horizon} onChange={e => setDraft(rt, { horizon: Number(e.target.value) })}>
                    {(HORIZONS.includes(draft.horizon) ? HORIZONS : [...HORIZONS, draft.horizon].sort((a, b) => a - b))
                      .map(h => <option key={h} value={h}>{h} gun</option>)}
                  </select>
                </Field>
                <label className="flex items-center gap-2 pb-2 text-sm">
                  <input type="checkbox" checked={draft.active} onChange={e => setDraft(rt, { active: e.target.checked })} />
                  Aktif
                </label>
                <Button variant="secondary" icon="save" onClick={() => void saveStock(rt)}>Kaydet</Button>
              </div>
              <p className="text-xs text-on-surface-variant">
                {cov?.inventoryUntil ? `Stok ${trDate(cov.inventoryUntil)} tarihine kadar acik` : 'Henuz stok acilmadi'}
                {cov?.ratesUntil ? `, fiyat ${trDate(cov.ratesUntil)} tarihine kadar acik` : ''}
              </p>
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <h3 className="text-sm font-medium text-on-surface">Fiyat kurallari</h3>
                <Button variant="ghost" icon="add" disabled={ratePlans.length === 0}
                  onClick={() => setForm(emptyForm(rt, ratePlans[0]?.id ?? ''))}>Kural ekle</Button>
              </div>
              {ratePlans.length === 0 && <p className="text-xs text-on-surface-variant">Fiyat kurali icin Fiyat planlari sayfasindan aktif bir plan ekleyin.</p>}
              {rules.length === 0 ? (
                <p className="text-sm text-on-surface-variant">Bu oda tipi icin fiyat kurali yok.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-xs text-on-surface-variant">
                        <th className="py-1 pr-3">Ad</th><th className="py-1 pr-3">Plan</th><th className="py-1 pr-3">Tarih</th>
                        <th className="py-1 pr-3">Temel fiyat</th><th className="py-1 pr-3">Hafta sonu</th><th className="py-1" />
                      </tr>
                    </thead>
                    <tbody>
                      {rules.map(r => (
                        <tr key={r.id} className="border-t border-outline-variant/40">
                          <td className="py-2 pr-3 font-medium">{r.name} {!r.active && <StatusPill tone="neutral">Pasif</StatusPill>}</td>
                          <td className="py-2 pr-3">{ratePlans.find(p => p.id === r.ratePlanId)?.name ?? '-'}</td>
                          <td className="py-2 pr-3">{r.validFrom && r.validTo ? `${trDate(r.validFrom)} - ${trDate(r.validTo)}` : 'Her zaman'}</td>
                          <td className="py-2 pr-3">{r.baseOccupancy} kisi: {money(r.baseAmount, r.currency)}</td>
                          <td className="py-2 pr-3">{r.weekendBaseAmount != null ? money(r.weekendBaseAmount, r.currency) : '-'}</td>
                          <td className="py-2 text-right whitespace-nowrap">
                            <Button variant="ghost" icon="edit" onClick={() => setForm(formFromRule(r))}>Duzenle</Button>
                            <Button variant="ghost" icon="delete" onClick={() => void deleteRule(r)}>Sil</Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {form && form.roomTypeId === rt.id && (
                <div className="rounded-lg border border-outline-variant/60 p-3 space-y-3">
                  <h4 className="text-sm font-semibold">{form.id ? 'Kurali duzenle' : 'Yeni fiyat kurali'}</h4>
                  <div className="grid gap-3 md:grid-cols-3">
                    <Field label="Kural adi" error={formErrors.name}>
                      <input className={inputClass} maxLength={80} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
                    </Field>
                    <Field label="Fiyat plani">
                      <select className={inputClass} value={form.ratePlanId} disabled={form.id != null}
                        onChange={e => setForm({ ...form, ratePlanId: e.target.value })}>
                        {ratePlans.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                      </select>
                    </Field>
                    <Field label="Para birimi" hint={hotelCurrency ? 'Otelin para birimi' : 'Ilk fiyattan sonra degistirilemez'}>
                      <select className={inputClass} value={effectiveCurrency} disabled={hotelCurrency != null} onChange={e => setCurrency(e.target.value)}>
                        {['TRY', 'EUR', 'USD', 'GBP'].map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </Field>
                    <label className="flex items-center gap-2 text-sm md:col-span-3">
                      <input type="checkbox" checked={form.season} onChange={e => setForm({ ...form, season: e.target.checked })} />
                      Sezon (belirli tarih araligi). Kapaliysa kural her zaman gecerlidir.
                    </label>
                    {form.season && (
                      <>
                        <Field label="Baslangic" error={formErrors.dates}>
                          <input type="date" className={inputClass} value={form.validFrom} onChange={e => setForm({ ...form, validFrom: e.target.value })} />
                        </Field>
                        <Field label="Bitis">
                          <input type="date" className={inputClass} value={form.validTo} onChange={e => setForm({ ...form, validTo: e.target.value })} />
                        </Field>
                        <div />
                      </>
                    )}
                    <Field label="Temel kisi sayisi" error={formErrors.baseOcc}>
                      <input className={inputClass} inputMode="numeric" value={form.baseOccupancy}
                        onChange={e => setForm({ ...form, baseOccupancy: e.target.value.replace(/\D/g, '') })} />
                    </Field>
                    <Field label="Temel fiyat" error={formErrors.base}>
                      <input className={inputClass} inputMode="decimal" value={form.baseAmount} placeholder="4.000,00"
                        onChange={e => setForm({ ...form, baseAmount: e.target.value.replace(/[^\d.,]/g, '') })} />
                    </Field>
                    <Field label="Hafta sonu fiyati (Cuma, Cumartesi)" hint="Bos birakirsaniz temel fiyat kullanilir" error={formErrors.weekend}>
                      <input className={inputClass} inputMode="decimal" value={form.weekendBaseAmount}
                        onChange={e => setForm({ ...form, weekendBaseAmount: e.target.value.replace(/[^\d.,]/g, '') })} />
                    </Field>
                    <Field label="Fazla kisi basina ekle" error={formErrors.extra}>
                      <input className={inputClass} inputMode="decimal" value={form.extraPersonAmount}
                        onChange={e => setForm({ ...form, extraPersonAmount: e.target.value.replace(/[^\d.,]/g, '') })} />
                    </Field>
                    <Field label="Eksik kisi basina indir" error={formErrors.reduced}>
                      <input className={inputClass} inputMode="decimal" value={form.reducedPersonAmount}
                        onChange={e => setForm({ ...form, reducedPersonAmount: e.target.value.replace(/[^\d.,]/g, '') })} />
                    </Field>
                    <label className="flex items-center gap-2 text-sm pt-5">
                      <input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} />
                      Aktif
                    </label>
                  </div>

                  {derived && (
                    <div className="text-sm space-y-1" data-testid="derived-prices">
                      <p>
                        {derived.weekend ? 'Hafta ici: ' : ''}
                        {derived.weekday.prices.map(p => `${p.occupancy} kisi ${money(p.amount, effectiveCurrency)}`).join(' \u00b7 ') || 'Fiyat yok'}
                      </p>
                      {derived.weekend && (
                        <p>Hafta sonu: {derived.weekend.prices.map(p => `${p.occupancy} kisi ${money(p.amount, effectiveCurrency)}`).join(' \u00b7 ') || 'Fiyat yok'}</p>
                      )}
                      {(() => {
                        const missing = [...new Set([...derived.weekday.unpriced, ...(derived.weekend?.unpriced ?? [])])].sort((a, b) => a - b);
                        return missing.length > 0 ? (
                          <p className="text-error" role="alert">
                            Uyari: {missing.join(', ')} kisilik konaklamalar icin fiyat olusturulmaz (tutar sifir veya negatif).
                          </p>
                        ) : null;
                      })()}
                    </div>
                  )}

                  <div className="flex gap-2">
                    <Button icon="save" disabled={Object.keys(formErrors).length > 0 || !form.ratePlanId} onClick={() => void saveRule()}>Kaydet ve uygula</Button>
                    <Button variant="ghost" onClick={() => setForm(null)}>Vazgec</Button>
                  </div>
                </div>
              )}
            </div>
          </Card>
        );
      })}
    </div>
  );
}
