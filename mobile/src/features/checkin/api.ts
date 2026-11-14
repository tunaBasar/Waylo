import { api } from '@/api';

export interface OrderCheckin { id: string; orderItemId: string; seatNumber: string | null; status: string; checkedInAt: string | null }

export const checkinApi = {
  mine: () => api.get<OrderCheckin[]>('/api/checkins/me'),
  checkIn: (orderItemId: string) => api.post<OrderCheckin>(`/api/checkins/order-items/${encodeURIComponent(orderItemId)}`, {}),
};

/** Mirrors frontend/src/lib/checkin.ts (CHECKIN-CONTRACT.md): departure-24h .. departure-1h inclusive, INTERNAL supplier only. */
export function checkinWindow(departureAt: string, now: number, supplier?: string | null, itemCancelled = false, boarded = false): { open: boolean; reason: string | null } {
  if (supplier && supplier !== 'INTERNAL') return { open: false, reason: 'Bu ucusun check-in islemi havayolunda yapilir' };
  if (itemCancelled) return { open: false, reason: 'Iptal edilen bilet icin check-in yapilamaz' };
  if (boarded) return { open: false, reason: 'Ucusa binildi' };
  const dep = Date.parse(departureAt);
  if (Number.isNaN(dep)) return { open: false, reason: 'Kalkis saati bilinmiyor' };
  if (now < dep - 24 * 3600_000) return { open: false, reason: 'Online check-in kalkistan 24 saat once acilir' };
  if (now > dep - 3600_000) return { open: false, reason: 'Online check-in kalkistan 1 saat once kapanir' };
  return { open: true, reason: null };
}
