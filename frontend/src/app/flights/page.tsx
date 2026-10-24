'use client';

import { FormEvent, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Flight } from '@/lib/types';

const statusMap: Record<Flight['status'], { label: string; class: string }> = {
  SCHEDULED: { label: 'PLANLANDI', class: 'text-primary bg-primary-fixed' },
  CANCELLED: { label: 'IPTAL EDILDI', class: 'text-error bg-error-container' },
};

export default function FlightsPage() {
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | Flight['status']>('all');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<Flight[]>('/api/flights').then(res => setFlights(res.data)).catch(() => setError('Ucuslar yuklenemedi.')).finally(() => setLoading(false));
  }, []);

  async function searchFlights(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!from || !to || !date) {
      setError('Kalkis, varis ve tarih alanlarini doldurun.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ from: from.toUpperCase(), to: to.toUpperCase(), date });
      const res = await api.get<Flight[]>(`/api/flights/search?${params}`);
      setFlights(res.data);
    } catch {
      setError('Ucus aramasi yapilamadi.');
    } finally {
      setLoading(false);
    }
  }

  const filtered = flights.filter(f => (!search || f.flightNumber.toLowerCase().includes(search.toLowerCase()) || f.airline.toLowerCase().includes(search.toLowerCase())) && (statusFilter === 'all' || f.status === statusFilter));

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between gap-space-xs"><div className="flex items-center gap-space-xs"><h2 className="text-xl font-semibold text-on-surface tracking-tight">Ucuslar</h2><span className="bg-primary-fixed text-on-primary-fixed-variant text-xs font-semibold px-2.5 py-0.5 rounded-full">{flights.length} Ucus</span></div></div>
      <form onSubmit={searchFlights} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-4">
        <input value={from} onChange={e => setFrom(e.target.value.toUpperCase())} maxLength={3} placeholder="Kalkis IATA" className="rounded-lg bg-surface-container px-3 py-2 text-sm uppercase focus:outline-none" />
        <input value={to} onChange={e => setTo(e.target.value.toUpperCase())} maxLength={3} placeholder="Varis IATA" className="rounded-lg bg-surface-container px-3 py-2 text-sm uppercase focus:outline-none" />
        <input type="date" value={date} onChange={e => setDate(e.target.value)} className="rounded-lg bg-surface-container px-3 py-2 text-sm focus:outline-none" />
        <button className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-container">Ucus Ara</button>
      </form>
      <div className="relative w-full shadow-sm rounded-xl bg-surface-container-lowest"><input value={search} onChange={e => setSearch(e.target.value)} className="w-full px-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none" placeholder="Ucus No veya Havayolu ara..." /></div>
      <div className="flex items-center gap-space-xs overflow-x-auto pb-1 no-scrollbar">{[{ key: 'all', label: `Tumu (${flights.length})` }, { key: 'SCHEDULED', label: `PLANLANDI (${flights.filter(f => f.status === 'SCHEDULED').length})` }, { key: 'CANCELLED', label: `IPTAL (${flights.filter(f => f.status === 'CANCELLED').length})` }].map(filter => <button key={filter.key} onClick={() => setStatusFilter(filter.key as typeof statusFilter)} className={`text-xs font-medium px-3.5 py-1.5 rounded-full whitespace-nowrap shadow-sm ${statusFilter === filter.key ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant'}`}>{filter.label}</button>)}</div>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {loading ? <div className="text-center py-12 text-sm text-on-surface-variant">Ucuslar yukleniyor...</div> : filtered.length === 0 ? <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">flight_takeoff</span><p className="text-on-surface-variant text-sm">Ucus bulunamadi</p></div> : <div className="space-y-space-sm">{filtered.map(flight => { const status = statusMap[flight.status]; return <div key={flight.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4"><div className="flex items-center justify-between mb-3"><div><span className="text-lg font-bold text-on-surface">{flight.flightNumber}</span><span className="text-xs text-on-surface-variant ml-1.5">{flight.airline}</span></div><span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${status.class}`}>{status.label}</span></div><div className="flex items-center justify-between"><div><p className="text-xl font-bold text-on-surface">{new Date(flight.departureTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}</p><p className="text-sm font-bold text-on-surface">{flight.departureAirportCode}</p></div><div className="flex-1 mx-4 h-px bg-primary-fixed" /><div className="text-right"><p className="text-xl font-bold text-on-surface">{new Date(flight.arrivalTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}</p><p className="text-sm font-bold text-on-surface">{flight.arrivalAirportCode}</p></div></div></div>; })}</div>}
    </div>
  );
}
