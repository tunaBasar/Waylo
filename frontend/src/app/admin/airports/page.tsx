'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { adminApi, type AirportRequest } from '@/lib/admin';
import type { Airport } from '@/lib/types';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, inputClass } from '@/components/partner/ui';

const EMPTY = { iataCode: '', name: '', city: '', country: '', timezone: '', latitude: '', longitude: '' };
type Form = typeof EMPTY;

function toRequest(f: Form): AirportRequest {
  return {
    iataCode: f.iataCode.trim().toUpperCase(), name: f.name.trim(), city: f.city.trim(), country: f.country.trim(),
    timezone: f.timezone.trim(),
    latitude: f.latitude.trim() === '' ? null : Number(f.latitude),
    longitude: f.longitude.trim() === '' ? null : Number(f.longitude),
  };
}

export default function AdminAirportsPage() {
  const toast = useToast();
  const [airports, setAirports] = useState<Airport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try { setAirports((await adminApi.airports()).data ?? []); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : 'Havalimanlari yuklenemedi'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }));
  const valid = /^[A-Za-z]{3}$/.test(form.iataCode.trim()) && !!form.name.trim();

  function edit(a: Airport) {
    setEditing(a.id);
    setForm({ iataCode: a.iataCode, name: a.name ?? '', city: a.city ?? '', country: a.country ?? '', timezone: a.timezone ?? '',
      latitude: a.latitude?.toString() ?? '', longitude: a.longitude?.toString() ?? '' });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || saving || !editing) return;
    setSaving(true);
    try {
      if (editing === 'new') await adminApi.createAirport(toRequest(form));
      else await adminApi.updateAirport(editing, toRequest(form));
      toast.success('Havalimani kaydedildi');
      setEditing(null);
      await load();
    } catch (err) { toast.error(err instanceof Error ? err.message : 'Kaydedilemedi'); }
    finally { setSaving(false); }
  }

  async function remove(a: Airport) {
    if (!window.confirm(`${a.iataCode} silinsin mi?`)) return;
    try { await adminApi.deleteAirport(a.id); toast.success('Havalimani silindi'); await load(); }
    catch (err) { toast.error(err instanceof Error ? err.message : 'Silinemedi'); }
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Havalimanlari" subtitle={`${airports.length} kayit`}
        actions={<Button icon="add" onClick={() => { setEditing('new'); setForm(EMPTY); }}>Yeni havalimani</Button>} />
      {editing && (
        <Card className="p-4">
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <Field label="IATA kodu"><input className={inputClass} maxLength={3} value={form.iataCode} onChange={set('iataCode')} /></Field>
            <Field label="Ad"><input className={inputClass} value={form.name} onChange={set('name')} /></Field>
            <Field label="Sehir"><input className={inputClass} value={form.city} onChange={set('city')} /></Field>
            <Field label="Ulke"><input className={inputClass} value={form.country} onChange={set('country')} /></Field>
            <Field label="Saat dilimi" hint="Ornek: Europe/Istanbul"><input className={inputClass} value={form.timezone} onChange={set('timezone')} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Enlem"><input type="number" step="any" min={-90} max={90} className={inputClass} value={form.latitude} onChange={set('latitude')} /></Field>
              <Field label="Boylam"><input type="number" step="any" min={-180} max={180} className={inputClass} value={form.longitude} onChange={set('longitude')} /></Field>
            </div>
            <div className="sm:col-span-2 flex gap-2">
              <Button type="submit" icon="check" disabled={!valid || saving}>{saving ? 'Kaydediliyor' : 'Kaydet'}</Button>
              <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Vazgec</Button>
            </div>
          </form>
        </Card>
      )}
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : airports.length === 0 ? <EmptyState icon="flight_takeoff" title="Havalimani yok" />
        : (
          <div className="space-y-2">
            {airports.map(a => (
              <Card key={a.id} className="p-4 flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface">{a.iataCode} <span className="font-normal text-on-surface-variant">{a.name}</span></p>
                  <p className="text-xs text-on-surface-variant">{[a.city, a.country].filter(Boolean).join(', ')}{a.timezone ? ` · ${a.timezone}` : ''}</p>
                </div>
                <div className="flex gap-2">
                  <Button variant="ghost" icon="edit" onClick={() => edit(a)}>Duzenle</Button>
                  <Button variant="danger" icon="delete" onClick={() => void remove(a)}>Sil</Button>
                </div>
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
