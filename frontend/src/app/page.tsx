'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Flight, Hotel, Vehicle, PackageBooking, Customer, Payment } from '@/lib/types';
import {
  Plane, Building2, Car, Package, Users, CreditCard,
  TrendingUp, AlertCircle,
} from 'lucide-react';

interface DashboardStats {
  flights: number;
  hotels: number;
  vehicles: number;
  bookings: number;
  customers: number;
  payments: number;
}

const cards = [
  { key: 'flights' as const, label: 'Ucuslar', icon: Plane, color: 'bg-blue-500', href: '/flights' },
  { key: 'hotels' as const, label: 'Oteller', icon: Building2, color: 'bg-emerald-500', href: '/hotels' },
  { key: 'vehicles' as const, label: 'Araclar', icon: Car, color: 'bg-orange-500', href: '/vehicles' },
  { key: 'bookings' as const, label: 'Rezervasyonlar', icon: Package, color: 'bg-purple-500', href: '/bookings' },
  { key: 'customers' as const, label: 'Musteriler', icon: Users, color: 'bg-pink-500', href: '/customers' },
  { key: 'payments' as const, label: 'Odemeler', icon: CreditCard, color: 'bg-indigo-500', href: '/payments' },
];

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats>({
    flights: 0, hotels: 0, vehicles: 0, bookings: 0, customers: 0, payments: 0,
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
          payments: payments.status === 'fulfilled' ? payments.value.data.length : 0,
        });
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Veri yuklenemedi');
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">Waylo platform genel bakis</p>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-3">
          <AlertCircle className="text-amber-500" size={20} />
          <p className="text-amber-700 text-sm">
            Backend baglantisi kurulamadi. <code className="bg-amber-100 px-1 rounded">docker compose up -d</code> calistirdiginizdan emin olun.
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {cards.map(({ key, label, icon: Icon, color, href }) => (
          <a
            key={key}
            href={href}
            className="bg-white rounded-xl border border-gray-200 p-6 hover:shadow-md transition-shadow group"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">{label}</p>
                <p className="text-3xl font-bold text-gray-900 mt-1">
                  {loading ? (
                    <span className="inline-block w-12 h-8 bg-gray-200 animate-pulse rounded" />
                  ) : (
                    stats[key]
                  )}
                </p>
              </div>
              <div className={`${color} p-3 rounded-lg text-white group-hover:scale-110 transition-transform`}>
                <Icon size={24} />
              </div>
            </div>
            <div className="mt-4 flex items-center text-sm text-gray-500">
              <TrendingUp size={14} className="mr-1" />
              Detaylari gor
            </div>
          </a>
        ))}
      </div>
    </div>
  );
}
