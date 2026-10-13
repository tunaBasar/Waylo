'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Customer } from '@/lib/types';

const tierConfig: Record<string, { color: string; bg: string }> = {
  BRONZE: { color: 'text-orange-700', bg: 'bg-orange-100' },
  SILVER: { color: 'text-gray-600', bg: 'bg-gray-200' },
  GOLD: { color: 'text-yellow-700', bg: 'bg-yellow-100' },
  PLATINUM: { color: 'text-indigo-700', bg: 'bg-indigo-100' },
};

export default function CustomersPage() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [tierFilter, setTierFilter] = useState<string | null>(null);

  useEffect(() => {
    api.get<Customer[]>('/api/customers').then(r => { setCustomers(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = customers.filter(c => {
    const matchSearch = !search || `${c.firstName} ${c.lastName}`.toLowerCase().includes(search.toLowerCase()) || c.email?.toLowerCase().includes(search.toLowerCase());
    const matchTier = !tierFilter || c.tier === tierFilter;
    return matchSearch && matchTier;
  });

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Musteriler</h2>
          <span className="bg-secondary-container text-on-secondary-container text-xs font-semibold px-2.5 py-0.5 rounded-full">{customers.length}</span>
        </div>
        <button className="bg-secondary hover:bg-secondary-container text-on-secondary text-sm font-medium px-3.5 py-2 rounded-lg flex items-center gap-1 shadow-md active:scale-95 transition-all">
          <span className="material-symbols-outlined text-[18px]">person_add</span> Yeni Musteri
        </button>
      </div>

      <div className="relative shadow-sm rounded-xl bg-surface-container-lowest">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
        <input value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none"
          placeholder="Isim veya e-posta ara..." />
      </div>

      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
        <button onClick={() => setTierFilter(null)} className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-colors ${!tierFilter ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>Tumu</button>
        {['PLATINUM', 'GOLD', 'SILVER', 'BRONZE'].map(t => (
          <button key={t} onClick={() => setTierFilter(tierFilter === t ? null : t)}
            className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-colors ${tierFilter === t ? (tierConfig[t]?.bg + ' ' + tierConfig[t]?.color) : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>
            {t}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">people</span><p className="text-on-surface-variant text-sm">Musteri bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(c => {
            const tc = tierConfig[c.tier] || tierConfig.BRONZE;
            const initials = `${c.firstName?.[0] || ''}${c.lastName?.[0] || ''}`.toUpperCase();
            return (
              <div key={c.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center gap-3">
                  <div className="w-11 h-11 rounded-full bg-primary-fixed flex items-center justify-center text-primary text-sm font-bold">{initials}</div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-bold text-on-surface truncate">{c.firstName} {c.lastName}</p>
                      <span className={`text-[9px] font-bold uppercase px-2 py-0.5 rounded-full ${tc.bg} ${tc.color}`}>{c.tier}</span>
                    </div>
                    <p className="text-xs text-on-surface-variant truncate">{c.email}</p>
                    <div className="flex items-center gap-3 mt-1 text-[10px] text-on-surface-variant">
                      {c.phone && <span className="flex items-center gap-0.5"><span className="material-symbols-outlined text-[12px]">call</span> {c.phone}</span>}
                      <span className="flex items-center gap-0.5"><span className="material-symbols-outlined text-[12px]">star</span> {c.loyaltyPoints.toLocaleString()} puan</span>
                    </div>
                  </div>
                  <button className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center hover:bg-surface-container-high transition-colors">
                    <span className="material-symbols-outlined text-on-surface-variant text-[18px]">chevron_right</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
