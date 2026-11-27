'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { adminApi } from '@/lib/admin';
import { ApiError } from '@/lib/api';
import { formatTrAmountInput, parseTrAmount, AMOUNT_ERROR } from '@/lib/amount';
import {
  DEFAULT_CABINS, SCHEDULE_CODE_PATTERN, SEAT_TYPES, WEEKDAYS, flightNumberPreview, flightSchedulesApi,
  scheduleErrorText, summaryText, weekdaysSummary,
  type FlightSchedule, type ScheduleRequest, type SeatType, type Weekday,
} from '@/lib/flight-schedules';
import { useSingleFlight } from '@/lib/use-single-flight';
import type { Airport } from '@/lib/types';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

interface CabinForm { seatType: SeatType; fromRow: string; toRow: string; letters: string; price: string }
interface Form {
  code: string; airline: string; from: string; to: string; weekdays: Weekday[]; departureTime: string;
  durationMinutes: string; validFrom: string; validTo: string; horizonDays: string; cabins: CabinForm[];
}

function emptyForm(): Form {
  return {
    code: '', airline: '', from: '', to: '', weekdays: [], departureTime: '09:00', durationMinutes: '90',
    validFrom: new Date().toISOString().slice(0, 10), validTo: '', horizonDays: '30',
    cabins: DEFAULT_CABINS.map(c => ({ ...c })),
  };
}

function errText(e: unknown): string {
  if (e instanceof ApiError) return scheduleErrorText(e.code, e.message);
  return scheduleErrorText(null, e instanceof Error ? e.message : undefined);
}

function toRequest(f: Form, version?: number): ScheduleRequest | string {
  const cabins = [];
  for (const c of f.cabins) {
    const price = parseTrAmount(c.price);
    if (price == null) return `${AMOUNT_ERROR}`;
    cabins.push({ seatType: c.seatType, price, fromRow: Number(c.fromRow), toRow: Number(c.toRow), letters: c.letters.trim().toUpperCase() });
  }
  return {
    code: f.code.trim().toUpperCase(), airline: f.airline.trim(), departureAirportCode: f.from, arrivalAirportCode: f.to,
    weekdays: f.weekdays, departureTime: f.departureTime, durationMinutes: Number(f.durationMinutes),
    validFrom: f.validFrom, validTo: f.validTo || null, horizonDays: Number(f.horizonDays), cabins,
    ...(version !== undefined ? { expectedVersion: version } : {}),
  };
}

function toForm(s: FlightSchedule): Form {
  return {
    code: s.code, airline: s.airline, from: s.departureAirportCode, to: s.arrivalAirportCode, weekdays: s.weekdays,
    departureTime: s.departureTime.slice(0, 5), durationMinutes: String(s.durationMinutes), validFrom: s.validFrom,
    validTo: s.validTo ?? '', horizonDays: String(s.horizonDays),
    cabins: s.cabins.map(c => ({ seatType: c.seatType, fromRow: String(c.fromRow), toRow: String(c.toRow), letters: c.letters, price: formatTrAmountInput(c.price) })),
  };
}

function fmtDeparture(iso: string): string {
  return new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Istanbul' });
}

