'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { api, ApiError } from '@/lib/api';

interface Order {
  id: string;
  status: string;
  currency: string;
  quoteId: string | null;
  totalAmount: number;
  createdAt: string;
  holdExpiresAt?: string | null;
}

function OrdersContent() {
  const searchParams = useSearchParams();
  const createdId = searchParams.get('created');
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<Order[]>('/api/orders').then(response => setOrders(response.data)).catch((err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Siparisler yuklenemedi');
    });
  }, []);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Siparislerim</h2>
      {createdId && <p className="rounded-xl bg-tertiary-container p-3 text-sm text-on-tertiary-container">Siparisiniz olusturuldu: {createdId}</p>}
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {orders.length === 0 && !error ? (
        <p className="text-sm text-on-surface-variant">Siparis bulunamadi</p>
      ) : (
        <div className="space-y-3">
          {orders.map(order => (
            <div key={order.id} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-on-surface">#{order.id}</span>
                <span className="text-xs text-on-surface-variant">{order.status}</span>
              </div>
              <p className="mt-2 text-lg font-bold text-primary">{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: order.currency }).format(order.totalAmount)}</p>
              {order.status === 'PENDING' && order.holdExpiresAt && (
                <p className="text-xs font-medium text-error">
                  {new Date(order.holdExpiresAt).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' })} saate kadar odeme yapin
                </p>
              )}
              <p className="text-xs text-on-surface-variant">{new Date(order.createdAt).toLocaleDateString('tr-TR')}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function OrdersPage() {
  return <Suspense fallback={<p className="text-sm text-on-surface-variant">Yukleniyor...</p>}><OrdersContent /></Suspense>;
}
