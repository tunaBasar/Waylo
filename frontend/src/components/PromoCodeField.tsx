'use client';

import { useState } from 'react';
import { ApiError } from '@/lib/api';
import { normalizePromoCode, pricingApi, totalDiscount, type PreviewResponse, type PricingVertical } from '@/lib/pricing';

const money = (v: number, currency: string) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v);

/**
 * Promo code input for checkouts. "Uygula" asks pricing-service for an advisory preview. The code is sent with the order
 * either way and validated for real at order creation, so a failed preview never blocks the purchase.
 */
export function PromoCodeField({ vertical, supplierOrganizationId, grossAmount, currency, code, onCodeChange }: {
  vertical: PricingVertical;
  supplierOrganizationId?: string;
  grossAmount: number;
  currency: string;
  code: string;
  onCodeChange: (code: string) => void;
}) {
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function apply() {
    const promoCode = normalizePromoCode(code);
    if (!promoCode) return;
    setBusy(true);
    setMessage('');
    setPreview(null);
    try {
      const res = await pricingApi.preview({ vertical, supplierOrganizationId, grossAmount, currency, promoCode });
      setPreview(res.data);
    } catch (cause) {
      setMessage(cause instanceof ApiError && cause.status === 422
        ? 'Kod bu siparis icin gecerli gorunmuyor.'
        : 'Indirim onizlemesi alinamadi, kod siparis sirasinda dogrulanir.');
    } finally {
      setBusy(false);
    }
  }

  const discount = preview ? totalDiscount(preview.discountLines) : 0;
  return (
    <div className="space-y-1">
      <label className="block text-xs font-medium text-on-surface-variant">Promosyon kodu</label>
      <div className="flex gap-2">
        <input value={code} onChange={e => { onCodeChange(e.target.value); setPreview(null); setMessage(''); }}
          maxLength={50} placeholder="Kod (opsiyonel)"
          className="flex-1 rounded-lg bg-surface-container px-3 py-2 text-sm" />
        <button type="button" onClick={apply} disabled={busy || !normalizePromoCode(code)}
          className="rounded-lg bg-secondary-container px-3 py-2 text-sm font-medium text-on-secondary-container disabled:opacity-50">
          {busy ? '...' : 'Uygula'}
        </button>
      </div>
      {message && <p className="text-xs text-error">{message}</p>}
      {preview && discount > 0 && (
        <p className="text-xs text-on-surface-variant">
          Indirim: <span className="font-medium text-on-surface">-{money(discount, currency)}</span>
          {' '}· Odenecek: <span className="font-medium text-on-surface">{money(preview.netAmount, currency)}</span> (vergiler dahil)
        </p>
      )}
      {preview && discount === 0 && <p className="text-xs text-on-surface-variant">Bu kod icin indirim bulunamadi.</p>}
    </div>
  );
}
