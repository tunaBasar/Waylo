'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { PackageBooking } from '@/lib/types';
import { formatDate, formatCurrency } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { Package, Search } from 'lucide-react';

export default function BookingsPage() {
  const [bookings, setBookings] = useState<PackageBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    api.get<PackageBooking[]>('/api/bookings')
      .then(r => setBookings(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const filtered = bookings.filter(b =>
    b.id.toLowerCase().includes(search.toLowerCase()) ||
    b.status.toLowerCase().includes(search.toLowerCase())
  );

  async function handleCancel(id: string) {
    try {
      const r = await api.post<PackageBooking>(`/api/bookings/${id}/cancel`, {});
      setBookings(prev => prev.map(b => b.id === id ? r.data : b));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Hata');
    }
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Package size={24} /> Rezervasyonlar
        </h1>
        <p className="text-gray-500 text-sm mt-1">{bookings.length} rezervasyon</p>
      </div>

      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text" placeholder="ID veya durum ara..." value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
        />
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Rezervasyon bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">ID</th>
                  <th className="px-4 py-3 text-left">Musteri</th>
                  <th className="px-4 py-3 text-left">Ucus</th>
                  <th className="px-4 py-3 text-left">Otel</th>
                  <th className="px-4 py-3 text-left">Arac</th>
                  <th className="px-4 py-3 text-left">Toplam</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                  <th className="px-4 py-3 text-left">Tarih</th>
                  <th className="px-4 py-3 text-left">Islem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map(b => (
                  <tr key={b.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{b.id.slice(0, 8)}...</td>
                    <td className="px-4 py-3 font-mono text-xs">{b.customerId.slice(0, 8)}...</td>
                    <td className="px-4 py-3">{b.flightId ? '&#9992;' : '-'}</td>
                    <td className="px-4 py-3">{b.hotelReservationId ? '&#127968;' : '-'}</td>
                    <td className="px-4 py-3">{b.vehicleReservationId ? '&#128663;' : '-'}</td>
                    <td className="px-4 py-3 font-medium">{formatCurrency(b.totalPrice)}</td>
                    <td className="px-4 py-3"><StatusBadge status={b.status} /></td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(b.createdAt)}</td>
                    <td className="px-4 py-3">
                      {b.status === 'PENDING' && (
                        <button onClick={() => handleCancel(b.id)} className="text-red-600 hover:text-red-800 text-xs font-medium">Iptal</button>
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
