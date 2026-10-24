'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import { formatCurrency } from '@/lib/utils';

interface OrderSummary {
  id: string;
  status: string;
  currency: string;
  totalAmount: number;
  createdAt: string;
}

type Tab = 'hotels' | 'flights' | 'vehicles';

const tabs: Array<{ key: Tab; label: string; icon: string }> = [
  { key: 'hotels', label: 'Otel', icon: 'hotel' },
  { key: 'flights', label: 'Ucus', icon: 'flight' },
  { key: 'vehicles', label: 'Arac', icon: 'directions_car' },
];

const field = 'w-full rounded-lg bg-surface-container-lowest px-3 py-2 text-sm text-on-surface outline-none shadow-sm';
const label = 'mb-1 block text-xs text-on-surface-variant';

export default function CustomerHome() {
  const { user } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>('hotels');
  const [city, setCity] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState('');
  const [pickup, setPickup] = useState('');
  const [dropoff, setDropoff] = useState('');
  const [location, setLocation] = useState('');
  const [orders, setOrders] = useState<OrderSummary[]>([]);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    api.get<OrderSummary[]>('/api/orders')
      .then(res => {
        if (!cancelled) setOrders([...res.data].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5));
      })
      .catch(() => { /* the hub still works without the recent orders list */ });
    return () => { cancelled = true; };
  }, [user]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (tab === 'hotels' && city.trim()) params.set('city', city.trim());
    if (tab === 'flights') {
      if (from) params.set('from', from.toUpperCase());
      if (to) params.set('to', to.toUpperCase());
      if (date) params.set('date', date);
    }
    if (tab === 'vehicles') {
      if (pickup) params.set('from', pickup);
      if (dropoff) params.set('to', dropoff);
      if (location.trim()) params.set('location', location.trim());
    }
    const query = params.toString();
    router.push(`/${tab}${query ? `?${query}` : ''}`);
  }

  return (
    <div className="space-y-space-md">
      <section className="bg-surface-container-low p-space-md rounded-xl shadow-sm">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">
          Seyahatini planla{user?.firstName ? `, ${user.firstName}` : ''}
        </h2>
        <p className="text-sm text-on-surface-variant mt-0.5">Otel, ucus ve arac kiralama icin arama yap.</p>

        <div className="mt-space-sm flex gap-1 rounded-xl bg-surface-container p-1" role="tablist">
          {tabs.map(t => (
            <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}
              className={`flex flex-1 items-center justify-center gap-1.5 rounded-lg py-1.5 text-xs font-medium transition-all ${
                tab === t.key ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant'
              }`}>
              <span className="material-symbols-outlined text-[18px]">{t.icon}</span>
              {t.label}
            </button>
          ))}
        </div>

        <form onSubmit={submit} className="mt-space-sm grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {tab === 'hotels' && (
            <label className="lg:col-span-3"><span className={label}>Sehir</span>
              <input value={city} onChange={e => setCity(e.target.value)} placeholder="Sehir girin" className={field} />
            </label>
          )}
          {tab === 'flights' && (
            <>
              <label><span className={label}>Kalkis (IATA)</span>
                <input value={from} onChange={e => setFrom(e.target.value.toUpperCase())} maxLength={3} placeholder="IST" className={`${field} uppercase`} />
              </label>
              <label><span className={label}>Varis (IATA)</span>
                <input value={to} onChange={e => setTo(e.target.value.toUpperCase())} maxLength={3} placeholder="ESB" className={`${field} uppercase`} />
              </label>
              <label><span className={label}>Tarih</span>
                <input type="date" value={date} onChange={e => setDate(e.target.value)} className={field} />
              </label>
            </>
          )}
          {tab === 'vehicles' && (
            <>
              <label><span className={label}>Alis tarihi</span>
                <input type="date" value={pickup} onChange={e => setPickup(e.target.value)} className={field} />
              </label>
              <label><span className={label}>Teslim tarihi</span>
                <input type="date" value={dropoff} onChange={e => setDropoff(e.target.value)} className={field} />
              </label>
              <label><span className={label}>Lokasyon</span>
                <input value={location} onChange={e => setLocation(e.target.value)} placeholder="Opsiyonel" className={field} />
              </label>
            </>
          )}
          <div className="flex items-end">
            <button className="flex w-full items-center justify-center gap-1 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary shadow-sm active:scale-95 transition-all">
              <span className="material-symbols-outlined text-[18px]">search</span>
              Ara
            </button>
          </div>
        </form>
      </section>

      {orders.length > 0 && (
        <section>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-base font-semibold text-on-surface flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-xl">receipt_long</span>
              Son Siparislerim
            </h3>
            <Link href="/orders" className="text-xs font-medium text-primary">Tum siparisler</Link>
          </div>
          <div className="space-y-space-sm">
            {orders.map(o => (
              <div key={o.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-space-md">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-semibold text-on-surface-variant">#{o.id.slice(0, 8)}</span>
                  <span className="text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-surface-container-high text-on-surface-variant">{o.status}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-xs text-on-surface-variant">{new Date(o.createdAt).toLocaleDateString('tr-TR')}</span>
                  <span className="text-lg font-bold text-on-surface">{formatCurrency(o.totalAmount, o.currency)}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
