'use client';

/**
 * Inventory and rate calendar for the selected hotel.
 *
 * Shows a 14-day window for one room type: a row of allotment cells, a row of stop-sell toggles,
 * and one price row per active rate plan (at the room type's max occupancy). Edits are collected
 * locally and sent as two batch requests (inventory, then rates) when the user presses Kaydet.
 * The backend returns no row for days that were never set, so those cells start empty.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import {
  partnerApi,
  partnerErrorMessage,
  isVersionConflict,
  MEAL_PLAN_LABEL,
  type DailyInventory,
  type DailyRate,
  type InventoryUpdateItem,
  type IsoDate,
  type RatePlan,
  type RateUpdateItem,
  type RoomType,
} from '@/lib/partner';
import { Button, Card, EmptyState, LoadingRows, NeedsHotel, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';
import { cn } from '@/lib/utils';

const WINDOW_DAYS = 14;
const MAX_AHEAD_DAYS = 730;

function toIso(d: Date): IsoDate {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function addDays(iso: IsoDate, n: number): IsoDate {
  const [y, m, d] = iso.split('-').map(Number);
  return toIso(new Date(y, m - 1, d + n));
}

function daysBetween(a: IsoDate, b: IsoDate): number {
  const [ay, am, ad] = a.split('-').map(Number);
  const [by, bm, bd] = b.split('-').map(Number);
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / 86_400_000);
}

const WEEKDAY = ['Paz', 'Pzt', 'Sal', 'Car', 'Per', 'Cum', 'Cmt'];

function dayLabel(iso: IsoDate) {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  return { day: d, weekday: WEEKDAY[date.getDay()], weekend: date.getDay() === 0 || date.getDay() === 6 };
}

/** Local, unsaved edits. Keys: `${date}` for inventory, `${ratePlanId}|${date}` for rates. */
interface Edits {
  allotment: Record<string, string>;
  stopSell: Record<string, boolean>;
  amount: Record<string, string>;
}

const NO_EDITS: Edits = { allotment: {}, stopSell: {}, amount: {} };

const AMOUNT_RE = /^\d{1,10}([.,]\d{1,2})?$/;

