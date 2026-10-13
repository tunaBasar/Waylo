'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Flight } from '@/lib/types';

const statusMap: Record<string, { label: string; class: string }> = {
  SCHEDULED: { label: 'PLANLANDI', class: 'text-primary bg-primary-fixed' },
  ACTIVE: { label: 'AKTIF', class: 'text-secondary bg-secondary-container' },
  COMPLETED: { label: 'TAMAMLANDI', class: 'text-on-surface-variant bg-surface-container-high' },
  CANCELLED: { label: 'IPTAL EDILDI', class: 'text-error bg-error-container' },
  DELAYED: { label: 'GECIKTI', class: 'text-on-tertiary-fixed-variant bg-tertiary-fixed' },
};

export default function FlightsPage() {
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    api.get<Flight[]>('/api/flights').then(res => {
      setFlights(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const filtered = flights.filter(f => {
    const matchSearch = !search ||
      f.flightNumber?.toLowerCase().includes(search.toLowerCase()) ||
      f.airline?.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === 'all' || f.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const statusCounts = {
    all: flights.length,
    SCHEDULED: flights.filter(f => f.status === 'SCHEDULED').length,
    ACTIVE: flights.filter(f => f.status === 'ACTIVE').length,
    CANCELLED: flights.filter(f => f.status === 'CANCELLED').length,
  };

  return (
    <div className="space-y-space-md">
      {/* Header */}
      <div className="flex items-center justify-between gap-space-xs">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Ucuslar</h2>
          <span className="bg-primary-fixed text-on-primary-fixed-variant text-xs font-semibold px-2.5 py-0.5 rounded-full shadow-sm">
            {flights.length} Ucus
          </span>
        </div>
        <button onClick={() => setShowModal(true)}
          className="bg-primary hover:bg-primary-container text-on-primary text-sm font-medium px-3.5 py-2 rounded-lg flex items-center gap-1 shadow-md active:scale-95 transition-all">
          <span className="material-symbols-outlined text-[18px]">add</span>
          <span>Yeni Ucus</span>
        </button>
      </div>

      {/* Search */}
      <div className="relative w-full shadow-sm rounded-xl bg-surface-container-lowest">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
        <input
          value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none"
          placeholder="Ucus No veya Havayolu ara..."
        />
      </div>

      {/* Status Filters */}
      <div className="flex items-center gap-space-xs overflow-x-auto pb-1 no-scrollbar">
        {[
          { key: 'all', label: `Tumu (${statusCounts.all})` },
          { key: 'SCHEDULED', label: `PLANLANDI (${statusCounts.SCHEDULED})` },
          { key: 'ACTIVE', label: `AKTIF` },
          { key: 'CANCELLED', label: `IPTAL` },
        ].map(f => (
          <button key={f.key} onClick={() => setStatusFilter(f.key)}
            className={`text-xs font-medium px-3.5 py-1.5 rounded-full whitespace-nowrap shadow-sm transition-colors shrink-0 ${
              statusFilter === f.key
                ? 'bg-primary text-on-primary'
                : 'bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
            }`}>
            {f.label}
          </button>
        ))}
      </div>

      {/* Flight Cards */}
      {loading ? (
        <div className="space-y-3">
          {[1,2,3].map(i => (
            <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse">
              <div className="h-5 bg-surface-container rounded w-1/3 mb-3" />
              <div className="h-4 bg-surface-container rounded w-2/3 mb-2" />
              <div className="h-4 bg-surface-container rounded w-1/2" />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12">
          <span className="material-symbols-outlined text-outline text-5xl mb-3 block">flight_takeoff</span>
          <p className="text-on-surface-variant text-sm">Ucus bulunamadi</p>
        </div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(flight => {
            const st = statusMap[flight.status || 'SCHEDULED'] || statusMap.SCHEDULED;
            const airlineCode = flight.flightNumber?.substring(0, 2) || 'TK';
            const airlineName = flight.airline || 'Turkish Airlines';

            return (
              <div key={flight.id} className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                <div className="p-4">
                  {/* Flight header */}
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center">
                        <span className="text-xs font-bold text-on-surface-variant">{airlineCode}</span>
                      </div>
                      <div>
                        <span className="text-lg font-bold text-on-surface">{flight.flightNumber}</span>
                        <span className="text-xs text-on-surface-variant ml-1.5">{airlineName}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${st.class} flex items-center gap-1`}>
                        <span className="w-1.5 h-1.5 rounded-full bg-current" />
                        {st.label}
                      </span>
                    </div>
                  </div>

                  {/* Route visualization */}
                  <div className="flex items-center justify-between mb-3">
                    <div className="text-center">
                      <p className="text-xl font-bold text-on-surface">
                        {flight.departureTime ? new Date(flight.departureTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '--:--'}
                      </p>
                      <p className="text-sm font-bold text-on-surface">{flight.departureAirportCode || 'IST'}</p>
                      <p className="text-[10px] text-on-surface-variant">{flight.departureAirportName || ''}</p>
                    </div>

                    <div className="flex-1 mx-4 flex flex-col items-center">
                      <span className="text-[10px] text-on-surface-variant mb-1">
                        {flight.duration || 'Direkt Ucus'}
                      </span>
                      <div className="w-full flex items-center gap-1">
                        <div className="w-2 h-2 rounded-full bg-primary" />
                        <div className="flex-1 h-px bg-primary-fixed relative">
                          <span className="material-symbols-outlined absolute left-1/2 -translate-x-1/2 -top-2.5 text-primary text-[16px]">flight</span>
                        </div>
                        <div className="w-2 h-2 rounded-full bg-primary" />
                      </div>
                      <span className="text-[10px] text-on-surface-variant mt-1">Direkt Ucus</span>
                    </div>

                    <div className="text-center">
                      <p className="text-xl font-bold text-on-surface">
                        {flight.arrivalTime ? new Date(flight.arrivalTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '--:--'}
                      </p>
                      <p className="text-sm font-bold text-on-surface">{flight.arrivalAirportCode || 'AYT'}</p>
                      <p className="text-[10px] text-on-surface-variant">{flight.arrivalAirportName || ''}</p>
                    </div>
                  </div>

                  {/* Capacity bar */}
                  {flight.availableSeats !== undefined && flight.totalSeats !== undefined && (
                    <div className="mb-3">
                      <div className="flex items-center justify-between text-xs text-on-surface-variant mb-1">
                        <span>Koltuk Dolulugu</span>
                        <span>%{Math.round(((flight.totalSeats - flight.availableSeats) / flight.totalSeats) * 100)} ({flight.totalSeats - flight.availableSeats}/{flight.totalSeats})</span>
                      </div>
                      <div className="w-full h-1.5 bg-surface-container rounded-full overflow-hidden">
                        <div className="h-full bg-primary rounded-full transition-all"
                          style={{ width: `${((flight.totalSeats - flight.availableSeats) / flight.totalSeats) * 100}%` }} />
                      </div>
                    </div>
                  )}

                  {/* Price + Actions */}
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-xs text-on-surface-variant">Baslangic Fiyati</span>
                      <p className="text-xl font-bold text-primary">
                        ₺{(flight.price || 0).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <button className="px-4 py-2 rounded-lg bg-surface-container text-on-surface text-xs font-medium hover:bg-surface-container-high transition-colors">
                        Detay
                      </button>
                      <button className="px-4 py-2 rounded-lg bg-primary text-on-primary text-xs font-medium shadow-sm hover:bg-primary-container active:scale-95 transition-all">
                        Yonet
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
