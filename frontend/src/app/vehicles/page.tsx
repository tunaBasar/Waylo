'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { AvailableVehicle } from '@/lib/types';
import { formatCurrency } from '@/lib/utils';

const typeLabels: Record<string, string> = { ECONOMY: 'Ekonomi', COMPACT: 'Kompakt', MIDSIZE: 'Orta', FULLSIZE: 'Buyuk', SUV: 'SUV', LUXURY: 'Luks', VAN: 'Van' };
const errorLabels: Record<string, string> = { VEHICLE_NOT_AVAILABLE: 'Bu arac sectiginiz tarihlerde musait degil.', VEHICLE_NOT_RENTABLE: 'Bu arac su anda kiralanamaz.' };
const daysBetween = (from: string, to: string) => Math.ceil((new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / 86400000);

export default function VehiclesPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [vehicles, setVehicles] = useState<AvailableVehicle[]>([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [location, setLocation] = useState('');
  const [vehicleType, setVehicleType] = useState('');
  const [loading, setLoading] = useState(false);
  const [renting, setRenting] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function searchVehicles(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (!from || !to || daysBetween(from, to) < 1 || daysBetween(from, to) > 30) { setError('Tarih araligi 1 ile 30 gun arasinda olmali.'); return; }
    setLoading(true); setError('');
    try { const params = new URLSearchParams({ from, to }); if (location) params.set('location', location); if (vehicleType) params.set('vehicleType', vehicleType); const res = await api.get<AvailableVehicle[]>(`/api/vehicles/available?${params}`); setVehicles(res.data); } catch { setError('Musait araclar yuklenemedi.'); } finally { setLoading(false); }
  }

  async function rent(vehicle: AvailableVehicle) {
    if (authLoading || !user) { router.push('/login'); return; }
    setRenting(vehicle.id); setError(''); setMessage('');
    try { await api.post('/api/rentals', { vehicleId: vehicle.id, pickupDate: from, returnDate: to }); setMessage('Arac rezervasyonunuz olusturuldu.'); await searchVehicles(); } catch (cause) { const apiError = cause as ApiError; setError(errorLabels[apiError.code || ''] || (apiError.status === 400 ? 'Tarih bilgilerini kontrol edin.' : 'Arac kiralanamadi.')); } finally { setRenting(null); }
  }

  const days = from && to ? daysBetween(from, to) : 0;
  return <div className="space-y-space-md"><div className="flex items-center gap-space-xs"><h2 className="text-xl font-semibold text-on-surface tracking-tight">Araclar</h2><span className="bg-tertiary-fixed text-on-tertiary-fixed-variant text-xs font-semibold px-2.5 py-0.5 rounded-full">{vehicles.length} Arac</span></div><form onSubmit={searchVehicles} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-5"><input type="date" value={from} onChange={e => setFrom(e.target.value)} className="rounded-lg bg-surface-container px-3 py-2 text-sm" /><input type="date" value={to} onChange={e => setTo(e.target.value)} className="rounded-lg bg-surface-container px-3 py-2 text-sm" /><input value={location} onChange={e => setLocation(e.target.value)} placeholder="Lokasyon (opsiyonel)" className="rounded-lg bg-surface-container px-3 py-2 text-sm" /><select value={vehicleType} onChange={e => setVehicleType(e.target.value)} className="rounded-lg bg-surface-container px-3 py-2 text-sm"><option value="">Tum tipler</option>{Object.entries(typeLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select><button className="rounded-lg bg-tertiary px-4 py-2 text-sm font-medium text-on-tertiary">Arac Ara</button></form>{(error || message) && <p className={`rounded-lg px-3 py-2 text-sm ${error ? 'bg-error-container text-on-error-container' : 'bg-secondary-container text-on-secondary-container'}`}>{error || message}</p>}{loading ? <p className="py-12 text-center text-sm text-on-surface-variant">Araclar yukleniyor...</p> : vehicles.length === 0 ? <p className="py-12 text-center text-sm text-on-surface-variant">Musait arac bulunamadi</p> : <div className="space-y-space-sm">{vehicles.map(vehicle => <div key={vehicle.id} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm"><div className="flex items-center justify-between"><div><p className="text-base font-bold text-on-surface">{vehicle.brand} {vehicle.model}</p><p className="text-xs text-on-surface-variant">{vehicle.plateNumber} · {typeLabels[vehicle.vehicleType] || vehicle.vehicleType} · {vehicle.location}</p></div><p className="text-lg font-bold text-tertiary">{formatCurrency(vehicle.dailyRate, vehicle.currency)}<span className="text-xs font-normal text-on-surface-variant"> /gun</span></p></div><div className="mt-3 flex items-center justify-between"><span className="text-xs text-on-surface-variant">{vehicle.seatCount} koltuk{days > 0 && ` · Toplam: ${formatCurrency(vehicle.dailyRate * days, vehicle.currency)} (${days} gun)`}</span><button onClick={() => rent(vehicle)} disabled={renting === vehicle.id} className="rounded-lg bg-tertiary px-4 py-2 text-xs font-medium text-on-tertiary disabled:opacity-50">{renting === vehicle.id ? 'Kiralanıyor...' : 'Kirala'}</button></div></div>)}</div>}</div>;
}
