'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { api, ApiError, ADMIN_CANNOT_PURCHASE_MESSAGE } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import {
  hasMixedCurrency, itemIndexMessage, loadCart, removeItem, saveCart, toPackageRequest, totalsByCurrency, type CartItem,
} from '@/lib/cart';
import { normalizePromoCode, promoErrorMessage } from '@/lib/pricing';

const VERTICAL_LABEL: Record<string, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };

function money(amount: number, currency: string) {
  return `${amount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} ${currency}`;
}

export default function CartPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [items, setItems] = useState<CartItem[]>([]);
  const [ready, setReady] = useState(false);
  const [promo, setPromo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(() => setItems(loadCart()), []);
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    refresh();
    setReady(true);
    window.addEventListener('waylo-cart', refresh);
    return () => window.removeEventListener('waylo-cart', refresh);
  }, [refresh]);
  /* eslint-enable react-hooks/set-state-in-effect */

  async function buy() {
    if (authLoading || !user) { router.push('/login'); return; }
    setBusy(true);
    setError('');
    try {
      const res = await api.post<{ id: string }>('/api/orders/package',
        toPackageRequest(items, promo.trim() ? normalizePromoCode(promo) : undefined));
      saveCart([]);
      router.push(`/orders?created=${encodeURIComponent(res.data.id)}`);
    } catch (err) {
      if (err instanceof ApiError) {
        let which: string | null = null;
        const m = /^Item (\d+):/.exec(err.message);
        if (m) which = itemIndexMessage(Number(m[1]), items);
        const base = err.code === 'ADMIN_CANNOT_PURCHASE' ? ADMIN_CANNOT_PURCHASE_MESSAGE
          : promoErrorMessage(err.code) ?? (err.status === 409 ? 'Musaitlik kalmadi'
            : err.status === 422 ? 'Teklifin suresi dolmus olabilir' : err.status === 503 ? 'Bir servis su an yanit vermiyor, tekrar deneyin' : 'Sepet satin alinamadi');
        setError(which ? `${which}: ${base}. Hicbir ayirma yapilmadi.` : base);
      } else {
        setError('Sepet satin alinamadi');
      }
    } finally {
      setBusy(false);
    }
  }

  if (!ready) return <p className="text-sm text-on-surface-variant">Yukleniyor...</p>;
  const totals = Object.entries(totalsByCurrency(items));
  const mixed = hasMixedCurrency(items);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Sepet</h2>
      {items.length === 0 ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">
          Sepetiniz bos. <Link href="/hotels" className="text-primary underline">Otel</Link>, <Link href="/vehicles" className="text-primary underline">arac</Link>,{' '}
          <Link href="/flights" className="text-primary underline">ucus</Link> veya <Link href="/bus" className="text-primary underline">otobus</Link> ekleyin.
        </p>
      ) : (
        <>
          <ul className="space-y-2">
            {items.map(i => (
              <li key={i.id} className="flex items-center justify-between rounded-xl bg-surface-container-lowest p-4 shadow-sm">
                <div>
                  <p className="text-xs font-semibold uppercase text-on-surface-variant">{VERTICAL_LABEL[i.vertical] ?? i.vertical}</p>
                  <p className="text-sm text-on-surface">{i.label}</p>
                  {i.amount != null && i.currency && <p className="text-sm font-semibold text-primary">{money(i.amount, i.currency)}</p>}
                </div>
                <button onClick={() => saveCart(removeItem(items, i.id))} className="text-sm text-error underline">Kaldir</button>
              </li>
            ))}
          </ul>
          <div className="rounded-xl bg-surface-container-lowest p-4 shadow-sm space-y-3">
            <div>
              <label className="text-xs text-on-surface-variant" htmlFor="cart-promo">Promosyon kodu</label>
              <input id="cart-promo" value={promo} onChange={e => setPromo(e.target.value)} maxLength={50}
                className="mt-1 w-full rounded-lg bg-surface-container px-3 py-2 text-sm" placeholder="Kod (istege bagli)" />
              <p className="mt-1 text-xs text-on-surface-variant">Kod sepette bir kalemde uygulanir.</p>
            </div>
            {mixed && <p className="text-sm text-error">Sepetteki kalemler farkli para birimlerinde, birlikte satin alinamaz.</p>}
            {totals.map(([cur, amt]) => (
              <p key={cur} className="text-sm text-on-surface">Toplam (indirim oncesi): <span className="font-semibold">{money(amt, cur)}</span></p>
            ))}
            <p className="text-xs text-on-surface-variant">Kesin fiyat satin alirken tedarikcilerden alinir. Tek odeme yapilir, her kalemin ayri QR kodu olur.</p>
            {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
            <button onClick={buy} disabled={busy || mixed}
              className="w-full rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-50">
              {busy ? 'Isleniyor...' : 'Sepeti satin al'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
