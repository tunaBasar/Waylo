import { api } from '@/api';

export type SupportStatus = 'OPEN' | 'ANSWERED' | 'CLOSED';
export type SupportRole = 'CUSTOMER' | 'PARTNER' | 'ADMIN';

export interface SupportMessage {
  id: string;
  authorRole: SupportRole;
  authorUserId: string | null;
  body: string;
  createdAt: string;
}

/** CaseResponse. messages is null in list views. */
export interface SupportCase {
  id: string;
  customerId: string;
  orderId: string;
  orderItemId: string | null;
  supplierOrganizationId: string | null;
  subject: string;
  status: SupportStatus;
  createdAt: string;
  updatedAt: string;
  messages: SupportMessage[] | null;
}

export interface SupportCapabilities {
  liveChat: boolean;
  channels: string[];
  hours: string | null;
  decisionPending: boolean;
}

export const SUBJECT_MAX = 150;
export const BODY_MAX = 4000;

export const STATUS_LABEL: Record<SupportStatus, string> = { OPEN: 'Açık', ANSWERED: 'Yanıtlandı', CLOSED: 'Kapalı' };
export const ROLE_LABEL: Record<SupportRole, string> = { CUSTOMER: 'Müşteri', PARTNER: 'İşletme', ADMIN: 'Waylo' };

export function canReply(status: SupportStatus, role: SupportRole = 'CUSTOMER'): boolean {
  return status !== 'CLOSED' && (role === 'CUSTOMER' || role === 'PARTNER' || role === 'ADMIN');
}

/** Partners cannot close cases. */
export function canClose(status: SupportStatus, role: SupportRole = 'CUSTOMER'): boolean {
  return status !== 'CLOSED' && (role === 'CUSTOMER' || role === 'ADMIN');
}

export function validateMessage(body: string): string | null {
  if (!body.trim()) return 'Mesaj boş olamaz.';
  if (body.length > BODY_MAX) return `Mesaj en fazla ${BODY_MAX} karakter olabilir.`;
  return null;
}

export function validateNewCase(subject: string, message: string): { subject: string | null; message: string | null } {
  let s: string | null = null;
  if (!subject.trim()) s = 'Konu boş olamaz.';
  else if (subject.length > SUBJECT_MAX) s = `Konu en fazla ${SUBJECT_MAX} karakter olabilir.`;
  return { subject: s, message: validateMessage(message) };
}

export function capabilitiesText(cap: SupportCapabilities): string {
  if (cap.decisionPending) {
    return 'Destek kanalları ve çalışma saatleri henüz belirlenmedi. Talebinize bu sayfadan yanıt verilir.';
  }
  const parts: string[] = [];
  if (cap.channels.length > 0) parts.push(`Destek kanalları: ${cap.channels.join(', ')}.`);
  if (cap.hours) parts.push(`Çalışma saatleri: ${cap.hours}.`);
  if (parts.length === 0) parts.push('Talebinize bu sayfadan yanıt verilir.');
  return parts.join(' ');
}

export function supportErrorMessage(err: { status?: number; code?: string | null; message?: string }): string {
  if (err.code === 'CASE_CLOSED') return 'Bu talep kapatılmış, yanıt eklenemez.';
  if (err.status === 404) return 'Talep bulunamadı.';
  if (err.status === 0) return 'Sunucuya bağlanılamıyor. İnternet bağlantınızı kontrol edin.';
  return err.message || 'İşlem başarısız.';
}

const enc = encodeURIComponent;

export const supportApi = {
  capabilities: () => api.get<SupportCapabilities>('/api/support/capabilities'),
  open: (req: { orderId: string; orderItemId?: string; subject: string; message: string }) =>
    api.post<SupportCase>('/api/me/support-cases', req),
  myList: () => api.get<SupportCase[]>('/api/me/support-cases'),
  myGet: (id: string) => api.get<SupportCase>(`/api/me/support-cases/${enc(id)}`),
  myReply: (id: string, body: string) => api.post<SupportCase>(`/api/me/support-cases/${enc(id)}/messages`, { body }),
  myClose: (id: string) => api.post<SupportCase>(`/api/me/support-cases/${enc(id)}/close`, {}),
};
