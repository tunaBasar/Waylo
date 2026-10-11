'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Flight } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { Plane, Plus, Search } from 'lucide-react';

export default function FlightsPage() {
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    api.get<Flight[]>('/api/flights')
      .then(r => setFlights(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const filtered = flights.filter(f =>
    f.flightNumber.toLowerCase().includes(search.toLowerCase()) ||
    f.airline.toLowerCase().includes(search.toLowerCase()) ||
    f.departureAirportCode.toLowerCase().includes(search.toLowerCase()) ||
    f.arrivalAirportCode.toLowerCase().includes(search.toLowerCase())
  );

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const body = {
      flightNumber: fd.get('flightNumber'),
      departureAirportCode: fd.get('from'),
      arrivalAirportCode: fd.get('to'),
      departureTime: new Date(fd.get('departureTime') as string).toISOString(),
      arrivalTime: new Date(fd.get('arrivalTime') as string).toISOString(),
      airline: fd.get('airline'),
    };
    try {
      const r = await api.post<Flight>('/api/flights', body);
      setFlights(prev => [r.data, ...prev]);
      setShowForm(false);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Hata');
    }
  }

  async function handleCancel(id: string) {
    try {
      const r = await api.post<Flight>(`/api/flights/${id}/cancel`, {});
      setFlights(prev => prev.map(f => f.id === id ? r.data : f));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Hata');
    }
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Plane size={24} /> Ucuslar
          </h1>
          <p className="text-gray-500 text-sm mt-1">{flights.length} ucus kaydi</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
        >
          <Plus size={16} /> Yeni Ucus
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold mb-4">Yeni Ucus Olustur</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <input name="flightNumber" placeholder="Ucus No (orn: TK1001)" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="from" placeholder="Kalkis (IATA: IST)" required maxLength={3} className="border rounded-lg px-3 py-2 text-sm" />
            <input name="to" placeholder="Varis (IATA: SAW)" required maxLength={3} className="border rounded-lg px-3 py-2 text-sm" />
            <input name="departureTime" type="datetime-local" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="arrivalTime" type="datetime-local" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="airline" placeholder="Havayolu" required className="border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div className="flex gap-3 mt-4">
            <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium">Olustur</button>
            <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 text-sm">Iptal</button>
          </div>
        </form>
      )}

      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text"
          placeholder="Ucus no, havayolu veya havaalani ara..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Ucus bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Ucus No</th>
                  <th className="px-4 py-3 text-left">Guzergah</th>
                  <th className="px-4 py-3 text-left">Kalkis</th>
                  <th className="px-4 py-3 text-left">Varis</th>
                  <th className="px-4 py-3 text-left">Havayolu</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                  <th className="px-4 py-3 text-left">Islem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map(f => (
                  <tr key={f.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">{f.flightNumber}</td>
                    <td className="px-4 py-3">
                      <span className="font-medium">{f.departureAirportCode}</span>
                      <span className="text-gray-400 mx-1">&#8594;</span>
                      <span className="font-medium">{f.arrivalAirportCode}</span>
                    </td>
                    <td className="px-4 py-3 text-gray-600">{formatDate(f.departureTime)}</td>
                    <td className="px-4 py-3 text-gray-600">{formatDate(f.arrivalTime)}</td>
                    <td className="px-4 py-3 text-gray-600">{f.airline}</td>
                    <td className="px-4 py-3"><StatusBadge status={f.status} /></td>
                    <td className="px-4 py-3">
                      {f.status === 'SCHEDULED' && (
                        <button
                          onClick={() => handleCancel(f.id)}
                          className="text-red-600 hover:text-red-800 text-xs font-medium"
                        >
                          Iptal Et
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
