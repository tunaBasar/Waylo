import { api } from './api';

export type SupportStatus = 'OPEN' | 'ANSWERED' | 'CLOSED';
export type SupportRole = 'CUSTOMER' | 'PARTNER' | 'ADMIN';

export interface SupportMessage {
  id: string;
  authorRole: SupportRole;
  authorUserId: string | null;
  body: string;
  createdAt: string;
}

/** Flat shape of CaseResponse. messages is null in list views. */
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

export const STATUS_LABEL: Record<SupportStatus, string> = {
  OPEN: 'Açık',
  ANSWERED: 'Yanıtlandı',
  CLOSED: 'Kapalı',
};

export type StatusTone = 'success' | 'warning' | 'error' | 'neutral';

export const STATUS_TONE: Record<SupportStatus, StatusTone> = {
  OPEN: 'warning',
  ANSWERED: 'success',
  CLOSED: 'neutral',
};

export const ROLE_LABEL: Record<SupportRole, string> = {
  CUSTOMER: 'Müşteri',
  PARTNER: 'İşletme',
  ADMIN: 'Waylo',
};

/** Anyone in the thread may reply until the case is closed. */
export function canReply(status: SupportStatus, role: SupportRole): boolean {
  return status !== 'CLOSED' && (role === 'CUSTOMER' || role === 'PARTNER' || role === 'ADMIN');
}

/** Partners cannot close cases (no partner close endpoint). */
export function canClose(status: SupportStatus, role: SupportRole): boolean {
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
    return 'Destek kanalları ve çalışma saatleri henüz belirlenmedi; talebinize bu sayfadan yanıt verilir.';
  }
  const parts: string[] = [];
  if (cap.channels.length > 0) parts.push(`Destek kanalları: ${cap.channels.join(', ')}.`);
  if (cap.hours) parts.push(`Çalışma saatleri: ${cap.hours}.`);
  if (parts.length === 0) parts.push('Talebinize bu sayfadan yanıt verilir.');
  return parts.join(' ');
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
  partnerList: (orgId: string) => api.get<SupportCase[]>(`/api/partner/organizations/${enc(orgId)}/support-cases`),
  partnerGet: (orgId: string, id: string) => api.get<SupportCase>(`/api/partner/organizations/${enc(orgId)}/support-cases/${enc(id)}`),
  partnerReply: (orgId: string, id: string, body: string) =>
    api.post<SupportCase>(`/api/partner/organizations/${enc(orgId)}/support-cases/${enc(id)}/messages`, { body }),
  adminList: (status?: SupportStatus) =>
    api.get<SupportCase[]>(`/api/admin/support-cases${status ? `?status=${status}` : ''}`),
  adminGet: (id: string) => api.get<SupportCase>(`/api/admin/support-cases/${enc(id)}`),
  adminReply: (id: string, body: string) => api.post<SupportCase>(`/api/admin/support-cases/${enc(id)}/messages`, { body }),
  adminClose: (id: string) => api.post<SupportCase>(`/api/admin/support-cases/${enc(id)}/close`, {}),
};
