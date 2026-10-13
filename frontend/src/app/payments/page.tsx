'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Payment } from '@/lib/types';

const statusConfig: Record<string, { label: string; class: string; icon: string }> = {
  COMPLETED: { label: 'Tamamlandi', class: 'bg-secondary-container text-on-secondary-container', icon: 'check_circle' },
  PENDING: { label: 'Beklemede', class: 'bg-primary-fixed text-primary', icon: 'schedule' },
  FAILED: { label: 'Basarisiz', class: 'bg-error-container text-on-error-container', icon: 'error' },
  REFUNDED: { label: 'Iade', class: 'bg-tertiary-fixed text-on-tertiary-fixed-variant', icon: 'undo' },
};
const methodIcons: Record<string, string> = { CREDIT_CARD: 'credit_card', DEBIT_CARD: 'credit_card', BANK_TRANSFER: 'account_balance', MOCK: 'science' };

export default function PaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    api.get<Payment[]>('/api/payments').then(r => { setPayments(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = payments.filter(p => !search || p.transactionRef?.toLowerCase().includes(search.toLowerCase()) || p.bookingId?.toLowerCase().includes(search.toLowerCase()));

  const totalRevenue = payments.filter(p => p.status === 'COMPLETED').reduce((s, p) => s + p.amount, 0);

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Odemeler</h2>
          <span className="bg-secondary-container text-on-secondary-container text-xs font-semibold px-2.5 py-0.5 rounded-full">{payments.length}</span>
        </div>
      </div>

      {/* Revenue card */}
      <div className="bg-gradient-to-br from-primary to-primary-container rounded-xl p-4 shadow-md">
        <p className="text-primary-fixed text-xs font-medium mb-1">Toplam Gelir</p>
        <p className="text-2xl font-bold text-on-primary">₺{totalRevenue.toLocaleString('tr-TR')}</p>
        <p className="text-primary-fixed text-[10px] mt-1">{payments.filter(p => p.status === 'COMPLETED').length} basarili odeme</p>
      </div>

      <div className="relative shadow-sm rounded-xl bg-surface-container-lowest">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
        <input value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 bg-transparent rounded-xl text-on-surface text-sm placeholder:text-outline focus:outline-none"
          placeholder="Islem referansi veya booking ID ara..." />
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">payments</span><p className="text-on-surface-variant text-sm">Odeme bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(p => {
            const sc = statusConfig[p.status] || statusConfig.PENDING;
            return (
              <div key={p.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 rounded-lg bg-secondary-container flex items-center justify-center">
                      <span className="material-symbols-outlined text-secondary text-lg">{methodIcons[p.paymentMethod] || 'payments'}</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold text-on-surface">₺{p.amount.toLocaleString('tr-TR')}</p>
                      <p className="text-[10px] text-on-surface-variant">{p.transactionRef}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${sc.class}`}>{sc.label}</span>
                </div>
                <div className="flex items-center justify-between text-xs text-on-surface-variant">
                  <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">confirmation_number</span> #{p.bookingId.slice(0,8)}</span>
                  <span>{new Date(p.createdAt).toLocaleDateString('tr-TR')}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
