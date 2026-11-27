'use client';

/** "Haftalik sefer planlari": weekly timetables the backend turns into trips every day. */
import { useEffect, useState } from 'react';
import { useSingleFlight } from '@/lib/use-single-flight';
import {
  busErrorMessage, formatClock, formatTripTime, partnerBusApi, scheduleOffsets, validateFares, weekdayLabel, WEEKDAYS,
  type BusUnit, type ScheduleClockStop, type Terminal, type TripFareInput, type TripSchedule, type Weekday,
} from '@/lib/bus';
import { useToast } from '@/components/Toast';
import { Button, Card, Field, StatusPill, inputClass } from '@/components/partner/ui';

const HORIZONS = [14, 30, 60, 90];
const emptyStop = (): ScheduleClockStop => ({ terminalId: '', arrival: '', arrivalNextDay: false, departure: '', departureNextDay: false });
const todayIso = () => new Date().toISOString().slice(0, 10);

function route(s: TripSchedule): string {
  return s.stops.map(x => x.city ?? '?').join(' > ');
}

function nextText(s: TripSchedule): string {
  const n = s.nextDepartures[0];
  if (!n) return 'Sonraki sefer yok';
  const t = formatTripTime(n); // dd.mm.yyyy HH:mm
  return `Sonraki: ${t.slice(0, 5)} ${t.slice(11)}`;
}

