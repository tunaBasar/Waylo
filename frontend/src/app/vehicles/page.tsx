'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Vehicle } from '@/lib/types';

const typeLabels: Record<string, string> = {
  ECONOMY: 'Ekonomi', COMPACT: 'Kompakt', MIDSIZE: 'Orta', FULLSIZE: 'Buyuk', SUV: 'SUV', LUXURY: 'Luks', VAN: 'Van',
};
const statusLabels: Record<string, { label: string; class: string }> = {
  AVAILABLE: { label: 'Musait', class: 'bg-secondary-container text-on-secondary-container' },
  RESERVED: { label: 'Rezerve', class: 'bg-primary-fixed text-primary' },
  RENTED: { label: 'Kirada', class: 'bg-tertiary-fixed text-on-tertiary-fixed-variant' },
  MAINTENANCE: { label: 'Bakim', class: 'bg-error-container text-on-error-container' },
};

export default function VehiclesPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    api.get<Vehicle[]>('/api/vehicles').then(r => { setVehicles(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = vehicles.filter(v =>
    !search || v.brand?.toLowerCase().includes(search.toLowerCase()) ||
    v.model?.toLowerCase().includes(search.toLowerCase()) ||
    v.plateNumber?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Araclar</h2>
          <span className="bg-tertiary-fixed text-on-tertiary-fixed-variant text-xs font-semibold px-2.5 py-0.5 rounded-full">{vehicles.length} Arac</span>
        </div>
        <button className="bg-tertiary hover:bg-tertiary-container text-on-tertiary text-sm font-medium px-3.5 py-2 rounded-lg flex items-center gap-1 shadow-md active:scale-95 transition-all">
          <span className="material-symbols-outlined text-[18px]">add</span> Yeni Arac
        </button>
      </div>

      <div className="relative shadow-sm rounded-xl bg-surface-container-lowest">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
        <input value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none"
          placeholder="Marka, model veya plaka ara..." />
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">directions_car</span><p className="text-on-surface-variant text-sm">Arac bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(v => {
            const st = statusLabels[v.status] || statusLabels.AVAILABLE;
            return (
              <div key={v.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-10 h-10 rounded-xl bg-tertiary-fixed flex items-center justify-center">
                      <span className="material-symbols-outlined text-tertiary text-xl">directions_car</span>
                    </div>
                    <div>
                      <p className="text-base font-bold text-on-surface">{v.brand} {v.model}</p>
                      <p className="text-xs text-on-surface-variant">{v.plateNumber} · {v.year} · {typeLabels[v.vehicleType] || v.vehicleType}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${st.class}`}>{st.label}</span>
                </div>
                <div className="flex items-center gap-4 text-xs text-on-surface-variant mb-2">
                  <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">airline_seat_recline_normal</span> {v.seatCount} Koltuk</span>
                  <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">palette</span> {v.color || '-'}</span>
                  <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">location_on</span> {v.location}</span>
                </div>
                <div className="flex items-center justify-between">
                  <p className="text-lg font-bold text-tertiary">₺{v.dailyRate.toLocaleString('tr-TR')}<span className="text-xs font-normal text-on-surface-variant"> /gun</span></p>
                  <button className="px-4 py-2 rounded-lg bg-tertiary text-on-tertiary text-xs font-medium shadow-sm hover:bg-tertiary-container active:scale-95 transition-all">Yonet</button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
