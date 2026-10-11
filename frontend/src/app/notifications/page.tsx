'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Notification } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { Bell } from 'lucide-react';

export default function NotificationsPage() {
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<Notification[]>('/api/notifications')
      .then(r => setItems(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Bell size={24} /> Bildirimler
        </h1>
        <p className="text-gray-500 text-sm mt-1">{items.length} bildirim</p>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : items.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Bildirim bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Tip</th>
                  <th className="px-4 py-3 text-left">Alici</th>
                  <th className="px-4 py-3 text-left">Konu</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                  <th className="px-4 py-3 text-left">Tarih</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {items.map(n => (
                  <tr key={n.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3"><StatusBadge status={n.type} /></td>
                    <td className="px-4 py-3 text-gray-600">{n.recipient}</td>
                    <td className="px-4 py-3 text-gray-900">{n.subject || '-'}</td>
                    <td className="px-4 py-3"><StatusBadge status={n.status} /></td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(n.sentAt || n.createdAt)}</td>
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
