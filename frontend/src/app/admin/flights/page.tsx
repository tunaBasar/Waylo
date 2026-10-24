'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { adminApi, type FlightRequest } from '@/lib/admin';
import type { Flight } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const EMPTY = { flightNumber: '', departureAirportCode: '', arrivalAirportCode: '', airline: '', departureTime: '', arrivalTime: '' };
type Form = typeof EMPTY;

/** <input type="datetime-local"> value for an ISO instant, in the browser's timezone. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function AdminFlightsPage() {
  const toast = useToast();
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try { setFlights((await adminApi.flights()).data ?? []); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : 'Ucuslar yuklenemedi'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }));
  const iata = (v: string) => /^[A-Za-z]{3}$/.test(v.trim());
  const timesOk = !!form.departureTime && !!form.arrivalTime && new Date(form.arrivalTime) > new Date(form.departureTime);
  const valid = !!form.flightNumber.trim() && !!form.airline.trim() && iata(form.departureAirportCode) && iata(form.arrivalAirportCode) && timesOk;

  function edit(f: Flight) {
    setEditing(f.id);
    setForm({ flightNumber: f.flightNumber, departureAirportCode: f.departureAirportCode, arrivalAirportCode: f.arrivalAirportCode,
      airline: f.airline, departureTime: toLocalInput(f.departureTime), arrivalTime: toLocalInput(f.arrivalTime) });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || saving || !editing) return;
    setSaving(true);
    const body: FlightRequest = {
      flightNumber: form.flightNumber.trim().toUpperCase(),
      departureAirportCode: form.departureAirportCode.trim().toUpperCase(),
      arrivalAirportCode: form.arrivalAirportCode.trim().toUpperCase(),
      airline: form.airline.trim(),
      departureTime: new Date(form.departureTime).toISOString(),
      arrivalTime: new Date(form.arrivalTime).toISOString(),
    };
    try {
      if (editing === 'new') await adminApi.createFlight(body);
      else await adminApi.updateFlight(editing, body);
      toast.success('Ucus kaydedildi');
      setEditing(null);
      await load();
    } catch (err) { toast.error(err instanceof Error ? err.message : 'Kaydedilemedi'); }
    finally { setSaving(false); }
  }

  async function cancel(f: Flight) {
    if (!window.confirm(`${f.flightNumber} iptal edilsin mi?`)) return;
    try { await adminApi.cancelFlight(f.id); toast.success('Ucus iptal edildi'); await load(); }
    catch (err) { toast.error(err instanceof Error ? err.message : 'Iptal edilemedi'); }
  }

  async function remove(f: Flight) {
    if (!window.confirm(`${f.flightNumber} silinsin mi?`)) return;
    try { await adminApi.deleteFlight(f.id); toast.success('Ucus silindi'); await load(); }
    catch (err) { toast.error(err instanceof Error ? err.message : 'Silinemedi'); }
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Ucuslar" subtitle={`${flights.length} kayit`}
        actions={<Button icon="add" onClick={() => { setEditing('new'); setForm(EMPTY); }}>Yeni ucus</Button>} />
      {editing && (
        <Card className="p-4">
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <Field label="Ucus no"><input className={inputClass} maxLength={10} value={form.flightNumber} onChange={set('flightNumber')} /></Field>
            <Field label="Havayolu"><input className={inputClass} maxLength={100} value={form.airline} onChange={set('airline')} /></Field>
            <Field label="Kalkis (IATA)"><input className={inputClass} maxLength={3} value={form.departureAirportCode} onChange={set('departureAirportCode')} /></Field>
            <Field label="Varis (IATA)"><input className={inputClass} maxLength={3} value={form.arrivalAirportCode} onChange={set('arrivalAirportCode')} /></Field>
            <Field label="Kalkis zamani"><input type="datetime-local" className={inputClass} value={form.departureTime} onChange={set('departureTime')} /></Field>
            <Field label="Varis zamani" error={form.arrivalTime && !timesOk ? 'Varis, kalkistan sonra olmali' : null}>
              <input type="datetime-local" className={inputClass} value={form.arrivalTime} onChange={set('arrivalTime')} />
            </Field>
            <div className="sm:col-span-2 flex gap-2">
              <Button type="submit" icon="check" disabled={!valid || saving}>{saving ? 'Kaydediliyor' : 'Kaydet'}</Button>
              <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Vazgec</Button>
            </div>
          </form>
        </Card>
      )}
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : flights.length === 0 ? <EmptyState icon="flight" title="Ucus yok" />
        : (
          <div className="space-y-2">
            {flights.map(f => (
              <Card key={f.id} className="p-4 flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface">
                    {f.flightNumber} <span className="font-normal text-on-surface-variant">{f.airline}</span>{' '}
                    <StatusPill tone={f.status === 'SCHEDULED' ? 'success' : 'error'}>{f.status === 'SCHEDULED' ? 'Planlandi' : 'Iptal'}</StatusPill>
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    {f.departureAirportCode} → {f.arrivalAirportCode} · {formatDate(f.departureTime)} - {formatDate(f.arrivalTime)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button variant="ghost" icon="edit" onClick={() => edit(f)}>Duzenle</Button>
                  {f.status === 'SCHEDULED' && <Button variant="ghost" icon="block" onClick={() => void cancel(f)}>Iptal et</Button>}
                  <Button variant="danger" icon="delete" onClick={() => void remove(f)}>Sil</Button>
                </div>
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
