'use client';

import { useEffect, useState } from 'react';
import { ApiError, api } from '@/lib/api';
import type { Rental } from '@/lib/types';

const statusLabels: Record<Rental['status'], string> = { RESERVED: 'Rezerve', CANCELLED: 'Iptal edildi' };
const canCancel = (rental: Rental) => rental.status === 'RESERVED' && new Date(`${rental.pickupDate}T00:00:00`).getTime() > Date.now();

export default function RentalsPage() {
  const [rentals, setRentals] = useState<Rental[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState<string | null>(null);

  async function load() { try { const res = await api.get<Rental[]>('/api/rentals/me'); setRentals(res.data); } catch { setError('Kiralama kayitlari yuklenemedi.'); } finally { setLoading(false); } }
  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, []);
  async function cancel(id: string) { setCancelling(id); setError(''); try { await api.post(`/api/rentals/${id}/cancel`, {}); await load(); } catch (cause) { const apiError = cause as ApiError; setError(apiError.message || 'Kiralama iptal edilemedi.'); } finally { setCancelling(null); } }

  return <div className="space-y-space-md"><h2 className="text-xl font-semibold text-on-surface tracking-tight">Kiralama Rezervasyonlarim</h2>{error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}{loading ? <p className="py-12 text-center text-sm text-on-surface-variant">Kiralama kayitlari yukleniyor...</p> : rentals.length === 0 ? <p className="py-12 text-center text-sm text-on-surface-variant">Kiralama rezervasyonunuz bulunmuyor.</p> : <div className="space-y-space-sm">{rentals.map(rental => <div key={rental.id} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm"><div className="flex items-center justify-between"><div><p className="text-sm font-semibold text-on-surface">{rental.pickupDate} - {rental.returnDate}</p><p className="text-xs text-on-surface-variant">Arac: {rental.vehicleId}</p></div><span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${rental.status === 'RESERVED' ? 'bg-primary-fixed text-primary' : 'bg-surface-container text-on-surface-variant'}`}>{statusLabels[rental.status]}</span></div><div className="mt-3 flex items-center justify-between"><p className="text-sm font-bold text-primary">₺{rental.totalPrice.toLocaleString('tr-TR')}</p>{canCancel(rental) && <button onClick={() => cancel(rental.id)} disabled={cancelling === rental.id} className="rounded-lg bg-error-container px-3 py-2 text-xs font-medium text-error disabled:opacity-50">{cancelling === rental.id ? 'Iptal ediliyor...' : 'Iptal et'}</button>}</div></div>)}</div>}</div>;
}
