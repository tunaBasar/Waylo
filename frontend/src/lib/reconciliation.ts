export type ReconciliationTone = 'success' | 'neutral' | 'warning';

export const RECONCILIATION_STATUS_LABEL: Record<string, string> = {
  ESCROW_REPAIRED: 'Escrow onarıldı',
  PROVIDER_CANCELLED: 'Ödeme iptal edildi',
  MANUAL_REVIEW: 'Manuel inceleme',
};

export const RECONCILIATION_STATUS_TONE: Record<string, ReconciliationTone> = {
  ESCROW_REPAIRED: 'success',
  PROVIDER_CANCELLED: 'neutral',
  MANUAL_REVIEW: 'warning',
};

export function reconciliationLabel(status: string): string {
  return RECONCILIATION_STATUS_LABEL[status] ?? status;
}

export function reconciliationTone(status: string): ReconciliationTone {
  return RECONCILIATION_STATUS_TONE[status] ?? 'neutral';
}

export function reconciliationPath(status: string, page: number, size: number): string {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  params.set('page', String(page));
  params.set('size', String(size));
  return `/api/admin/payment-reconciliation?${params.toString()}`;
}

export function shortId(uuid: string): string {
  return uuid.slice(0, 8);
}
