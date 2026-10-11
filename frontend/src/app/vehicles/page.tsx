'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Vehicle } from '@/lib/types';
import { formatCurrency } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { Car, Plus, Search } from 'lucide-react';

export default function VehiclesPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    api.get<Vehicle[]>('/api/vehicles')
      .then(r => setVehicles(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const filtered = vehicles.filter(v =>
    v.brand.toLowerCase().includes(search.toLowerCase()) ||
    v.model.toLowerCase().includes(search.toLowerCase()) ||
    v.plateNumber.toLowerCase().includes(search.toLowerCase()) ||
    v.location.toLowerCase().includes(search.toLowerCase())
  );

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const body = {
      plateNumber: fd.get('plateNumber'),
      brand: fd.get('brand'),
      model: fd.get('model'),
      year: Number(fd.get('year')),
      vehicleType: fd.get('vehicleType'),
      seatCount: Number(fd.get('seatCount')),
      dailyRate: Number(fd.get('dailyRate')),
      location: fd.get('location'),
    };
    try {
      const r = await api.post<Vehicle>('/api/vehicles', body);
      setVehicles(prev => [r.data, ...prev]);
      setShowForm(false);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Hata');
    }
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Car size={24} /> Araclar
          </h1>
          <p className="text-gray-500 text-sm mt-1">{vehicles.length} arac kaydi</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors text-sm font-medium"
        >
          <Plus size={16} /> Yeni Arac
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold mb-4">Yeni Arac Ekle</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <input name="plateNumber" placeholder="Plaka (34 ABC 123)" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="brand" placeholder="Marka" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="model" placeholder="Model" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="year" type="number" placeholder="Yil" required min={1990} className="border rounded-lg px-3 py-2 text-sm" />
            <select name="vehicleType" required className="border rounded-lg px-3 py-2 text-sm">
              <option value="">Tip Secin</option>
              {['ECONOMY','COMPACT','MIDSIZE','FULLSIZE','SUV','LUXURY','VAN'].map(t => <option key={t} value={t}>{t}</option>)}
            </select>
            <input name="seatCount" type="number" placeholder="Koltuk Sayisi" required min={1} className="border rounded-lg px-3 py-2 text-sm" />
            <input name="dailyRate" type="number" placeholder="Gunluk Ucret" required min={0} step="0.01" className="border rounded-lg px-3 py-2 text-sm" />
            <input name="location" placeholder="Konum" required className="border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div className="flex gap-3 mt-4">
            <button type="submit" className="px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 text-sm font-medium">Olustur</button>
            <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 text-sm">Iptal</button>
          </div>
        </form>
      )}

      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text"
          placeholder="Marka, model, plaka veya konum ara..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-orange-500"
        />
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Arac bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Plaka</th>
                  <th className="px-4 py-3 text-left">Marka / Model</th>
                  <th className="px-4 py-3 text-left">Tip</th>
                  <th className="px-4 py-3 text-left">Konum</th>
                  <th className="px-4 py-3 text-left">Gunluk Ucret</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map(v => (
                  <tr key={v.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">{v.plateNumber}</td>
                    <td className="px-4 py-3">{v.brand} {v.model} ({v.year})</td>
                    <td className="px-4 py-3 text-gray-600">{v.vehicleType}</td>
                    <td className="px-4 py-3 text-gray-600">{v.location}</td>
                    <td className="px-4 py-3 font-medium">{formatCurrency(v.dailyRate)}</td>
                    <td className="px-4 py-3"><StatusBadge status={v.status} /></td>
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