export default function CalendarPage() {
  const toast = useToast();
  const { hotelId, loading: workspaceLoading, setHasUnsavedChanges } = usePartner();
  const today = useMemo(() => toIso(new Date()), []);

  const [roomTypes, setRoomTypes] = useState<RoomType[]>([]);
  const [ratePlans, setRatePlans] = useState<RatePlan[]>([]);
  const [roomTypeId, setRoomTypeId] = useState<string | null>(null);
  const [from, setFrom] = useState<IsoDate>(today);
  const [inventory, setInventory] = useState<DailyInventory[]>([]);
  const [rates, setRates] = useState<DailyRate[]>([]);
  const [hotelCurrency, setHotelCurrency] = useState<string | null>(null);
  const [edits, setEdits] = useState<Edits>(NO_EDITS);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [gridLoading, setGridLoading] = useState(false);
  const [gridError, setGridError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);
  const gridRequest = useRef(0);

  const to = addDays(from, WINDOW_DAYS - 1);
  const dates = useMemo(() => Array.from({ length: WINDOW_DAYS }, (_, i) => addDays(from, i)), [from]);
  const roomType = roomTypes.find(r => r.id === roomTypeId) ?? null;
  const activePlans = ratePlans.filter(p => p.status === 'ACTIVE');
  // Locked to the hotel's existing currency; the partner chooses it only before the first price exists.
  const [chosenCurrency, setChosenCurrency] = useState('TRY');
  const currency = hotelCurrency ?? chosenCurrency;

  // ---- loading ----

  const loadCatalog = useCallback(async (hid: string) => {
    try {
      const [rt, rp, cur] = await Promise.all([
        partnerApi.roomTypes(hid),
        partnerApi.ratePlans(hid),
        partnerApi.rateCurrency(hid),
      ]);
      const types = (rt.data ?? []).filter(r => r.status === 'ACTIVE');
      setRoomTypes(types);
      setRatePlans(rp.data ?? []);
      setRoomTypeId(prev => (types.some(t => t.id === prev) ? prev : types[0]?.id ?? null));
      setHotelCurrency(cur.data?.currency ?? null);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Katalog yuklenemedi');
    } finally {
      setCatalogLoading(false);
    }
  }, []);

  const loadGrid = useCallback(async (hid: string, rtId: string, start: IsoDate) => {
    const request = ++gridRequest.current;
    setGridLoading(true);
    setGridError(null);
    try {
      const end = addDays(start, WINDOW_DAYS - 1);
      const [inv, rt] = await Promise.all([
        partnerApi.inventory(hid, start, end, rtId),
        partnerApi.rates(hid, start, end, rtId),
      ]);
      if (request !== gridRequest.current) return;
      setInventory(inv.data ?? []);
      setRates(rt.data ?? []);
    } catch (e) {
      if (request === gridRequest.current) {
        const message = e instanceof Error ? e.message : 'Takvim yuklenemedi';
        setGridError(message);
        toast.error(message);
      }
    } finally {
      if (request === gridRequest.current) setGridLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    if (!hotelId) return;
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return loadCatalog(hotelId); });
    return () => { cancelled = true; };
  }, [hotelId, loadCatalog]);

  const invalidateGrid = useCallback(() => { gridRequest.current++; }, []);

  useEffect(() => {
    if (!hotelId || !roomTypeId) return;
    let cancelled = false;
    // Room type and date window can only change when there are no unsaved edits (controls are
    // disabled otherwise), so starting from a clean edit set here never loses work.
    void Promise.resolve().then(() => {
      if (cancelled) return;
      setEdits(NO_EDITS);
      return loadGrid(hotelId, roomTypeId, from);
    });
    return () => { cancelled = true; invalidateGrid(); };
  }, [hotelId, roomTypeId, from, loadGrid, invalidateGrid]);

  // ---- derived cell values ----

  const invByDate = useMemo(() => new Map(inventory.map(r => [r.stayDate, r])), [inventory]);
  const rateByKey = useMemo(() => {
    const map = new Map<string, DailyRate>();
    if (!roomType) return map;
    for (const r of rates) {
      if (r.occupancy === roomType.maxOccupancy) map.set(`${r.ratePlanId}|${r.stayDate}`, r);
    }
    return map;
  }, [rates, roomType]);

  const editable = (date: IsoDate) => daysBetween(today, date) >= -1 && daysBetween(today, date) <= MAX_AHEAD_DAYS;

  const allotmentValue = (date: IsoDate) =>
    edits.allotment[date] ?? (invByDate.get(date) ? String(invByDate.get(date)!.allotment) : '');
  const stopSellValue = (date: IsoDate) => edits.stopSell[date] ?? invByDate.get(date)?.stopSell ?? false;
  const amountValue = (planId: string, date: IsoDate) =>
    edits.amount[`${planId}|${date}`] ?? (rateByKey.get(`${planId}|${date}`) ? String(rateByKey.get(`${planId}|${date}`)!.amount) : '');

  const changeCount = Object.keys(edits.allotment).length + Object.keys(edits.stopSell).length + Object.keys(edits.amount).length;

  // Tell the workspace (hotel selector) and the browser that there is unsaved work.
  const dirty = changeCount > 0;
  useEffect(() => {
    setHasUnsavedChanges(dirty);
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, setHasUnsavedChanges]);
  useEffect(() => () => setHasUnsavedChanges(false), [setHasUnsavedChanges]);

  // ---- validation ----

  const allotmentError = (date: IsoDate): string | null => {
    const raw = edits.allotment[date];
    if (raw === undefined) return null;
    if (raw === '') return invByDate.get(date) ? 'Bos birakilamaz' : null;
    const n = Number(raw);
    if (!Number.isInteger(n) || n < 0 || n > 10000) return '0-10000 arasi tam sayi';
    const row = invByDate.get(date);
    if (row && n < row.held + row.confirmed) return `En az ${row.held + row.confirmed} (satilmis)`;
    return null;
  };

  const amountError = (key: string): string | null => {
    const raw = edits.amount[key];
    if (raw === undefined || raw === '') return null;
    if (!AMOUNT_RE.test(raw.trim())) return 'Gecersiz tutar';
    if (Number(raw.replace(',', '.')) <= 0) return 'Sifirdan buyuk olmali';
    return null;
  };

  const stopSellNeedsAllotment = (date: IsoDate) =>
    edits.stopSell[date] !== undefined && !invByDate.get(date) && (edits.allotment[date] ?? '') === '';

  const hasErrors =
    dates.some(d => allotmentError(d) || stopSellNeedsAllotment(d)) ||
    Object.keys(edits.amount).some(k => amountError(k));

  // ---- save ----

  async function save() {
    if (!hotelId || !roomType || inFlight.current || gridLoading || gridError || changeCount === 0 || hasErrors) return;
    inFlight.current = true;
    setSaving(true);

    const invItems: InventoryUpdateItem[] = [];
    for (const date of dates) {
      const a = edits.allotment[date];
      const s = edits.stopSell[date];
      if (a === undefined && s === undefined) continue;
      if (a === '' && s === undefined) continue;
      const row = invByDate.get(date);
      invItems.push({
        roomTypeId: roomType.id,
        stayDate: date,
        ...(a !== undefined && a !== '' ? { allotment: Number(a) } : {}),
        ...(s !== undefined ? { stopSell: s } : {}),
        expectedVersion: row?.version ?? null,
      });
    }

    const rateItems: RateUpdateItem[] = [];
    for (const [key, raw] of Object.entries(edits.amount)) {
      if (raw.trim() === '') continue;
      const [ratePlanId, stayDate] = key.split('|');
      rateItems.push({
        roomTypeId: roomType.id,
        ratePlanId,
        stayDate,
        occupancy: roomType.maxOccupancy,
        amount: Number(raw.replace(',', '.')),
        currency,
        expectedVersion: rateByKey.get(key)?.version ?? null,
      });
    }

    let inventorySaved = false;
    try {
      if (invItems.length) {
        await partnerApi.updateInventory(hotelId, invItems, 'Takvim ekranindan guncelleme');
        inventorySaved = true;
      }
      if (rateItems.length) {
        await partnerApi.updateRates(hotelId, rateItems);
        if (!hotelCurrency) setHotelCurrency(currency);
      }
      toast.success(`${invItems.length + rateItems.length} gun guncellendi`);
      setEdits(NO_EDITS);
      await loadGrid(hotelId, roomType.id, from);
    } catch (e) {
      toast.error(partnerErrorMessage(e, 'Kaydedilemedi'));
      if (isVersionConflict(e)) {
        // Someone else changed these days: the versions in the edits are stale. Show fresh data and
        // let the partner re-enter, instead of silently overwriting the other change.
        setEdits(NO_EDITS);
      } else if (inventorySaved) {
        // Inventory went through, rates failed: keep only the rate edits so they can be fixed and resent.
        setEdits(x => ({ ...x, allotment: {}, stopSell: {} }));
      }
      // Other errors (e.g. one invalid value): keep every edit so the partner can fix it.
      await loadGrid(hotelId, roomType.id, from);
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  // ---- bulk fill helper ----

  const [bulkAllotment, setBulkAllotment] = useState('');
  const applyBulk = () => {
    if (bulkAllotment === '' || inFlight.current || gridLoading) return;
    setEdits(e => {
      const allotment = { ...e.allotment };
      for (const d of dates) if (editable(d)) allotment[d] = bulkAllotment;
      return { ...e, allotment };
    });
  };

  // ---- render ----

  if (workspaceLoading) return <LoadingRows />;
  if (!hotelId) return <NeedsHotel />;
  if (catalogLoading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;
  if (roomTypes.length === 0) {
    return <EmptyState icon="bed" title="Aktif oda tipi yok"
      description="Takvimi kullanmak icin Oda tipleri sayfasindan en az bir aktif oda tipi tanimlayin." />;
  }

  const gridBlocked = gridLoading || gridError !== null;
  const canGoBack = daysBetween(today, from) > -1;

  return (
    <div className="space-y-space-md">
      <PageHeader
        title="Musaitlik ve fiyat takvimi"
        subtitle={`Fiyatlar ${roomType?.maxOccupancy ?? ''} kisilik konaklama icin, para birimi ${currency}`}
        actions={
          <>
            {changeCount > 0 && <StatusPill tone="warning">{changeCount} kaydedilmemis degisiklik</StatusPill>}
            <Button variant="ghost" icon="undo" disabled={changeCount === 0 || saving} onClick={() => setEdits(NO_EDITS)}>Geri al</Button>
            <Button icon="save" disabled={changeCount === 0 || hasErrors || saving || gridBlocked} onClick={save}>
              {saving ? 'Kaydediliyor' : 'Kaydet'}
            </Button>
          </>
        }
      />

      <Card className="p-3 flex flex-wrap items-end gap-3">
        <label className="space-y-1 min-w-[200px]">
          <span className="text-[11px] font-medium text-on-surface-variant">Oda tipi</span>
          <select className={inputClass} value={roomTypeId ?? ''} disabled={changeCount > 0 || saving || gridLoading}
            onChange={e => setRoomTypeId(e.target.value)}>
            {roomTypes.map(r => <option key={r.id} value={r.id}>{r.name} ({r.code})</option>)}
          </select>
        </label>
        <div className="flex items-center gap-1">
          <Button variant="ghost" icon="chevron_left" disabled={!canGoBack || changeCount > 0 || saving || gridLoading}
            onClick={() => setFrom(f => (daysBetween(today, addDays(f, -WINDOW_DAYS)) < -1 ? addDays(today, -1) : addDays(f, -WINDOW_DAYS)))}>
            Onceki
          </Button>
          <Button variant="ghost" disabled={changeCount > 0 || saving || gridLoading || from === today} onClick={() => setFrom(today)}>Bugun</Button>
          <Button variant="ghost" disabled={changeCount > 0 || saving || gridLoading || daysBetween(today, to) >= MAX_AHEAD_DAYS}
            onClick={() => setFrom(f => addDays(f, WINDOW_DAYS))}>
            Sonraki<span className="material-symbols-outlined text-[18px]">chevron_right</span>
          </Button>
        </div>
        <div className="flex items-end gap-2 ml-auto">
          {!hotelCurrency && (
            <label className="space-y-1" title="Ilk fiyat kaydedildikten sonra otelin para birimi degistirilemez">
              <span className="text-[11px] font-medium text-on-surface-variant">Para birimi</span>
              <select className={cn(inputClass, 'w-24')} value={chosenCurrency} disabled={saving || gridLoading} onChange={e => { if (!inFlight.current) setChosenCurrency(e.target.value); }}>
                {['TRY', 'EUR', 'USD', 'GBP'].map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
          )}
          <label className="space-y-1">
            <span className="text-[11px] font-medium text-on-surface-variant">Tum gunlere kontenjan</span>
            <input className={cn(inputClass, 'w-24')} inputMode="numeric" value={bulkAllotment} disabled={saving || gridLoading}
              onChange={e => setBulkAllotment(e.target.value.replace(/\D/g, ''))} placeholder="Orn. 10" />
          </label>
          <Button variant="secondary" disabled={bulkAllotment === '' || saving || gridBlocked} onClick={applyBulk}>Uygula</Button>
        </div>
        {changeCount > 0 && (
          <p className="w-full text-xs text-on-surface-variant">Oda tipi veya tarih degistirmeden once degisiklikleri kaydedin ya da geri alin.</p>
        )}
      </Card>

      {gridError && (
        <Card className="p-3 flex items-center gap-3 text-error">
          <span className="flex-1 text-sm">{gridError}. Duzenlemeleriniz korunuyor. Devam etmeden once takvimi yeniden yukleyin.</span>
          <Button variant="ghost" onClick={() => { if (hotelId && roomTypeId) void loadGrid(hotelId, roomTypeId, from); }}>Tekrar dene</Button>
        </Card>
      )}
      <Card className={cn('overflow-x-auto', gridBlocked && 'opacity-60 pointer-events-none')}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr>
              <th className="sticky left-0 z-10 bg-surface-container-lowest text-left px-3 py-2 text-xs font-medium text-on-surface-variant min-w-[170px]">
                {from} / {to}
              </th>
              {dates.map(d => {
                const l = dayLabel(d);
                return (
                  <th key={d} className={cn('px-1 py-2 text-center min-w-[78px]', l.weekend && 'bg-surface-container-low', d === today && 'text-primary')}>
                    <div className="text-[11px] font-medium text-on-surface-variant">{l.weekday}</div>
                    <div className="text-base font-semibold">{l.day}</div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            <tr className="border-t border-outline-variant/50">
              <td className="sticky left-0 z-10 bg-surface-container-lowest px-3 py-2">
                <div className="font-medium text-on-surface">Kontenjan</div>
                <div className="text-[11px] text-outline">Satilabilir oda sayisi</div>
              </td>
              {dates.map(d => {
                const err = allotmentError(d);
                const row = invByDate.get(d);
                const changed = edits.allotment[d] !== undefined;
                return (
                  <td key={d} className="px-1 py-1.5 align-top">
                    <input
                      aria-label={`Kontenjan ${d}`}
                      className={cn(inputClass, 'px-1.5 text-center', changed && 'border-2 border-primary bg-primary-fixed font-semibold', err && 'border-error')}
                      inputMode="numeric" disabled={!editable(d) || saving || gridBlocked} placeholder="-"
                      value={allotmentValue(d)}
                      onChange={e => {
                        if (inFlight.current || gridLoading) return;
                        const v = e.target.value.replace(/\D/g, '');
                        setEdits(x => ({ ...x, allotment: { ...x.allotment, [d]: v } }));
                      }} />
                    {err ? <div className="text-[10px] text-error mt-0.5 leading-tight">{err}</div>
                      : row ? <div className="text-[10px] text-outline mt-0.5 text-center">{row.available} bos</div> : null}
                  </td>
                );
              })}
            </tr>
            <tr className="border-t border-outline-variant/50">
              <td className="sticky left-0 z-10 bg-surface-container-lowest px-3 py-2">
                <div className="font-medium text-on-surface">Satisa kapali</div>
                <div className="text-[11px] text-outline">Isaretli gunler satilmaz</div>
              </td>
              {dates.map(d => {
                const closed = stopSellValue(d);
                const needsAllot = stopSellNeedsAllotment(d);
                return (
                  <td key={d} className="px-1 py-1.5 text-center align-top">
                    <button
                      type="button" aria-label={`Satisa kapali ${d}`} aria-pressed={closed}
                      disabled={!editable(d) || saving || gridBlocked}
                      onClick={() => { if (!inFlight.current && !gridLoading) setEdits(x => ({ ...x, stopSell: { ...x.stopSell, [d]: !closed } })); }}
                      className={cn(
                        'w-9 h-8 rounded-lg inline-flex items-center justify-center transition-colors disabled:opacity-40',
                        closed ? 'bg-error-container text-on-error-container' : 'bg-surface-container text-outline hover:bg-surface-container-high',
                        edits.stopSell[d] !== undefined && 'ring-2 ring-primary',
                      )}>
                      <span className="material-symbols-outlined text-[18px]">{closed ? 'block' : 'check'}</span>
                    </button>
                    {needsAllot && <div className="text-[10px] text-error mt-0.5 leading-tight">Once kontenjan</div>}
                  </td>
                );
              })}
            </tr>
            {activePlans.length === 0 ? (
              <tr className="border-t border-outline-variant/50">
                <td colSpan={WINDOW_DAYS + 1} className="px-3 py-4 text-sm text-on-surface-variant">
                  Fiyat girmek icin Fiyat planlari sayfasindan aktif bir plan ekleyin.
                </td>
              </tr>
            ) : activePlans.map(plan => (
              <tr key={plan.id} className="border-t border-outline-variant/50">
                <td className="sticky left-0 z-10 bg-surface-container-lowest px-3 py-2">
                  <div className="font-medium text-on-surface">{plan.name}</div>
                  <div className="text-[11px] text-outline">{MEAL_PLAN_LABEL[plan.mealPlan]}, gecelik {currency}</div>
                </td>
                {dates.map(d => {
                  const key = `${plan.id}|${d}`;
                  const err = amountError(key);
                  const changed = edits.amount[key] !== undefined;
                  return (
                    <td key={d} className="px-1 py-1.5 align-top">
                      <input
                        aria-label={`${plan.name} fiyat ${d}`}
                        className={cn(inputClass, 'px-1.5 text-right', changed && 'border-2 border-primary bg-primary-fixed font-semibold', err && 'border-error')}
                        inputMode="decimal" disabled={!editable(d) || saving || gridBlocked} placeholder="-"
                        value={amountValue(plan.id, d)}
                        onChange={e => {
                          if (inFlight.current || gridLoading) return;
                          const v = e.target.value.replace(/[^\d.,]/g, '');
                          setEdits(x => ({ ...x, amount: { ...x.amount, [key]: v } }));
                        }} />
                      {err && <div className="text-[10px] text-error mt-0.5 leading-tight">{err}</div>}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <p className="text-xs text-on-surface-variant">
        Bos hucreler henuz tanimlanmamis gunlerdir. Bir gunu satisa acmak icin kontenjan ve en az bir fiyat girin.
        Fiyat silme ve kisi sayisina gore farkli fiyat bu ekranda henuz yok.
      </p>
    </div>
  );
}
