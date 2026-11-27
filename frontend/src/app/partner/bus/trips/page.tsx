'use client';

/** Trips (seferler): stops with times and a fare table per (from stop, to stop) pair. */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  busErrorMessage, partnerBusApi, formatTripTime, formatMoney, tripBadge, validateFares,
  type BusUnit, type Terminal, type Trip, type TripFareInput,
} from '@/lib/bus';
import { useToast } from '@/components/Toast';
import ScheduleSection from './ScheduleSection';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

interface StopDraft { terminalId: string; arrivalAt: string; departureAt: string }
const emptyStop = (): StopDraft => ({ terminalId: '', arrivalAt: '', departureAt: '' });
const withSeconds = (v: string) => (v.length === 16 ? v + ':00' : v);

function TripForm({ organizationId, buses, terminals, onDone }: {
  organizationId: string; buses: BusUnit[]; terminals: Terminal[]; onDone: (saved: boolean) => void;
}) {
  const [busId, setBusId] = useState(buses[0]?.id ?? '');
  const [code, setCode] = useState('');
  const [stops, setStops] = useState<StopDraft[]>([emptyStop(), emptyStop()]);
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  const pairs: [number, number][] = [];
  for (let i = 0; i < stops.length; i++) for (let j = i + 1; j < stops.length; j++) pairs.push([i, j]);
  const setStop = (i: number, patch: Partial<StopDraft>) => setStops(s => s.map((x, k) => (k === i ? { ...x, ...patch } : x)));
  const city = (id: string) => terminals.find(t => t.id === id)?.city ?? `Durak ${id ? '?' : '-'}`;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (inFlight.current) return;
    const fares: TripFareInput[] = pairs
      .filter(([i, j]) => Number(prices[`${i}>${j}`]) > 0)
      .map(([i, j]) => ({ fromSeq: i, toSeq: j, price: Number(prices[`${i}>${j}`]) }));
    const problem = validateFares(stops.length, fares);
    if (problem) { setError(problem); return; }
    inFlight.current = true;
    setSaving(true);
    setError('');
    try {
      await partnerBusApi.createTrip(organizationId, {
        busId, code: code.trim(), currency: 'TRY', fares,
        stops: stops.map(s => ({ terminalId: s.terminalId, arrivalAt: withSeconds(s.arrivalAt), departureAt: withSeconds(s.departureAt) })),
      });
      onDone(true);
    } catch (err) {
      setError(busErrorMessage(err, 'Kaydedilemedi'));
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  const stopsOk = stops.every(s => s.terminalId && s.arrivalAt && s.departureAt);
  return (
    <Card className="p-4">
      <form onSubmit={submit} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Otobus">
            <select className={inputClass} value={busId} onChange={e => setBusId(e.target.value)}>
              {buses.map(b => <option key={b.id} value={b.id}>{b.plateNumber} ({b.seatCount} koltuk)</option>)}
            </select>
          </Field>
          <Field label="Sefer kodu"><input className={inputClass} value={code} maxLength={40} onChange={e => setCode(e.target.value)} /></Field>
        </div>
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface">Duraklar (ilk durak kalkis, son durak varis)</p>
          {stops.map((s, i) => (
            <div key={i} className="grid gap-2 sm:grid-cols-[1fr_1fr_1fr_auto] items-end">
              <Field label={`Durak ${i + 1}`}>
                <select className={inputClass} value={s.terminalId} onChange={e => setStop(i, { terminalId: e.target.value })}>
                  <option value="">Secin</option>
                  {terminals.map(t => <option key={t.id} value={t.id}>{t.city} - {t.name}</option>)}
                </select>
              </Field>
              <Field label="Varis"><input className={inputClass} type="datetime-local" value={s.arrivalAt}
                onChange={e => setStop(i, { arrivalAt: e.target.value, departureAt: s.departureAt || e.target.value })} /></Field>
              <Field label="Kalkis"><input className={inputClass} type="datetime-local" value={s.departureAt}
                onChange={e => setStop(i, { departureAt: e.target.value })} /></Field>
              {stops.length > 2 && <Button type="button" variant="ghost" icon="delete"
                onClick={() => { setStops(x => x.filter((_, k) => k !== i)); setPrices({}); }}>Sil</Button>}
            </div>
          ))}
          {stops.length < 20 && <Button type="button" variant="ghost" icon="add"
            onClick={() => { setStops(x => [...x, emptyStop()]); setPrices({}); }}>Durak ekle</Button>}
        </div>
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface">Fiyat tablosu (TRY). Tum guzergah fiyati zorunlu, bos birakilan ikili satilmaz.</p>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {pairs.map(([i, j]) => (
              <Field key={`${i}>${j}`} label={`${city(stops[i].terminalId)} - ${city(stops[j].terminalId)}${i === 0 && j === stops.length - 1 ? ' (tum guzergah)' : ''}`}>
                <input className={inputClass} type="number" min="0" step="0.01" value={prices[`${i}>${j}`] ?? ''}
                  onChange={e => setPrices(p => ({ ...p, [`${i}>${j}`]: e.target.value }))} />
              </Field>
            ))}
          </div>
        </div>
        {error && <p className="text-sm text-error">{error}</p>}
        <div className="flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !busId || !code.trim() || !stopsOk}>{saving ? 'Kaydediliyor' : 'Seferi olustur'}</Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function TripsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [trips, setTrips] = useState<Trip[]>([]);
  const [buses, setBuses] = useState<BusUnit[]>([]);
  const [terminals, setTerminals] = useState<Terminal[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [version, setVersion] = useState(0);
  // Sellability for the trip badge: company status plus every bus (inactive ones too, a trip may still point at one).
  const [companySellable, setCompanySellable] = useState<boolean | null>(null);
  const [busSellable, setBusSellable] = useState<Record<string, boolean>>({});

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([partnerBusApi.trips(organizationId), partnerBusApi.buses(organizationId), partnerBusApi.terminals(organizationId)])
      .then(([t, b, term]) => {
        if (!alive) return;
        setTrips(t.data ?? []);
        setBuses((b.data ?? []).filter(x => x.active));
        setBusSellable(Object.fromEntries((b.data ?? []).map(x => [x.id, x.sellable && x.active])));
        setTerminals(term.data ?? []);
        setError(null);
      })
      .catch(e => { if (alive) setError(busErrorMessage(e, 'Yuklenemedi')); })
      .finally(() => { if (alive) setLoading(false); });
    partnerBusApi.status(organizationId)
      .then(r => { if (alive) setCompanySellable(r.data?.sellable ?? null); })
      .catch(() => { if (alive) setCompanySellable(null); });
    return () => { alive = false; };
  }, [organizationId, version]);

  async function cancel(t: Trip) {
    if (!organizationId || !window.confirm(`${t.code} seferi iptal edilsin mi?`)) return;
    try {
      await partnerBusApi.cancelTrip(organizationId, t.id);
      setVersion(v => v + 1);
    } catch (e) {
      toast.error(busErrorMessage(e, 'Iptal edilemedi'));
    }
  }

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;
  const canCreate = buses.length > 0 && terminals.length >= 2;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Seferler" subtitle="Guzergah, saatler ve fiyat tablosu"
        actions={!showForm && <Button icon="add" disabled={!canCreate} onClick={() => setShowForm(true)}>Sefer ekle</Button>} />
      {!canCreate && <p className="text-sm text-on-surface-variant">Sefer eklemek icin once aktif bir otobus ekleyin. Duraklar platform tarafindan tanimlanir.</p>}
      {organizationId && (
        <ScheduleSection organizationId={organizationId} buses={buses} terminals={terminals} canCreate={canCreate}
          onChanged={() => setVersion(v => v + 1)} />
      )}
      {showForm && organizationId && (
        <TripForm organizationId={organizationId} buses={buses} terminals={terminals}
          onDone={saved => { setShowForm(false); if (saved) setVersion(v => v + 1); }} />
      )}
      {trips.length === 0 ? (
        !showForm && <EmptyState icon="route" title="Henuz sefer yok" description="Ilk seferinizi olusturun." />
      ) : (
        <div className="space-y-3">
          {trips.map(t => {
            const badge = tripBadge(t.status, companySellable, busSellable[t.busId] ?? null);
            return (
            <Card key={t.id} className="p-4 space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                <p className="font-semibold text-on-surface flex-1 min-w-0">
                  {t.code}: {t.stops[0]?.city} - {t.stops[t.stops.length - 1]?.city}
                  <span className="text-on-surface-variant font-normal"> {formatTripTime(t.stops[0]?.departureAt)}</span>
                </p>
                <span className="text-xs text-outline">{t.soldSeats}/{t.seatCount} koltuk dolu</span>
                <StatusPill tone={badge.tone}>{badge.label}</StatusPill>
                {t.status === 'PUBLISHED' && <Button variant="ghost" icon="cancel" onClick={() => cancel(t)}>Iptal et</Button>}
              </div>
              {t.scheduleId && <p className="text-xs text-on-surface-variant">Plan: {t.code}</p>}
              {badge.hint && <p className="text-xs text-on-surface-variant">{badge.hint}</p>}
              <p className="text-xs text-on-surface-variant">
                {t.stops.map(s => `${s.city} ${formatTripTime(s.departureAt).slice(11)}`).join(' > ')}
              </p>
              <p className="text-xs text-on-surface-variant">
                {t.fares.map(f => `${t.stops[f.fromSeq]?.city}-${t.stops[f.toSeq]?.city}: ${formatMoney(f.price, t.currency)}`).join(', ')}
              </p>
            </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
