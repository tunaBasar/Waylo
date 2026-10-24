'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { adminApi } from '@/lib/admin';
import type { Payment } from '@/lib/types';
import { Card, LoadingRows } from '@/components/partner/ui';
import { Notice } from '@/components/Notice';
import { Suspense } from 'react';

interface Stat { key: string; label: string; icon: string; href: string; load: () => Promise<number> }

const len = (p: Promise<{ data: unknown[] }>) => p.then(r => r.data.length);

const stats: Stat[] = [
  { key: 'pending', label: 'Onay bekleyen isletme', icon: 'domain', href: '/admin/organizations', load: () => len(adminApi.organizations('PENDING')) },
  { key: 'flights', label: 'Ucuslar', icon: 'flight', href: '/admin/flights', load: () => len(adminApi.flights()) },
  { key: 'airports', label: 'Havalimanlari', icon: 'flight_takeoff', href: '/admin/airports', load: () => len(adminApi.airports()) },
  { key: 'vehicles', label: 'Araclar', icon: 'directions_car', href: '/admin/vehicles', load: () => len(adminApi.vehicles()) },
  { key: 'bookings', label: 'Rezervasyonlar', icon: 'confirmation_number', href: '/admin/bookings', load: () => len(api.get<unknown[]>('/api/bookings')) },
  { key: 'customers', label: 'Musteriler', icon: 'group', href: '/admin/customers', load: () => len(api.get<unknown[]>('/api/customers')) },
  { key: 'payments', label: 'Tamamlanan odemeler', icon: 'payments', href: '/admin/payments',
    load: () => api.get<Payment[]>('/api/payments').then(r => r.data.filter(p => p.status === 'COMPLETED').length) },
];

export default function AdminOverview() {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [failed, setFailed] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled(stats.map(s => s.load())).then(results => {
      if (cancelled) return;
      const next: Record<string, number> = {};
      const bad: string[] = [];
      results.forEach((r, i) => { if (r.status === 'fulfilled') next[stats[i].key] = r.value; else bad.push(stats[i].label); });
      setCounts(next);
      setFailed(bad);
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="space-y-space-md">
      <Suspense fallback={null}><Notice /></Suspense>
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Platform genel bakis</h2>
      {failed.length > 0 && (
        <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">Yuklenemedi: {failed.join(', ')}</p>
      )}
      {loading ? <LoadingRows /> : (
        <section className="grid grid-cols-2 lg:grid-cols-4 gap-space-sm">
          {stats.filter(s => counts[s.key] !== undefined).map(s => (
            <Link key={s.key} href={s.href}>
              <Card className="p-space-sm hover:shadow-md transition-shadow">
                <span className="material-symbols-outlined text-primary text-[20px]">{s.icon}</span>
                <p className="text-xs text-on-surface-variant mt-1">{s.label}</p>
                <p className="text-2xl font-bold text-on-surface tracking-tight">{counts[s.key]}</p>
              </Card>
            </Link>
          ))}
        </section>
      )}
    </div>
  );
}
