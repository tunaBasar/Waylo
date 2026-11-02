'use client';

import { useEffect, useState } from 'react';
import { adminApi, type AdminOrder, type AdminOrderPage } from '@/lib/admin';
import { useAuth } from '@/lib/auth';

const statusConfig: Record<string, { label: string; class: string }> = {
  CONFIRMED: { label: 'Onaylandi', class: 'bg-secondary-container text-on-secondary-container' },
  PENDING: { label: 'Beklemede', class: 'bg-primary-fixed text-primary' },
  CANCELLED: { label: 'Iptal', class: 'bg-error-container text-on-error-container' },
  FAILED: { label: 'Basarisiz', class: 'bg-error-container text-on-error-container' },
};
const paymentLabel: Record<string, string> = {
  UNPAID: 'Odenmedi', PAID: 'Odendi', PARTIALLY_REFUNDED: 'Kismi iade', REFUNDED: 'Iade edildi',
};
const verticalLabel: Record<string, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };
const PAGE_SIZE = 20;

function AdminOnly() {
  return (
    <div className="text-center py-12">
      <span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span>
      <p className="text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>
    </div>
  );
}

export default function BookingsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [data, setData] = useState<AdminOrderPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [status, setStatus] = useState('');
  const [vertical, setVertical] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    setLoading(true);
    adminApi.orders({ status, vertical, from, to, page, size: PAGE_SIZE })
      .then(r => { if (!cancelled) { setData(r.data); setFailed(false); } })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [isAdmin, status, vertical, from, to, page]);
  /* eslint-enable react-hooks/set-state-in-effect */

  if (!isAdmin) return <AdminOnly />;
  const orders: AdminOrder[] = data?.items ?? [];
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;
  const sel = 'rounded-lg bg-surface-container-lowest px-3 py-2 text-sm shadow-sm';

  return (
    <div className="space-y-space-md">
      <div className="flex items-center gap-space-xs">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">Siparisler</h2>
        {data && <span className="bg-primary-fixed text-primary text-xs font-semibold px-2.5 py-0.5 rounded-full">{data.total}</span>}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <select aria-label="Durum" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }} className={sel}>
          <option value="">Tum durumlar</option>
          {Object.entries(statusConfig).map(([k, c]) => <option key={k} value={k}>{c.label}</option>)}
        </select>
        <select aria-label="Dikey" value={vertical} onChange={e => { setVertical(e.target.value); setPage(0); }} className={sel}>
          <option value="">Tum dikeyler</option>
          {Object.entries(verticalLabel).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <input aria-label="Baslangic" type="date" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} className={sel} />
        <input aria-label="Bitis" type="date" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} className={sel} />
      </div>

      {failed && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">Siparisler yuklenemedi</p>}
      {loading ? (
        <p className="text-sm text-on-surface-variant">Yukleniyor...</p>
      ) : orders.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">inventory_2</span><p className="text-on-surface-variant text-sm">Siparis bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {orders.map(o => {
            const sc = statusConfig[o.status] ?? statusConfig.PENDING;
            return (
              <div key={o.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <p className="text-sm font-bold text-on-surface" title={o.id}>#{o.id.slice(0, 8)}</p>
                    <p className="text-[10px] text-on-surface-variant">{new Date(o.createdAt).toLocaleString('tr-TR')} · Musteri {o.customerId.slice(0, 8)}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-semibold px-2 py-1 rounded-full bg-surface-container text-on-surface-variant">{paymentLabel[o.paymentState] ?? o.paymentState}</span>
                    <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${sc.class}`}>{sc.label}</span>
                  </div>
                </div>
                <ul className="mb-2 space-y-1">
                  {o.items.map(i => (
                    <li key={i.id} className="flex items-center justify-between text-xs text-on-surface-variant">
                      <span><span className="font-medium text-on-surface">{verticalLabel[i.vertical] ?? i.vertical}</span> · {i.description}{i.promoCode ? ` · ${i.promoCode}` : ''}{i.cancelOutcome === 'REFUND' ? ' · iade' : ''}</span>
                      <span>{i.netAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                    </li>
                  ))}
                </ul>
                <p className="text-lg font-bold text-primary">{o.totalAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} {o.currency}</p>
              </div>
            );
          })}
        </div>
      )}

      <div className="flex items-center justify-between">
        <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="rounded-lg px-3 py-1.5 text-sm bg-surface-container-lowest shadow-sm disabled:opacity-50">Onceki</button>
        <span className="text-xs text-on-surface-variant">Sayfa {page + 1} / {pages}</span>
        <button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} className="rounded-lg px-3 py-1.5 text-sm bg-surface-container-lowest shadow-sm disabled:opacity-50">Sonraki</button>
      </div>
    </div>
  );
}
