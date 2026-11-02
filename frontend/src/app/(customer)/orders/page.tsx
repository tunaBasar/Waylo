'use client';

import { Suspense, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import QRCode from 'qrcode';
import { api, ApiError } from '@/lib/api';

// ── Local types ───────────────────────────────────────────────────────────────

interface OrderItem {
  id: string;
  vertical: 'HOTEL' | 'CAR' | 'FLIGHT' | string;
  description: string;
  unitAmount: number;
  quantity: number;
  subtotal: number;
  currency: string;
  pickupAt: string | null;
  returnAt: string | null;
}

interface Order {
  id: string;
  status: string;
  currency: string;
  quoteId: string | null;
  totalAmount: number;
  createdAt: string;
  holdExpiresAt?: string | null;
  items?: OrderItem[];
}

interface Voucher {
  voucherId: string;
  orderItemId: string;
  description: string;
  payload: string | null;
  status: 'ACTIVE' | 'REDEEMED' | 'VOID';
  validFrom: string;
  validUntil: string;
  redeemedAt: string | null;
  pickupAt?: string | null;
  returnAt?: string | null;
}

interface Intent {
  id: string;
  testMode: boolean;
  status: string;
}

interface OrderPayment {
  intent: Intent | null;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Format an offsetless local datetime (e.g. "2026-11-06T09:00:00") as dd.MM.yyyy HH:mm */
function formatOffsetDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const match = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):\d{2}$/);
  if (!match) return iso;
  const [, year, month, day, hour, minute] = match;
  return `${day}.${month}.${year} ${hour}:${minute}`;
}

const VOUCHER_STATUS_TR: Record<Voucher['status'], string> = {
  ACTIVE: 'Gecerli',
  REDEEMED: 'Kullanildi',
  VOID: 'Iptal edildi',
};

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });

const STATUS_TR: Record<string, string> = {
  PENDING: 'Odeme bekleniyor',
  CONFIRMED: 'Onaylandi',
  CANCELLED: 'Iptal edildi',
  EXPIRED: 'Suresi doldu',
};

function statusLabel(status: string): string {
  return STATUS_TR[status] ?? status;
}

// ── Components ────────────────────────────────────────────────────────────────

function QrImage({ payload }: { payload: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(payload, { margin: 1, width: 220, errorCorrectionLevel: 'M' })
      .then(url => { if (alive) setSrc(url); })
      .catch(() => { if (alive) setSrc(null); });
    return () => { alive = false; };
  }, [payload]);
  // eslint-disable-next-line @next/next/no-img-element
  return src ? <img src={src} alt="Kupon QR kodu" width={220} height={220} className="rounded-lg bg-white" /> : null;
}

