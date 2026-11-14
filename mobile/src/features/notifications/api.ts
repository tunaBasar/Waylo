import { api } from '@/api';

export interface AppNotification {
  id: string; type: string; subject: string | null; body: string; status: string; createdAt: string; sentAt: string | null;
}
/** GET /api/notifications/me, customer-scoped by X-User-Id. */
export const notificationsApi = { mine: () => api.get<AppNotification[]>('/api/notifications/me') };