export default function AdminFlightSchedulesPage() {
  const toast = useToast();
  const single = useSingleFlight();
  const [schedules, setSchedules] = useState<FlightSchedule[]>([]);
  const [airports, setAirports] = useState<Airport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: string | null; version?: number } | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);

  const load = useCallback(async () => {
    try {
      const [s, a] = await Promise.all([flightSchedulesApi.list(), adminApi.airports()]);
      setSchedules(s.data ?? []);
      setAirports(a.data ?? []);
      setError(null);
    } catch (e) { setError(errText(e)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  const setField = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));
  const setCabin = (i: number, k: keyof CabinForm, v: string) =>
    setForm(f => ({ ...f, cabins: f.cabins.map((c, j) => (j === i ? { ...c, [k]: v } : c)) }));
  const toggleDay = (d: Weekday) =>
    setForm(f => ({ ...f, weekdays: f.weekdays.includes(d) ? f.weekdays.filter(x => x !== d) : [...f.weekdays, d] }));

  const valid = SCHEDULE_CODE_PATTERN.test(form.code.trim().toUpperCase()) && !!form.airline.trim() && !!form.from && !!form.to
    && form.weekdays.length > 0 && !!form.departureTime && !!form.validFrom && form.cabins.length > 0;
  const preview = flightNumberPreview(form.code, form.validFrom);

  function save(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || !editing) return;
    const req = toRequest(form, editing.version);
    if (typeof req === 'string') { toast.error(req); return; }
    void single('save', async () => {
      try {
        const res = editing.id ? await flightSchedulesApi.update(editing.id, req) : await flightSchedulesApi.create(req);
        toast.success(`Plan kaydedildi. ${summaryText(res.data?.summary)}`.trim());
        setEditing(null);
        await load();
      } catch (err) { toast.error(errText(err)); }
    });
  }

  function toggleActive(s: FlightSchedule) {
    void single(`toggle-${s.id}`, async () => {
      try {
        const res = s.active ? await flightSchedulesApi.deactivate(s.id) : await flightSchedulesApi.activate(s.id);
        toast.success(s.active ? 'Plan pasif yapildi' : `Plan aktif. ${summaryText(res.data?.summary)}`.trim());
        await load();
      } catch (err) { toast.error(errText(err)); }
    });
  }

  function applyNow() {
    void single('apply', async () => {
      try {
        const res = await flightSchedulesApi.applyAll();
        toast.success(`Uygulandi. ${summaryText(res.data)}`.trim());
        await load();
      } catch (err) { toast.error(errText(err)); }
    });
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Ucus Planlari" subtitle={`${schedules.length} plan`}
        actions={<>
          <Button variant="secondary" icon="play_arrow" onClick={applyNow}>Simdi uygula</Button>
          <Button icon="add" onClick={() => { setEditing({ id: null }); setForm(emptyForm()); }}>Yeni plan</Button>
        </>} />
      {editing && (
        <Card className="p-4">
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <Field label="Plan kodu" hint="2 harf + 1-3 rakam, ornek WD1">
              <input className={inputClass} maxLength={5} value={form.code} onChange={setField('code')} />
            </Field>
            <Field label="Havayolu"><input className={inputClass} maxLength={100} value={form.airline} onChange={setField('airline')} /></Field>
            <Field label="Kalkis havalimani">
              <select className={inputClass} value={form.from} onChange={setField('from')}>
                <option value="">Seciniz</option>
                {airports.map(a => <option key={a.id} value={a.iataCode}>{a.iataCode} - {a.city}</option>)}
              </select>
            </Field>
            <Field label="Varis havalimani">
              <select className={inputClass} value={form.to} onChange={setField('to')}>
                <option value="">Seciniz</option>
                {airports.map(a => <option key={a.id} value={a.iataCode}>{a.iataCode} - {a.city}</option>)}
              </select>
            </Field>
            <div className="sm:col-span-2">
              <p className="mb-1 text-sm font-medium text-on-surface">Gunler</p>
              <div className="flex flex-wrap gap-2">
                {WEEKDAYS.map(d => (
                  <label key={d.value} className="inline-flex items-center gap-1.5 text-sm">
                    <input type="checkbox" checked={form.weekdays.includes(d.value)} onChange={() => toggleDay(d.value)} />
                    {d.label}
                  </label>
                ))}
              </div>
            </div>
            <Field label="Kalkis saati"><input type="time" className={inputClass} value={form.departureTime} onChange={setField('departureTime')} /></Field>
            <Field label="Sure (dakika)" hint="20 - 1200">
              <input type="number" min={20} max={1200} className={inputClass} value={form.durationMinutes} onChange={setField('durationMinutes')} />
            </Field>
            <Field label="Baslangic tarihi"><input type="date" className={inputClass} value={form.validFrom} onChange={setField('validFrom')} /></Field>
            <Field label="Bitis tarihi" hint="Bos birakilirsa suresiz">
              <input type="date" className={inputClass} value={form.validTo} onChange={setField('validTo')} />
            </Field>
            <Field label="Kac gun ileri" hint="1 - 120">
              <input type="number" min={1} max={120} className={inputClass} value={form.horizonDays} onChange={setField('horizonDays')} />
            </Field>
            {preview && <p className="self-end text-sm text-on-surface-variant">Ilk ucus numarasi: <span className="font-semibold text-on-surface">{preview}</span></p>}
            <div className="sm:col-span-2 space-y-2">
              <p className="text-sm font-medium text-on-surface">Kabinler</p>
              {form.cabins.map((c, i) => (
                <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-6 items-end">
                  <Field label="Sinif">
                    <select className={inputClass} value={c.seatType} onChange={e => setCabin(i, 'seatType', e.target.value)}>
                      {SEAT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                  </Field>
                  <Field label="Ilk sira"><input type="number" min={1} max={999} className={inputClass} value={c.fromRow} onChange={e => setCabin(i, 'fromRow', e.target.value)} /></Field>
                  <Field label="Son sira"><input type="number" min={1} max={999} className={inputClass} value={c.toRow} onChange={e => setCabin(i, 'toRow', e.target.value)} /></Field>
                  <Field label="Harfler"><input className={inputClass} maxLength={11} value={c.letters} onChange={e => setCabin(i, 'letters', e.target.value)} /></Field>
                  <Field label="Fiyat (TL)">
                    <input inputMode="decimal" className={inputClass} value={c.price} placeholder="2.400,00"
                      onChange={e => setCabin(i, 'price', e.target.value)}
                      onBlur={() => { const n = parseTrAmount(c.price); if (n != null) setCabin(i, 'price', formatTrAmountInput(n)); }} />
                  </Field>
                  <Button type="button" variant="ghost" icon="delete" disabled={form.cabins.length <= 1}
                    onClick={() => setForm(f => ({ ...f, cabins: f.cabins.filter((_, j) => j !== i) }))}>Kaldir</Button>
                </div>
              ))}
              <Button type="button" variant="ghost" icon="add" disabled={form.cabins.length >= SEAT_TYPES.length}
                onClick={() => setForm(f => ({ ...f, cabins: [...f.cabins, { seatType: 'PREMIUM_ECONOMY', fromRow: '', toRow: '', letters: 'ABCD', price: '' }] }))}>
                Kabin ekle
              </Button>
            </div>
            <div className="sm:col-span-2 flex gap-2">
              <Button type="submit" icon="check" disabled={!valid}>Kaydet</Button>
              <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Vazgec</Button>
            </div>
          </form>
        </Card>
      )}
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : schedules.length === 0 ? <EmptyState icon="event_repeat" title="Ucus plani yok" />
        : (
          <div className="space-y-2">
            {schedules.map(s => (
              <Card key={s.id} className="p-4 space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-on-surface">
                      {s.code} <span className="font-normal text-on-surface-variant">{s.departureAirportCode} - {s.arrivalAirportCode} · {s.airline}</span>
                    </p>
                    <p className="text-xs text-on-surface-variant">
                      {weekdaysSummary(s.weekdays)} · {s.departureTime.slice(0, 5)} · {s.durationMinutes} dk · {s.horizonDays} gun ileri
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusPill tone={s.active ? 'success' : 'neutral'}>{s.active ? 'Aktif' : 'Pasif'}</StatusPill>
                    <Button variant="secondary" onClick={() => toggleActive(s)}>{s.active ? 'Pasif yap' : 'Aktif yap'}</Button>
                    <Button variant="ghost" icon="edit" onClick={() => { setEditing({ id: s.id, version: s.version }); setForm(toForm(s)); }}>Duzenle</Button>
                  </div>
                </div>
                {s.nextFlights.length > 0 && (
                  <p className="text-xs text-on-surface-variant">
                    Siradaki ucuslar: {s.nextFlights.map(n => `${n.flightNumber} (${fmtDeparture(n.departureTime)})`).join(', ')}
                  </p>
                )}
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
