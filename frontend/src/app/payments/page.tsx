'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Payment } from '@/lib/types';
import { formatDate, formatCurrency } from '@/lib/utils';
import StatusBadge from '@/components/StatusBadge';
import { CreditCard } from 'lucide-react';

export default function PaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<Payment[]>('/api/payments')
      .then(r => setPayments(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  async function handleRefund(id: string) {
    try {
      const r = await api.post<Payment>(`/api/payments/${id}/refund`, {});
      setPayments(prev => prev.map(p => p.id === id ? r.data : p));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Hata');
    }
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <CreditCard size={24} /> Odemeler
        </h1>
        <p className="text-gray-500 text-sm mt-1">{payments.length} odeme kaydi</p>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Yukleniyor...</div>
        ) : payments.length === 0 ? (
          <div className="p-8 text-center text-gray-500">Odeme bulunamadi</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Islem Ref</th>
                  <th className="px-4 py-3 text-left">Tutar</th>
                  <th className="px-4 py-3 text-left">Yontem</th>
                  <th className="px-4 py-3 text-left">Durum</th>
                  <th className="px-4 py-3 text-left">Tarih</th>
                  <th className="px-4 py-3 text-left">Islem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {payments.map(p => (
                  <tr key={p.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{p.transactionRef}</td>
                    <td className="px-4 py-3 font-semibold">{formatCurrency(p.amount, p.currency)}</td>
                    <td className="px-4 py-3 text-gray-600">{p.paymentMethod}</td>
                    <td className="px-4 py-3"><StatusBadge status={p.status} /></td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(p.createdAt)}</td>
                    <td className="px-4 py-3">
                      {p.status === 'COMPLETED' && (
                        <button onClick={() => handleRefund(p.id)} className="text-purple-600 hover:text-purple-800 text-xs font-medium">Iade</button>
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
