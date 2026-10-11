'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Hotel } from '@/lib/types';
import { Building2, Plus, Search, Star } from 'lucide-react';

export default function HotelsPage() {
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    api.get<Hotel[]>('/api/hotels')
      .then(r => setHotels(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const filtered = hotels.filter(h =>
    h.name.toLowerCase().includes(search.toLowerCase()) ||
    h.city.toLowerCase().includes(search.toLowerCase())
  );

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const body = {
      name: fd.get('name'),
      city: fd.get('city'),
      country: fd.get('country'),
      address: fd.get('address'),
      starRating: Number(fd.get('starRating')),
      description: fd.get('description') || undefined,
    };
    try {
      const r = await api.post<Hotel>('/api/hotels', body);
      setHotels(prev => [r.data, ...prev]);
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
            <Building2 size={24} /> Oteller
          </h1>
          <p className="text-gray-500 text-sm mt-1">{hotels.length} otel kaydi</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
        >
          <Plus size={16} /> Yeni Otel
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold mb-4">Yeni Otel Olustur</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <input name="name" placeholder="Otel Adi" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="city" placeholder="Sehir" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="country" placeholder="Ulke" required className="border rounded-lg px-3 py-2 text-sm" />
            <input name="address" placeholder="Adres" required className="border rounded-lg px-3 py-2 text-sm" />
            <select name="starRating" required className="border rounded-lg px-3 py-2 text-sm">
              <option value="">Yildiz Secin</option>
              {[1,2,3,4,5].map(n => <option key={n} value={n}>{n} Yildiz</option>)}
            </select>
            <input name="description" placeholder="Aciklama (opsiyonel)" className="border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div className="flex gap-3 mt-4">
            <button type="submit" className="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 text-sm font-medium">Olustur</button>
            <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 text-sm">Iptal</button>
          </div>
        </form>
      )}

      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text"
          placeholder="Otel adi veya sehir ara..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
        />
      </div>

      {loading ? (
        <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
      ) : filtered.length === 0 ? (
        <div className="p-8 text-center text-gray-500">Otel bulunamadi</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {filtered.map(h => (
            <div key={h.id} className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between">
                <h3 className="font-semibold text-gray-900">{h.name}</h3>
                <div className="flex items-center gap-0.5">
                  {Array.from({ length: h.starRating }).map((_, i) => (
                    <Star key={i} size={14} className="fill-yellow-400 text-yellow-400" />
                  ))}
                </div>
              </div>
              <p className="text-sm text-gray-500 mt-1">{h.city}, {h.country}</p>
              <p className="text-xs text-gray-400 mt-1">{h.address}</p>
              {h.description && <p className="text-sm text-gray-600 mt-2">{h.description}</p>}
              <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-100">
                <span className="text-sm text-gray-500">{h.roomCount} oda</span>
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${h.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                  {h.active ? 'Aktif' : 'Pasif'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
