'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { PackageBooking } from '@/lib/types';

const statusConfig: Record<string, { label: string; class: string; icon: string }> = {
  CONFIRMED: { label: 'Onaylandi', class: 'bg-secondary-container text-on-secondary-container', icon: 'check_circle' },
  PENDING: { label: 'Beklemede', class: 'bg-primary-fixed text-primary', icon: 'schedule' },
  CANCELLED: { label: 'Iptal', class: 'bg-error-container text-on-error-container', icon: 'cancel' },
  FAILED: { label: 'Basarisiz', class: 'bg-error-container text-on-error-container', icon: 'error' },
  PARTIALLY_CONFIRMED: { label: 'Kismi Onay', class: 'bg-tertiary-fixed text-on-tertiary-fixed-variant', icon: 'pending' },
};

export default function BookingsPage() {
  const [bookings, setBookings] = useState<PackageBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  useEffect(() => {
    api.get<PackageBooking[]>('/api/bookings').then(r => { setBookings(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = bookings.filter(b => {
    const matchSearch = !search || b.id.toLowerCase().includes(search.toLowerCase()) || b.customerId?.toLowerCase().includes(search.toLowerCase());
    const matchStatus = !statusFilter || b.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const stats = {
    total: bookings.length,
    confirmed: bookings.filter(b => b.status === 'CONFIRMED').length,
    pending: bookings.filter(b => b.status === 'PENDING').length,
    cancelled: bookings.filter(b => b.status === 'CANCELLED').length,
  };

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Rezervasyonlar</h2>
          <span className="bg-primary-fixed text-primary text-xs font-semibold px-2.5 py-0.5 rounded-full">{stats.total}</span>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-space-xs">
        {[
          { label: 'Onaylanan', value: stats.confirmed, icon: 'check_circle', color: 'text-secondary' },
          { label: 'Bekleyen', value: stats.pending, icon: 'schedule', color: 'text-primary' },
          { label: 'Iptal', value: stats.cancelled, icon: 'cancel', color: 'text-error' },
        ].map(s => (
          <div key={s.label} className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
            <span className={`material-symbols-outlined ${s.color} text-xl mb-1 block`}>{s.icon}</span>
            <p className="text-lg font-bold text-on-surface">{s.value}</p>
            <p className="text-[10px] text-on-surface-variant">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Search */}
      <div className="relative shadow-sm rounded-xl bg-surface-container-lowest">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
        <input value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none"
          placeholder="Rezervasyon ID veya musteri ara..." />
      </div>

      {/* Status Filters */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
        <button onClick={() => setStatusFilter(null)}
          className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-colors ${!statusFilter ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>
          Tumu
        </button>
        {Object.entries(statusConfig).map(([key, cfg]) => (
          <button key={key} onClick={() => setStatusFilter(statusFilter === key ? null : key)}
            className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-colors ${statusFilter === key ? cfg.class : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>
            {cfg.label}
          </button>
        ))}
      </div>

      {/* Bookings list */}
      {loading ? (
        <div className="space-y-3">{[1,2,3,4].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-2/3"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">inventory_2</span><p className="text-on-surface-variant text-sm">Rezervasyon bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(b => {
            const sc = statusConfig[b.status] || statusConfig.PENDING;
            const services = [b.flightId && 'Ucus', b.hotelReservationId && 'Otel', b.vehicleReservationId && 'Arac'].filter(Boolean);
            return (
              <div key={b.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 rounded-lg bg-primary-fixed flex items-center justify-center">
                      <span className="material-symbols-outlined text-primary text-lg">inventory_2</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold text-on-surface">#{b.id.slice(0, 8)}</p>
                      <p className="text-[10px] text-on-surface-variant">{new Date(b.createdAt).toLocaleDateString('tr-TR')}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${sc.class}`}>{sc.label}</span>
                </div>
                <div className="flex items-center gap-1.5 mb-2">
                  {services.map(s => (
                    <span key={s} className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant">{s}</span>
                  ))}
                </div>
                <div className="flex items-center justify-between">
                  <p className="text-lg font-bold text-primary">₺{(b.totalPrice || 0).toLocaleString('tr-TR')}</p>
                  <button className="px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-medium shadow-sm active:scale-95 transition-all">Detay</button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
