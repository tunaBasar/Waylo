'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import type { Flight, Hotel, Vehicle, PackageBooking, Customer, Payment } from '@/lib/types';
import Link from 'next/link';

interface DashboardStats {
  flights: number;
  hotels: number;
  vehicles: number;
  bookings: number;
  customers: number;
  revenue: number;
}

const statCards = [
  { key: 'flights' as const, label: 'Ucuslar', sublabel: 'Haftalik periyot', icon: 'flight', badge: '+12%', badgeColor: 'text-secondary bg-secondary-container', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', href: '/flights' },
  { key: 'hotels' as const, label: 'Oteller', sublabel: 'Yuksek doluluk', icon: 'hotel', badge: '%98', badgeColor: 'text-primary bg-primary-fixed', iconBg: 'bg-secondary-fixed', iconColor: 'text-secondary', href: '/hotels' },
  { key: 'vehicles' as const, label: 'Araclar', sublabel: 'Kirada hazir', icon: 'directions_car', badge: '', badgeColor: '', iconBg: 'bg-tertiary-fixed', iconColor: 'text-tertiary', href: '/vehicles' },
  { key: 'bookings' as const, label: 'Rezervasyon', sublabel: 'Aylik toplam', icon: 'confirmation_number', badge: '+18.4%', badgeColor: 'text-secondary bg-secondary-container', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', href: '/bookings' },
  { key: 'customers' as const, label: 'Musteriler', sublabel: 'Yeni kayitlar', icon: 'group', badge: '+124', badgeColor: 'text-secondary bg-secondary-container', iconBg: 'bg-tertiary-fixed', iconColor: 'text-tertiary', href: '/customers' },
  { key: 'revenue' as const, label: 'Gelir & Ciro', sublabel: 'Net tahsilat', icon: 'payments', badge: '+22.8%', badgeColor: 'text-secondary bg-secondary-container', iconBg: 'bg-secondary-fixed', iconColor: 'text-secondary', href: '/payments' },
];

const recentBookings = [
  { id: '#WY-8921', customer: 'Can Demir', status: 'ONAYLANDI', statusColor: 'bg-secondary-container text-on-secondary-container', items: ['Ucus', 'Otel (Rixos Premium)', 'Arac (SUV)'], amount: '₺14.750,00' },
  { id: '#WY-8920', customer: 'Elif Kaya', status: 'BEKLEYEN', statusColor: 'bg-surface-container-high text-on-surface-variant', items: ['Ucus (Gidis-Donus)', 'Otel (Hilton Dalaman)'], amount: '₺9.820,00' },
  { id: '#WY-8919', customer: 'Burak Senturk', status: 'ONAYLANDI', statusColor: 'bg-secondary-container text-on-secondary-container', items: ['Ucus', 'Transfer Dahil'], amount: '₺4.490,00' },
];

export default function Dashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState<DashboardStats>({
    flights: 0, hotels: 0, vehicles: 0, bookings: 0, customers: 0, revenue: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadStats() {
      try {
        const [flights, hotels, vehicles, bookings, customers, payments] = await Promise.allSettled([
          api.get<Flight[]>('/api/flights'),
          api.get<Hotel[]>('/api/hotels'),
          api.get<Vehicle[]>('/api/vehicles'),
          api.get<PackageBooking[]>('/api/bookings'),
          api.get<Customer[]>('/api/customers'),
          api.get<Payment[]>('/api/payments'),
        ]);
        setStats({
          flights: flights.status === 'fulfilled' ? flights.value.data.length : 0,
          hotels: hotels.status === 'fulfilled' ? hotels.value.data.length : 0,
          vehicles: vehicles.status === 'fulfilled' ? vehicles.value.data.length : 0,
          bookings: bookings.status === 'fulfilled' ? bookings.value.data.length : 0,
          customers: customers.status === 'fulfilled' ? customers.value.data.length : 0,
          revenue: payments.status === 'fulfilled' ? payments.value.data.reduce((sum: number, p: Payment) => sum + (p.amount || 0), 0) : 0,
        });
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Veri yuklenemedi');
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  const today = new Date().toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' });

  return (
    <div className="space-y-space-md">
      {/* Welcome Banner */}
      <section className="bg-surface-container-low p-space-md rounded-xl shadow-sm relative overflow-hidden">
        <div className="flex items-center justify-between">
          <div className="inline-flex items-center gap-space-xs px-2.5 py-1 rounded-full bg-surface-container text-on-surface-variant text-xs font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
            <span>Waylo Global Turizm A.S.</span>
          </div>
          <span className="text-xs text-outline">{today}</span>
        </div>
        <div className="pt-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">
            Hos geldin, {user?.firstName || 'Admin'} 👋
          </h2>
          <p className="text-sm text-on-surface-variant">Turizm operasyonlari ve rezervasyon akisinda bugun yuksek hareketlilik var.</p>
        </div>
        {/* Quick Actions */}
        <div className="flex items-center gap-space-xs pt-space-sm overflow-x-auto no-scrollbar pb-1">
          <Link href="/flights" className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-primary text-on-primary text-xs font-medium shadow-sm hover:bg-primary-container active:scale-95 transition-all shrink-0">
            <span className="material-symbols-outlined text-[16px]">add</span>
            <span>Yeni Ucus</span>
          </Link>
          <Link href="/hotels" className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-lowest text-on-surface text-xs font-medium shadow-sm hover:bg-surface-container active:scale-95 transition-all shrink-0">
            <span className="material-symbols-outlined text-[16px] text-secondary">hotel</span>
            <span>Yeni Otel</span>
          </Link>
          <Link href="/vehicles" className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-lowest text-on-surface text-xs font-medium shadow-sm hover:bg-surface-container active:scale-95 transition-all shrink-0">
            <span className="material-symbols-outlined text-[16px] text-tertiary">directions_car</span>
            <span>Yeni Arac</span>
          </Link>
          <Link href="/bookings" className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-lowest text-on-surface text-xs font-medium shadow-sm hover:bg-surface-container active:scale-95 transition-all shrink-0">
            <span className="material-symbols-outlined text-[16px] text-primary">confirmation_number</span>
            <span>Yeni Rezervasyon</span>
          </Link>
        </div>
      </section>

      {error && (
        <div className="p-3 bg-error-container rounded-lg text-on-error-container text-sm flex items-center gap-2">
          <span className="material-symbols-outlined text-base">warning</span>
          Backend baglantisi kurulamadi. <code className="bg-on-error-container/10 px-1 rounded text-xs">docker compose start</code> calistirin.
        </div>
      )}

      {/* 6 Key Stat Cards */}
      <section className="grid grid-cols-2 lg:grid-cols-3 gap-space-sm">
        {statCards.map(({ key, label, sublabel, icon, badge, badgeColor, iconBg, iconColor, href }) => (
          <Link key={key} href={href}
            className="flex flex-col justify-between p-space-sm bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow group">
            <div className="flex items-center justify-between mb-1">
              <div className={`w-8 h-8 rounded-lg ${iconBg} flex items-center justify-center`}>
                <span className={`material-symbols-outlined ${iconColor} text-[18px]`}>{icon}</span>
              </div>
              {badge && (
                <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${badgeColor}`}>{badge}</span>
              )}
            </div>
            <span className="text-xs text-on-surface-variant mt-1">{label}</span>
            <span className="text-2xl font-bold text-on-surface tracking-tight">
              {loading ? (
                <span className="inline-block w-12 h-7 bg-surface-container animate-pulse rounded" />
              ) : key === 'revenue' ? (
                `₺${stats[key].toLocaleString('tr-TR')}`
              ) : (
                stats[key]
              )}
            </span>
            <span className="text-xs text-on-surface-variant">{sublabel}</span>
          </Link>
        ))}
      </section>

      {/* Revenue Chart Placeholder */}
      <section className="bg-surface-container-lowest rounded-xl shadow-sm p-space-md">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-on-surface">Haftalik Gelir Ozeti</h3>
            <p className="text-xs text-on-surface-variant">Son 7 gunluk satis hacmi dagilimi</p>
          </div>
          <span className="text-xs font-medium text-primary px-2.5 py-1 bg-primary-fixed rounded-full">Bu Hafta</span>
        </div>
        <div className="flex items-end gap-2 h-40">
          {[180, 240, 310, 290, 420, 510, 380].map((val, i) => {
            const days = ['Pzt', 'Sal', 'Car', 'Per', 'Cum', 'Cmt', 'Paz'];
            const maxVal = 510;
            const height = (val / maxVal) * 100;
            const isHighest = val === maxVal;
            return (
              <div key={i} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-[10px] text-on-surface-variant font-medium">{val}k</span>
                <div
                  className={`w-full rounded-t-md transition-all ${isHighest ? 'bg-primary' : 'bg-primary-fixed'}`}
                  style={{ height: `${height}%` }}
                />
                <span className={`text-[10px] font-medium ${isHighest ? 'text-primary font-bold bg-primary-fixed px-1.5 py-0.5 rounded' : 'text-on-surface-variant'}`}>
                  {days[i]}
                </span>
              </div>
            );
          })}
        </div>
        <div className="mt-3 flex items-center gap-2 text-xs text-on-surface-variant">
          <span className="w-2.5 h-2.5 rounded bg-primary inline-block" />
          En Yuksek: Cumartesi
          <span className="ml-auto">Ortalama: ₺362.8k/gun</span>
        </div>
      </section>

      {/* Recent Bookings */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-base font-semibold text-on-surface flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-xl">bookmark</span>
            Son Rezervasyonlar
          </h3>
          <Link href="/bookings" className="text-xs font-medium text-primary hover:text-primary-container transition-colors">
            Detayli Liste
          </Link>
        </div>
        <div className="space-y-space-sm">
          {recentBookings.map(b => (
            <div key={b.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-space-md">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-on-surface-variant">{b.id}</span>
                  <span className="text-sm font-bold text-on-surface">{b.customer}</span>
                </div>
                <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${b.statusColor}`}>
                  {b.status}
                </span>
              </div>
              <div className="flex flex-wrap gap-1.5 mb-2">
                {b.items.map((item, i) => (
                  <span key={i} className="text-[11px] px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-medium">
                    {item}
                  </span>
                ))}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-on-surface-variant">Paket Tutari</span>
                <span className="text-lg font-bold text-on-surface">{b.amount}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
