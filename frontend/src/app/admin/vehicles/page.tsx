'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { adminApi, type VehicleRequest } from '@/lib/admin';
import type { Vehicle } from '@/lib/types';
import { formatCurrency } from '@/lib/utils';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const TYPES: Vehicle['vehicleType'][] = ['ECONOMY', 'COMPACT', 'MIDSIZE', 'FULLSIZE', 'SUV', 'LUXURY', 'VAN'];
const EMPTY = { plateNumber: '', brand: '', model: '', year: String(new Date().getFullYear()), vehicleType: 'ECONOMY' as Vehicle['vehicleType'],
  color: '', seatCount: '5', dailyRate: '', currency: 'TRY', location: '', mileage: '' };
type Form = typeof EMPTY;

export default function AdminVehiclesPage() {
  const toast = useToast();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try { setVehicles((await adminApi.vehicles()).data ?? []); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : 'Araclar yuklenemedi'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm(f => ({ ...f, [k]: e.target.value }));
  const valid = !!form.plateNumber.trim() && !!form.brand.trim() && !!form.model.trim() && !!form.location.trim()
    && Number(form.year) >= 1990 && Number(form.seatCount) >= 1 && Number(form.seatCount) <= 60
    && Number(form.dailyRate) >= 0.01 && /^[A-Z]{3}$/.test(form.currency.trim().toUpperCase());

  function edit(v: Vehicle) {
    setEditing(v.id);
    setForm({ plateNumber: v.plateNumber, brand: v.brand, model: v.model, year: String(v.year), vehicleType: v.vehicleType, color: v.color ?? '',
      seatCount: String(v.seatCount), dailyRate: String(v.dailyRate), currency: v.currency, location: v.location, mileage: v.mileage?.toString() ?? '' });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || saving || !editing) return;
    setSaving(true);
    const body: VehicleRequest = {
      plateNumber: form.plateNumber.trim(), brand: form.brand.trim(), model: form.model.trim(), year: Number(form.year),
      vehicleType: form.vehicleType, color: form.color.trim() || null, seatCount: Number(form.seatCount),
      dailyRate: Number(form.dailyRate), currency: form.currency.trim().toUpperCase(), location: form.location.trim(),
      mileage: form.mileage.trim() === '' ? null : Number(form.mileage),
    };
    try {
      if (editing === 'new') await adminApi.createVehicle(body);
      else await adminApi.updateVehicle(editing, body);
      toast.success('Arac kaydedildi');
      setEditing(null);
      await load();
    } catch (err) { toast.error(err instanceof Error ? err.message : 'Kaydedilemedi'); }
    finally { setSaving(false); }
  }

  async function act(action: () => Promise<unknown>, done: string, fail: string) {
    try { await action(); toast.success(done); await load(); }
    catch (err) { toast.error(err instanceof Error ? err.message : fail); }
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Araclar" subtitle={`${vehicles.length} kayit`}
        actions={<Button icon="add" onClick={() => { setEditing('new'); setForm(EMPTY); }}>Yeni arac</Button>} />
      {editing && (
        <Card className="p-4">
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Plaka"><input className={inputClass} maxLength={20} value={form.plateNumber} onChange={set('plateNumber')} /></Field>
            <Field label="Marka"><input className={inputClass} value={form.brand} onChange={set('brand')} /></Field>
            <Field label="Model"><input className={inputClass} value={form.model} onChange={set('model')} /></Field>
            <Field label="Yil"><input type="number" min={1990} className={inputClass} value={form.year} onChange={set('year')} /></Field>
            <Field label="Tip">
              <select className={inputClass} value={form.vehicleType} onChange={set('vehicleType')}>{TYPES.map(t => <option key={t}>{t}</option>)}</select>
            </Field>
            <Field label="Renk"><input className={inputClass} value={form.color} onChange={set('color')} /></Field>
            <Field label="Koltuk"><input type="number" min={1} max={60} className={inputClass} value={form.seatCount} onChange={set('seatCount')} /></Field>
            <Field label="Gunluk ucret"><input type="number" min={0.01} step="0.01" className={inputClass} value={form.dailyRate} onChange={set('dailyRate')} /></Field>
            <Field label="Para birimi"><input className={inputClass} maxLength={3} value={form.currency} onChange={set('currency')} /></Field>
            <Field label="Lokasyon"><input className={inputClass} value={form.location} onChange={set('location')} /></Field>
            <Field label="Kilometre"><input type="number" min={0} className={inputClass} value={form.mileage} onChange={set('mileage')} /></Field>
            <div className="sm:col-span-2 lg:col-span-3 flex gap-2">
              <Button type="submit" icon="check" disabled={!valid || saving}>{saving ? 'Kaydediliyor' : 'Kaydet'}</Button>
              <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Vazgec</Button>
            </div>
          </form>
        </Card>
      )}
      {loading ? <LoadingRows /> : error ? <EmptyState icon="error" title="Yuklenemedi" description={error} />
        : vehicles.length === 0 ? <EmptyState icon="directions_car" title="Arac yok" />
        : (
          <div className="space-y-2">
            {vehicles.map(v => (
              <Card key={v.id} className="p-4 flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface">
                    {v.brand} {v.model} <span className="font-normal text-on-surface-variant">{v.plateNumber}</span>{' '}
                    <StatusPill tone={v.status === 'AVAILABLE' ? 'success' : 'warning'}>{v.status === 'AVAILABLE' ? 'Musait' : 'Bakimda'}</StatusPill>{' '}
                    {!v.active && <StatusPill tone="neutral">Pasif</StatusPill>}
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    {v.vehicleType} · {v.seatCount} koltuk · {v.location} · {formatCurrency(v.dailyRate, v.currency)}/gun
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="ghost" icon="edit" onClick={() => edit(v)}>Duzenle</Button>
                  <Button variant="ghost" icon="build" onClick={() => void act(
                    () => adminApi.setVehicleStatus(v.id, v.status === 'AVAILABLE' ? 'MAINTENANCE' : 'AVAILABLE'),
                    'Durum guncellendi', 'Durum guncellenemedi')}>
                    {v.status === 'AVAILABLE' ? 'Bakima al' : 'Musait yap'}
                  </Button>
                  <Button variant="ghost" icon={v.active ? 'visibility_off' : 'visibility'} onClick={() => void act(
                    () => v.active ? adminApi.deactivateVehicle(v.id) : adminApi.activateVehicle(v.id),
                    v.active ? 'Arac pasife alindi' : 'Arac aktif edildi', 'Islem basarisiz')}>
                    {v.active ? 'Pasife al' : 'Aktif et'}
                  </Button>
                  <Button variant="danger" icon="delete" onClick={() => {
                    if (window.confirm(`${v.plateNumber} silinsin mi?`)) void act(() => adminApi.deleteVehicle(v.id), 'Arac silindi', 'Silinemedi');
                  }}>Sil</Button>
                </div>
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
