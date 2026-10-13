'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Hotel } from '@/lib/types';

export default function HotelsPage() {
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [starFilter, setStarFilter] = useState<number | null>(null);
  const [view, setView] = useState<'cards' | 'rooms'>('cards');

  useEffect(() => {
    api.get<Hotel[]>('/api/hotels').then(res => {
      setHotels(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const filtered = hotels.filter(h => {
    const matchSearch = !search ||
      h.name?.toLowerCase().includes(search.toLowerCase()) ||
      h.city?.toLowerCase().includes(search.toLowerCase());
    const matchStar = starFilter === null || h.starRating === starFilter;
    return matchSearch && matchStar;
  });

  // Placeholder hotel images
  const hotelImages: Record<number, string> = {
    0: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=600&h=300&fit=crop',
    1: 'https://images.unsplash.com/photo-1582719508461-905c673771fd?w=600&h=300&fit=crop',
    2: 'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=600&h=300&fit=crop',
    3: 'https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=600&h=300&fit=crop',
    4: 'https://images.unsplash.com/photo-1564501049412-61c2a3083791?w=600&h=300&fit=crop',
  };

  return (
    <div className="space-y-space-md">
      {/* Header */}
      <div className="flex items-center justify-between gap-space-sm">
        <div className="flex items-center gap-space-xs min-w-0">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Oteller</h2>
          <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-surface-container-high text-primary text-xs font-semibold">
            {hotels.length} Tesis
          </span>
        </div>
        <button className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-secondary text-on-secondary text-sm font-medium shadow-sm active:scale-95 transition-all">
          <span className="material-symbols-outlined text-[18px]">add</span>
          <span>Yeni Otel Ekle</span>
        </button>
      </div>

      {/* View Switcher */}
      <div className="bg-surface-container-low p-1 rounded-xl flex items-center gap-1">
        <button onClick={() => setView('cards')}
          className={`flex-1 py-1.5 px-3 rounded-lg flex items-center justify-center gap-1.5 text-xs font-medium transition-all ${
            view === 'cards' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface'
          }`}>
          <span className="material-symbols-outlined text-[18px]">grid_view</span>
          <span>Kart Gorunumu</span>
        </button>
        <button onClick={() => setView('rooms')}
          className={`flex-1 py-1.5 px-3 rounded-lg flex items-center justify-center gap-1.5 text-xs font-medium transition-all ${
            view === 'rooms' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface'
          }`}>
          <span className="material-symbols-outlined text-[18px]">meeting_room</span>
          <span>Oda Listesi</span>
        </button>
      </div>

      {/* Search */}
      <div className="relative w-full">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px] pointer-events-none">search</span>
        <input value={search} onChange={e => setSearch(e.target.value)}
          className="w-full bg-surface-container-lowest text-on-surface placeholder:text-outline pl-10 pr-10 py-2.5 rounded-xl text-sm outline-none shadow-sm focus:bg-surface-container-low transition-all"
          placeholder="Otel adi veya sehir ara..." />
      </div>

      {/* Star Filters */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
        <span className="text-xs text-on-surface-variant whitespace-nowrap">Yildiz:</span>
        {[5, 4, 3].map(star => (
          <button key={star} onClick={() => setStarFilter(starFilter === star ? null : star)}
            className={`px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors shrink-0 ${
              starFilter === star
                ? 'bg-primary-container text-on-primary-container'
                : 'bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container shadow-sm'
            }`}>
            {'★'.repeat(star)} {star} Yildiz
          </button>
        ))}
        <button onClick={() => setStarFilter(null)}
          className={`px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors shrink-0 ${
            starFilter === null
              ? 'bg-primary text-on-primary'
              : 'bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container shadow-sm'
          }`}>
          Tumu
        </button>
      </div>

      {/* Hotel Cards */}
      {loading ? (
        <div className="space-y-4">
          {[1,2,3].map(i => (
            <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden animate-pulse">
              <div className="h-48 bg-surface-container" />
              <div className="p-4 space-y-2">
                <div className="h-5 bg-surface-container rounded w-2/3" />
                <div className="h-4 bg-surface-container rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12">
          <span className="material-symbols-outlined text-outline text-5xl mb-3 block">hotel</span>
          <p className="text-on-surface-variant text-sm">Otel bulunamadi</p>
        </div>
      ) : (
        <div className="space-y-space-md">
          {filtered.map((hotel, idx) => (
            <div key={hotel.id} className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
              {/* Hotel Image */}
              <div className="relative h-48 bg-surface-container overflow-hidden">
                <img
                  src={hotelImages[idx % 5]}
                  alt={hotel.name}
                  className="w-full h-full object-cover"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />

                {/* Badges */}
                <div className="absolute top-3 left-3 flex items-center gap-1.5">
                  <span className="text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-secondary text-on-secondary">
                    {hotel.active ? 'AKTIF' : 'PASIF'}
                  </span>
                  <span className="text-[10px] font-semibold px-2 py-1 rounded-full bg-surface-container-lowest/90 text-on-surface flex items-center gap-0.5">
                    ★ {hotel.starRating}.0
                  </span>
                </div>

                {/* Settings button */}
                <button className="absolute top-3 right-3 w-8 h-8 rounded-lg bg-surface-container-lowest/80 flex items-center justify-center hover:bg-surface-container-lowest transition-colors">
                  <span className="material-symbols-outlined text-on-surface text-[18px]">tune</span>
                </button>

                {/* Hotel name overlay */}
                <div className="absolute bottom-3 left-3 right-3">
                  <h3 className="text-lg font-bold text-white">{hotel.name}</h3>
                  <div className="flex items-center gap-1 text-white/80 text-xs">
                    <span className="material-symbols-outlined text-[14px]">location_on</span>
                    <span>{hotel.city}, {hotel.country || 'Turkiye'}</span>
                  </div>
                </div>
              </div>

              {/* Hotel Details */}
              <div className="p-4">
                {/* Room count + occupancy */}
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
                    <span className="material-symbols-outlined text-[16px]">bed</span>
                    <span>{hotel.roomCount || 0} Oda</span>
                  </div>
                  {hotel.roomCount > 0 && (
                    <span className="text-xs font-medium text-primary px-2 py-0.5 rounded-full bg-primary-fixed">
                      Yuksek Doluluk
                    </span>
                  )}
                </div>

                {/* Amenities */}
                <div className="flex items-center gap-3 mb-3 text-xs text-on-surface-variant">
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">pool</span> Havuz
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">spa</span> Spa
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">restaurant</span> Restoran
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">local_parking</span> P
                  </span>
                </div>

                {/* Price + action */}
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-xs text-on-surface-variant">Gecelik Baslangic</span>
                    <p className="text-xl font-bold text-primary">
                      ₺{((hotel.starRating || 3) * 1200 + 600).toLocaleString('tr-TR')}
                      <span className="text-xs font-normal text-on-surface-variant"> /gece</span>
                    </p>
                  </div>
                  <button className="px-4 py-2 rounded-lg bg-primary text-on-primary text-xs font-medium shadow-sm hover:bg-primary-container active:scale-95 transition-all flex items-center gap-1">
                    Odalari Yonet <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
