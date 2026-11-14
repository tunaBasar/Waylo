import { api, type CancellationPreview, type Order, type PaymentIntent, type Voucher } from '@/api';
import { toPackageRequest } from '@/features/cart/CartContext';
import type { CartItem } from '@/api';

const e = encodeURIComponent;

export const ordersApi = {
  list: () => api.get<Order[]>('/api/orders'),
  get: (id: string) => api.get<Order>(`/api/orders/${e(id)}`),
  vouchers: (id: string) => api.get<Voucher[]>(`/api/orders/${e(id)}/vouchers`),
  cancelPreview: (id: string) => api.get<CancellationPreview>(`/api/orders/${e(id)}/cancellation-preview`),
  cancel: (id: string) => api.post(`/api/orders/${e(id)}/cancel`, {}),
  createPackage: (items: CartItem[], promo?: string) => api.post<{ id: string }>('/api/orders/package', toPackageRequest(items, promo)),
  createIntent: (orderId: string) => api.post<PaymentIntent>(`/api/payments/orders/${e(orderId)}/intent`, {}),
  confirmIntent: (intentId: string, paymentToken: string) =>
    api.post<PaymentIntent>(`/api/payments/intents/${e(intentId)}/confirm`, { paymentToken }),
};

/** Sandbox payment. Returns the order as the backend sees it afterwards: callers must trust only order.status. */
export async function paySandbox(orderId: string, token: 'TEST_OK' | 'TEST_DECLINE' = 'TEST_OK'): Promise<Order> {
  const intent = await ordersApi.createIntent(orderId);
  await ordersApi.confirmIntent(intent.id, token);
  return ordersApi.get(orderId);
}

export function checkoutErrorMessage(err: { status?: number; code?: string | null; message?: string }): string {
  if (err.code === 'ADMIN_CANNOT_PURCHASE') return 'Platform yoneticisi hesabi ile satin alma yapilamaz.';
  if (err.code === 'COMMISSION_RATE_MISSING') return 'Bu urun icin satis henuz acik degil.';
  if (err.status === 0) return 'Sunucuya baglanilamiyor. Fiyat ve stok icin internet gerekir.';
  if (err.status === 409) return 'Musaitlik kalmadi.';
  if (err.status === 422) return 'Teklifin suresi dolmus olabilir.';
  if (err.status === 503) return 'Bir servis su an yanit vermiyor, tekrar deneyin.';
  return err.message || 'Islem basarisiz.';
}
