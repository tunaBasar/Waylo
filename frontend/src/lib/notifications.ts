export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'RETRYING' | 'SUPPRESSED' | 'GAVE_UP';
export type NotificationTab = 'ALL' | 'FAILED' | 'GAVE_UP' | 'SENT' | 'SUPPRESSED';
export type NotificationTone = 'success' | 'info' | 'error' | 'muted' | 'warning';

export const NOTIFICATION_TABS: { key: NotificationTab; label: string }[] = [
  { key: 'ALL', label: 'Tümü' },
  { key: 'FAILED', label: 'Başarısız' },
  { key: 'GAVE_UP', label: 'Vazgeçildi' },
  { key: 'SENT', label: 'Gönderildi' },
  { key: 'SUPPRESSED', label: 'Bastırıldı' },
];

export const NOTIFICATION_STATUS: Record<NotificationStatus, { label: string; tone: NotificationTone }> = {
  PENDING: { label: 'Beklemede', tone: 'info' },
  SENT: { label: 'Gönderildi', tone: 'success' },
  FAILED: { label: 'Başarısız', tone: 'error' },
  RETRYING: { label: 'Yeniden deneniyor', tone: 'warning' },
  SUPPRESSED: { label: 'Gönderilmedi (test adresi)', tone: 'muted' },
  GAVE_UP: { label: 'Vazgeçildi (5 deneme)', tone: 'error' },
};

export const NOTIFICATION_TONE_CLASS: Record<NotificationTone, string> = {
  success: 'bg-secondary-container text-on-secondary-container',
  info: 'bg-primary-fixed text-primary',
  error: 'bg-error-container text-on-error-container',
  muted: 'bg-surface-container text-on-surface-variant',
  warning: 'bg-tertiary-fixed text-on-tertiary-fixed-variant',
};

export function canRetry(status: string): boolean {
  return status === 'FAILED' || status === 'GAVE_UP';
}

export function nextAttemptText(iso: string | null | undefined): string {
  if (!iso) return 'planlanmadı';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'planlanmadı';
  return d.toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' });
}

export function filterPath(tab: NotificationTab): string {
  return tab === 'ALL' ? '/api/notifications' : `/api/notifications/status/${tab}`;
}