function VoucherList({ orderId, testMode }: { orderId: string; testMode: boolean }) {
  const [vouchers, setVouchers] = useState<Voucher[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<Voucher[]>(`/api/orders/${encodeURIComponent(orderId)}/vouchers`)
      .then(r => setVouchers(r.data))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : 'Kuponlar yuklenemedi'));
  }, [orderId]);

  if (error) return <p className="text-xs text-error">{error}</p>;
  if (!vouchers) return <p className="text-xs text-on-surface-variant">Kuponlar yukleniyor...</p>;
  return (
    <div className="mt-3 space-y-3">
      {testMode && <p className="rounded-lg bg-tertiary-container p-2 text-xs font-semibold text-on-tertiary-container">TEST ODEME: gercek para cekilmedi.</p>}
      {vouchers.map(v => (
        <div key={v.voucherId} className="rounded-xl bg-surface-container p-3 space-y-2">
          <p className="text-sm font-medium text-on-surface">{v.description}</p>
          <p className="text-xs text-on-surface-variant">
            {VOUCHER_STATUS_TR[v.status]} · Gecerlilik: {fmt(v.validFrom)} - {fmt(v.validUntil)}
            {v.redeemedAt && ` · Kullanim: ${fmt(v.redeemedAt)}`}
          </p>
          {v.pickupAt && v.returnAt && (
            <p className="text-xs text-on-surface-variant">
              Gidis: {formatOffsetDateTime(v.pickupAt)} · Donus: {formatOffsetDateTime(v.returnAt)}
            </p>
          )}
          {v.payload && <QrImage payload={v.payload} />}
          {v.payload && (
            <div className="flex flex-wrap items-center gap-2">
              <code className="break-all rounded-lg bg-surface-container-lowest px-2 py-1 text-xs text-on-surface">{v.payload}</code>
              <button type="button" onClick={() => void navigator.clipboard?.writeText(v.payload ?? '')}
                className="rounded-lg bg-secondary-container px-2 py-1 text-xs font-semibold text-on-secondary-container">
                Kodu kopyala
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

function PayPanel({ order, onPaid }: { order: Order; onPaid: () => void }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const pay = async (token: 'TEST_OK' | 'TEST_DECLINE') => {
    setBusy(true);
    setMsg(null);
    try {
      const intent = (await api.post<Intent>(`/api/payments/orders/${encodeURIComponent(order.id)}/intent`, {})).data;
      const confirmed = (await api.post<Intent>(`/api/payments/intents/${encodeURIComponent(intent.id)}/confirm`, { paymentToken: token })).data;
      if (token === 'TEST_DECLINE' || confirmed.status === 'FAILED') {
        setMsg('TEST ODEME reddedildi. Tekrar deneyebilirsiniz.');
      }
      onPaid();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'COMMISSION_RATE_MISSING') {
        setMsg('Bu urun icin satis henuz acik degil (komisyon orani tanimli degil). Lutfen daha sonra tekrar deneyin.');
      } else {
        setMsg(err instanceof ApiError ? err.message : 'Odeme basarisiz');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-3 space-y-2">
      <p className="rounded-lg bg-tertiary-container p-2 text-xs font-semibold text-on-tertiary-container">TEST ODEME: gercek kart bilgisi istenmez, para cekilmez.</p>
      <div className="flex flex-wrap gap-2">
        <button disabled={busy} onClick={() => void pay('TEST_OK')}
          className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-on-primary disabled:opacity-50">Odeme yap (TEST ODEME)</button>
        <button disabled={busy} onClick={() => void pay('TEST_DECLINE')}
          className="rounded-xl bg-surface-container px-4 py-2 text-sm font-semibold text-on-surface disabled:opacity-50">Reddedilen test odemesi</button>
      </div>
      {msg && <p className="text-xs text-error">{msg}</p>}
    </div>
  );
}

function OrderCard({ order, onChanged }: { order: Order; onChanged: () => void }) {
  const [testMode, setTestMode] = useState(false);

  useEffect(() => {
    if (order.status !== 'CONFIRMED') return;
    api.get<OrderPayment>(`/api/payments/orders/${encodeURIComponent(order.id)}`)
      .then(r => setTestMode(!!r.data.intent?.testMode))
      .catch(() => setTestMode(false));
  }, [order.id, order.status]);

  return (
    <div className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold text-on-surface">#{order.id}</span>
        <span className="text-xs text-on-surface-variant">{statusLabel(order.status)}</span>
      </div>
      <p className="mt-2 text-lg font-bold text-primary">{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: order.currency }).format(order.totalAmount)}</p>
      {order.items && order.items.map(item => (
        <div key={item.id} className="mt-1 text-xs text-on-surface-variant">
          <p>{item.description}</p>
          {item.vertical === 'CAR' && item.pickupAt && item.returnAt && (
            <p>Alis: {formatOffsetDateTime(item.pickupAt)} - Teslim: {formatOffsetDateTime(item.returnAt)}</p>
          )}
        </div>
      ))}
      {order.status === 'PENDING' && order.holdExpiresAt && (
        <p className="text-xs font-medium text-error">
          {fmt(order.holdExpiresAt)} saate kadar odeme yapin
        </p>
      )}
      <p className="text-xs text-on-surface-variant">{new Date(order.createdAt).toLocaleDateString('tr-TR')}</p>
      {order.status === 'PENDING' && <PayPanel order={order} onPaid={onChanged} />}
      {order.status === 'CONFIRMED' && <VoucherList orderId={order.id} testMode={testMode} />}
    </div>
  );
}

function OrdersContent() {
  const searchParams = useSearchParams();
  const createdId = searchParams.get('created');
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.get<Order[]>('/api/orders').then(response => setOrders(response.data)).catch((err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Siparisler yuklenemedi');
    });
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Siparislerim</h2>
      {createdId && <p className="rounded-xl bg-tertiary-container p-3 text-sm text-on-tertiary-container">Siparisiniz olusturuldu. 15 dakika icinde odeme yapin.</p>}
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {orders.length === 0 && !error ? (
        <p className="text-sm text-on-surface-variant">Siparis bulunamadi</p>
      ) : (
        <div className="space-y-3">
          {orders.map(order => <OrderCard key={order.id} order={order} onChanged={load} />)}
        </div>
      )}
    </div>
  );
}

export default function OrdersPage() {
  return <Suspense fallback={<p className="text-sm text-on-surface-variant">Yukleniyor...</p>}><OrdersContent /></Suspense>;
}
