'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Checkin } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { TicketCheck } from 'lucide-react';

export default function CheckinsPage() {
  const [checkins, setCheckins] = useState<Checkin[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<Checkin[]>('/api/checkins')
      .then(r => setCheckins(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <TicketCheck size={24} /> Check-in
        </h1>
        <p className="text-gray-500 text-sm mt-1">{checkins.length} check-in kaydi</p>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : checkins.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Check-in kaydi bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Koltuk</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                  <th className="px-4 py-3 text-left">Boarding Pass</th>
                  <th className="px-4 py-3 text-left">Check-in Zamani</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {checkins.map(c => (
                  <tr key={c.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium">{c.seatNumber || '-'}</td>
                    <td className="px-4 py-3"><StatusBadge status={c.status} /></td>
                    <td className="px-4 py-3">
                      {c.boardingPassUrl ? (
                        <a href={c.boardingPassUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline text-xs">Boarding Pass</a>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(c.checkedInAt)}</td>
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
