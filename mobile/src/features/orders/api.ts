import { api, type CancellationPreview, type CartItem, type Order, type PaymentIntent, type Voucher } from '@/api';
import { toPackageRequest } from '@/features/cart/CartContext';

const e = encodeURIComponent;

export const ordersApi = {
  list: () => api.get<Order[]>('/api/orders'),
  get: (id: string) => api.get<Order>(`/api/orders/${e(id)}`),
  vouchers: (id: string) => api.get<Voucher[]>(`/api/orders/${e(id)}/vouchers`),
  cancelPreview: (id: string) => api.get<CancellationPreview>(`/api/orders/${e(id)}/cancellation-preview`),
  cancel: (id: string) => api.post(`/api/orders/${e(id)}/cancel`, {}),
  createPackage: (items: CartItem[], promo?: string, guest?: { firstName?: string | null; lastName?: string | null } | null) => api.post<{ id: string }>('/api/orders/package', toPackageRequest(items, promo, guest ?? undefined)),
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
  if (err.code === 'ADMIN_CANNOT_PURCHASE') return 'Platform yöneticisi hesabı ile satın alma yapılamaz.';
  if (err.code === 'COMMISSION_RATE_MISSING') return 'Bu ürün için satış henüz açık değil.';
  if (err.code === 'HOTEL_NOT_BOOKABLE') return 'Bu otel şu anda rezervasyona açık değil.';
  if (err.status === 0) return 'Sunucuya bağlanılamıyor. Fiyat ve stok için internet gerekir.';
  if (err.status === 409) return 'Müsaitlik kalmadı.';
  if (err.status === 422) return 'Teklifin süresi dolmuş olabilir.';
  if (err.status === 503) return 'Bir servis şu an yanıt vermiyor, tekrar deneyin.';
  return err.message || 'İşlem başarısız.';
}
