'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import type { Flight, Hotel, Vehicle, PackageBooking, Customer, Payment } from '@/lib/types';
import Link from 'next/link';

interface OrderSummary {
  id: string;
  status: string;
  currency: string;
  totalAmount: number;
  createdAt: string;
}

type CardKey = 'flights' | 'hotels' | 'vehicles' | 'orders' | 'bookings' | 'customers' | 'revenue';

interface StatCard {
  key: CardKey;
  label: string;
  icon: string;
  iconBg: string;
  iconColor: string;
  href: string;
  adminOnly?: boolean;
}

const statCards: StatCard[] = [
  { key: 'flights', label: 'Ucuslar', icon: 'flight', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', href: '/flights' },
  { key: 'hotels', label: 'Oteller', icon: 'hotel', iconBg: 'bg-secondary-fixed', iconColor: 'text-secondary', href: '/hotels' },
  { key: 'vehicles', label: 'Araclar', icon: 'directions_car', iconBg: 'bg-tertiary-fixed', iconColor: 'text-tertiary', href: '/vehicles' },
  { key: 'orders', label: 'Siparislerim', icon: 'receipt_long', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', href: '/orders' },
  { key: 'bookings', label: 'Rezervasyon', icon: 'confirmation_number', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', href: '/bookings', adminOnly: true },
  { key: 'customers', label: 'Musteriler', icon: 'group', iconBg: 'bg-tertiary-fixed', iconColor: 'text-tertiary', href: '/customers', adminOnly: true },
  { key: 'revenue', label: 'Tamamlanan Odemeler', icon: 'payments', iconBg: 'bg-secondary-fixed', iconColor: 'text-secondary', href: '/payments', adminOnly: true },
];

type Counts = Partial<Record<CardKey, number>>;

export default function Dashboard() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [counts, setCounts] = useState<Counts>({});
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    async function loadStats() {
      const jobs: Array<[CardKey, Promise<{ data: unknown[] }>]> = [
        ['flights', api.get<Flight[]>('/api/flights')],
        ['hotels', api.get<Hotel[]>('/api/hotels')],
        ['vehicles', api.get<Vehicle[]>('/api/vehicles')],
        ['orders', api.get<OrderSummary[]>('/api/orders')],
      ];
      if (isAdmin) {
        jobs.push(
          ['bookings', api.get<PackageBooking[]>('/api/bookings')],
          ['customers', api.get<Customer[]>('/api/customers')],
          ['revenue', api.get<Payment[]>('/api/payments')],
        );
      }
      const results = await Promise.allSettled(jobs.map(j => j[1]));
      if (cancelled) return;
      const next: Counts = {};
      let failed = 0;
      results.forEach((r, i) => {
        const key = jobs[i][0];
        if (r.status !== 'fulfilled') { failed++; return; }
        const rows = r.value.data;
        if (key === 'revenue') {
          next.revenue = (rows as Payment[]).filter(p => p.status === 'COMPLETED').length;
        } else {
          next[key] = rows.length;
        }
        if (key === 'orders') {
          setOrders(([...(rows as OrderSummary[])]).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5));
        }
      });
      if (failed === results.length) setError('Veri yuklenemedi');
      setCounts(next);
      setLoading(false);
    }
    void loadStats();
    return () => { cancelled = true; };
  }, [user, isAdmin]);

  const visibleCards = statCards.filter(c => (!c.adminOnly || isAdmin) && (loading || counts[c.key] !== undefined));

  const today = new Date().toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' });

  return (
    <div className="space-y-space-md">
      {/* Welcome Banner */}
      <section className="bg-surface-container-low p-space-md rounded-xl shadow-sm relative overflow-hidden">
        <div className="flex items-center justify-end">
          <span className="text-xs text-outline">{today}</span>
        </div>
        <div className="pt-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">
            Hos geldin{user?.firstName ? `, ${user.firstName}` : ''}
          </h2>
        </div>
        <div className="flex items-center gap-space-xs pt-space-sm overflow-x-auto no-scrollbar pb-1">
          {[
            { href: '/flights', icon: 'flight', label: 'Ucuslar' },
            { href: '/hotels', icon: 'hotel', label: 'Oteller' },
            { href: '/vehicles', icon: 'directions_car', label: 'Araclar' },
            { href: '/orders', icon: 'receipt_long', label: 'Siparislerim' },
          ].map(a => (
            <Link key={a.href} href={a.href} className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-lowest text-on-surface text-xs font-medium shadow-sm hover:bg-surface-container active:scale-95 transition-all shrink-0">
              <span className="material-symbols-outlined text-[16px] text-primary">{a.icon}</span>
              <span>{a.label}</span>
            </Link>
          ))}
        </div>
      </section>

      {error && (
        <div className="p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
          <span className="material-symbols-outlined text-base">warning</span>
          {error}
        </div>
      )}

      <section className="grid grid-cols-2 lg:grid-cols-3 gap-space-sm">
        {visibleCards.map(({ key, label, icon, iconBg, iconColor, href }) => (
          <Link key={key} href={href}
            className="flex flex-col justify-between p-space-sm bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow group">
            <div className="flex items-center justify-between mb-1">
              <div className={`w-8 h-8 rounded-lg ${iconBg} flex items-center justify-center`}>
                <span className={`material-symbols-outlined ${iconColor} text-[18px]`}>{icon}</span>
              </div>
            </div>
            <span className="text-xs text-on-surface-variant mt-1">{label}</span>
            <span className="text-2xl font-bold text-on-surface tracking-tight">
              {loading ? (
                <span className="inline-block w-12 h-7 bg-surface-container animate-pulse rounded" />
              ) : (
                counts[key]
              )}
            </span>
          </Link>
        ))}
      </section>

      {orders.length > 0 && (
        <section>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-base font-semibold text-on-surface flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-xl">receipt_long</span>
              Son Siparislerim
            </h3>
            <Link href="/orders" className="text-xs font-medium text-primary hover:text-primary-container transition-colors">
              Tum siparisler
            </Link>
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
                  <span className="text-lg font-bold text-on-surface">{o.totalAmount.toLocaleString('tr-TR')} {o.currency}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