function ScheduleForm({ organizationId, buses, terminals, onDone }: {
  organizationId: string; buses: BusUnit[]; terminals: Terminal[]; onDone: (saved: boolean) => void;
}) {
  const toast = useToast();
  const flight = useSingleFlight();
  const [busId, setBusId] = useState(buses[0]?.id ?? '');
  const [code, setCode] = useState('');
  const [days, setDays] = useState<Weekday[]>([]);
  const [time, setTime] = useState('');
  const [stops, setStops] = useState<ScheduleClockStop[]>([emptyStop(), emptyStop()]);
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [validFrom, setValidFrom] = useState(todayIso());
  const [validTo, setValidTo] = useState('');
  const [horizon, setHorizon] = useState(30);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const pairs: [number, number][] = [];
  for (let i = 0; i < stops.length; i++) for (let j = i + 1; j < stops.length; j++) pairs.push([i, j]);
  const setStop = (i: number, patch: Partial<ScheduleClockStop>) => setStops(s => s.map((x, k) => (k === i ? { ...x, ...patch } : x)));
  const city = (id: string) => terminals.find(t => t.id === id)?.city ?? '?';
  const toggleDay = (d: Weekday) => setDays(x => (x.includes(d) ? x.filter(y => y !== d) : [...x, d]));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const offsets = scheduleOffsets(time, stops);
    if (!offsets) { setError('Durak saatlerini SS:dd bicininde girin.'); return; }
    const fares: TripFareInput[] = pairs
      .filter(([i, j]) => Number(prices[`${i}>${j}`]) > 0)
      .map(([i, j]) => ({ fromSeq: i, toSeq: j, price: Number(prices[`${i}>${j}`]) }));
    const problem = validateFares(stops.length, fares);
    if (problem) { setError(problem); return; }
    setError('');
    await flight('create-schedule', async () => {
      setSaving(true);
      try {
        const r = await partnerBusApi.createSchedule(organizationId, {
          busId, code: code.trim(), currency: 'TRY', weekdays: days, departureTime: time, validFrom,
          validTo: validTo || null, horizonDays: horizon, stops: offsets, fares,
        });
        toast.success(`Plan kaydedildi, ${r.data?.summary.created ?? 0} sefer olusturuldu.`);
        onDone(true);
      } catch (err) {
        setError(busErrorMessage(err, 'Kaydedilemedi'));
      } finally {
        setSaving(false);
      }
    });
  }

  const stopsOk = stops.every((s, i) => s.terminalId && (i === 0 || (s.arrival && s.departure)));
  return (
    <Card className="p-4">
      <form onSubmit={submit} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Otobus">
            <select className={inputClass} value={busId} onChange={e => setBusId(e.target.value)}>
              {buses.map(b => <option key={b.id} value={b.id}>{b.plateNumber} ({b.seatCount} koltuk)</option>)}
            </select>
          </Field>
          <Field label="Plan kodu"><input className={inputClass} value={code} maxLength={40} onChange={e => setCode(e.target.value)} /></Field>
        </div>
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface">Hangi gunler</p>
          <div className="flex flex-wrap gap-3">
            {WEEKDAYS.map(d => (
              <label key={d.value} className="flex items-center gap-1 text-sm text-on-surface">
                <input type="checkbox" checked={days.includes(d.value)} onChange={() => toggleDay(d.value)} /> {d.label}
              </label>
            ))}
          </div>
        </div>
        <Field label="Kalkis saati"><input className={inputClass} type="time" value={time} onChange={e => setTime(e.target.value)} /></Field>
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface">Duraklar (ertesi gune sarkan saatleri isaretleyin)</p>
          {stops.map((s, i) => (
            <div key={i} className="grid gap-2 sm:grid-cols-[1fr_auto_auto_auto] items-end">
              <Field label={`Durak ${i + 1}`}>
                <select className={inputClass} value={s.terminalId} onChange={e => setStop(i, { terminalId: e.target.value })}>
                  <option value="">Secin</option>
                  {terminals.map(t => <option key={t.id} value={t.id}>{t.city} - {t.name}</option>)}
                </select>
              </Field>
              {i === 0 ? <p className="text-xs text-on-surface-variant sm:col-span-2">Ilk durak: kalkis saati yukaridaki saattir.</p> : (
                <>
                  <Field label="Varis">
                    <span className="flex items-center gap-2">
                      <input className={inputClass} type="time" value={s.arrival}
                        onChange={e => setStop(i, { arrival: e.target.value, departure: s.departure || e.target.value })} />
                      <label className="text-xs whitespace-nowrap"><input type="checkbox" checked={s.arrivalNextDay}
                        onChange={e => setStop(i, { arrivalNextDay: e.target.checked })} /> +1 gun</label>
                    </span>
                  </Field>
                  <Field label="Kalkis">
                    <span className="flex items-center gap-2">
                      <input className={inputClass} type="time" value={s.departure} onChange={e => setStop(i, { departure: e.target.value })} />
                      <label className="text-xs whitespace-nowrap"><input type="checkbox" checked={s.departureNextDay}
                        onChange={e => setStop(i, { departureNextDay: e.target.checked })} /> +1 gun</label>
                    </span>
                  </Field>
                </>
              )}
              {stops.length > 2 && i > 0 && <Button type="button" variant="ghost" icon="delete"
                onClick={() => { setStops(x => x.filter((_, k) => k !== i)); setPrices({}); }}>Sil</Button>}
            </div>
          ))}
          {stops.length < 20 && <Button type="button" variant="ghost" icon="add"
            onClick={() => { setStops(x => [...x, emptyStop()]); setPrices({}); }}>Durak ekle</Button>}
        </div>
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface">Fiyat tablosu (TRY). Tum guzergah fiyati zorunlu.</p>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {pairs.map(([i, j]) => (
              <Field key={`${i}>${j}`} label={`${city(stops[i].terminalId)} - ${city(stops[j].terminalId)}${i === 0 && j === stops.length - 1 ? ' (tum guzergah)' : ''}`}>
                <input className={inputClass} type="number" min="0" step="0.01" value={prices[`${i}>${j}`] ?? ''}
                  onChange={e => setPrices(p => ({ ...p, [`${i}>${j}`]: e.target.value }))} />
              </Field>
            ))}
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Gecerli baslangic"><input className={inputClass} type="date" value={validFrom} onChange={e => setValidFrom(e.target.value)} /></Field>
          <Field label="Gecerli bitis (istege bagli)"><input className={inputClass} type="date" value={validTo} onChange={e => setValidTo(e.target.value)} /></Field>
          <Field label="Kac gun ileri">
            <select className={inputClass} value={horizon} onChange={e => setHorizon(Number(e.target.value))}>
              {HORIZONS.map(h => <option key={h} value={h}>{h} gun</option>)}
            </select>
          </Field>
        </div>
        {error && <p className="text-sm text-error">{error}</p>}
        <div className="flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !busId || !code.trim() || !time || days.length === 0 || !validFrom || !stopsOk}>
            {saving ? 'Kaydediliyor' : 'Plani olustur'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function ScheduleSection({ organizationId, buses, terminals, canCreate, onChanged }: {
  organizationId: string; buses: BusUnit[]; terminals: Terminal[]; canCreate: boolean; onChanged: () => void;
}) {
  const toast = useToast();
  const flight = useSingleFlight();
  const [schedules, setSchedules] = useState<TripSchedule[] | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [version, setVersion] = useState(0);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    let alive = true;
    partnerBusApi.schedules(organizationId)
      .then(r => { if (alive) { setSchedules(r.data ?? []); setLoadError(''); } })
      .catch(e => { if (alive) setLoadError(busErrorMessage(e, 'Planlar yuklenemedi')); });
    return () => { alive = false; };
  }, [organizationId, version]);

  const reload = () => { setVersion(v => v + 1); onChanged(); };

  async function toggle(s: TripSchedule) {
    await flight(`toggle-${s.id}`, async () => {
      try {
        await partnerBusApi.setScheduleActive(organizationId, s.id, !s.active);
        reload();
      } catch (e) {
        toast.error(busErrorMessage(e, 'Guncellenemedi'));
      }
    });
  }

  async function applyNow() {
    await flight('apply-schedules', async () => {
      try {
        const r = await partnerBusApi.applySchedules(organizationId);
        toast.success(`${r.data?.created ?? 0} yeni sefer olusturuldu.`);
        reload();
      } catch (e) {
        toast.error(busErrorMessage(e, 'Seferler olusturulamadi'));
      }
    });
  }

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold text-on-surface flex-1">Haftalik sefer planlari</h2>
        {!showForm && schedules && schedules.length > 0 && <Button variant="ghost" icon="sync" onClick={applyNow}>Simdi uygula</Button>}
        {!showForm && <Button icon="add" disabled={!canCreate} onClick={() => setShowForm(true)}>Plan ekle</Button>}
      </div>
      {showForm && (
        <ScheduleForm organizationId={organizationId} buses={buses} terminals={terminals}
          onDone={saved => { setShowForm(false); if (saved) reload(); }} />
      )}
      {loadError && <p className="text-sm text-error">{loadError}</p>}
      {schedules && schedules.length === 0 && !showForm && (
        <p className="text-sm text-on-surface-variant">Henuz plan yok. Plan ekleyin, seferler her gun otomatik acilsin.</p>
      )}
      {schedules?.map(s => (
        <Card key={s.id} className="p-4 space-y-1">
          <div className="flex flex-wrap items-center gap-3">
            <p className="font-semibold text-on-surface flex-1 min-w-0">
              {s.code}: {weekdayLabel(s.weekdays)} {formatClock(`T${s.departureTime}`)}
            </p>
            <span className="text-xs text-outline">{nextText(s)}</span>
            <StatusPill tone={s.active ? 'success' : 'neutral'}>{s.active ? 'Aktif' : 'Pasif'}</StatusPill>
            <Button variant="ghost" onClick={() => toggle(s)}>{s.active ? 'Pasife al' : 'Aktif et'}</Button>
          </div>
          <p className="text-xs text-on-surface-variant">{route(s)} ({s.generatedCount} sefer olusturuldu)</p>
        </Card>
      ))}
    </section>
  );
}
