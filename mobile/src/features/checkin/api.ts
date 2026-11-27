import { api } from '@/api';

export interface OrderCheckin { id: string; orderItemId: string; seatNumber: string | null; status: string; checkedInAt: string | null }

export const checkinApi = {
  mine: () => api.get<OrderCheckin[]>('/api/checkins/me'),
  checkIn: (orderItemId: string) => api.post<OrderCheckin>(`/api/checkins/order-items/${encodeURIComponent(orderItemId)}`, {}),
};

/** Mirrors frontend/src/lib/checkin.ts (CHECKIN-CONTRACT.md): departure-24h .. departure-1h inclusive, INTERNAL supplier only. */
export function checkinWindow(departureAt: string, now: number, supplier?: string | null, itemCancelled = false, boarded = false): { open: boolean; reason: string | null } {
  if (supplier && supplier !== 'INTERNAL') return { open: false, reason: 'Bu uçuşun check-in işlemi havayolunda yapılır' };
  if (itemCancelled) return { open: false, reason: 'İptal edilen bilet için check-in yapılamaz' };
  if (boarded) return { open: false, reason: 'Uçuşa binildi' };
  const dep = Date.parse(departureAt);
  if (Number.isNaN(dep)) return { open: false, reason: 'Kalkış saati bilinmiyor' };
  if (now < dep - 24 * 3600_000) return { open: false, reason: 'Online check-in kalkıştan 24 saat önce açılır' };
  if (now > dep - 3600_000) return { open: false, reason: 'Online check-in kalkıştan 1 saat önce kapanır' };
  return { open: true, reason: null };
}
