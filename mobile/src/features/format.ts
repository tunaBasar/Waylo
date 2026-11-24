export const money = (v: number, currency: string) => {
  try { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v); }
  catch { return `${v.toFixed(2)} ${currency}`; }
};

/** Offsetless local datetime "2026-11-06T09:00:00" -> "06.11.2026 09:00" (no tz conversion). */
export function fmtLocal(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return m ? `${m[3]}.${m[2]}.${m[1]} ${m[4]}:${m[5]}` : iso;
}

export const ORDER_STATUS_TR: Record<string, string> = {
  PENDING: 'Odeme bekleniyor', CONFIRMED: 'Onaylandi', CANCELLED: 'Iptal edildi', EXPIRED: 'Süresi doldu',
};

export const ORDER_EXPIRED_NOTE = 'Ödeme süresi içinde tamamlanmadığı için rezervasyon tutmaları sona erdi. Ödeme yapılamaz, lütfen yeniden arama yapın.';

/** Known statuses get a Turkish label, unknown future ones show raw in a neutral badge. */
export function orderStatusInfo(status: string): { label: string; known: boolean; note: string | null } {
  const label = ORDER_STATUS_TR[status];
  return { label: label ?? status, known: label !== undefined, note: status === 'EXPIRED' ? ORDER_EXPIRED_NOTE : null };
}

export function policyText(policy: string | null | undefined, hours: number | null | undefined): string {
  if (policy === 'NON_REFUNDABLE') return 'Iade edilemez';
  if (policy === 'FREE_UNTIL' && hours != null) return `Baslangica ${hours} saat kalaya kadar ucretsiz iptal`;
  return 'Ucretsiz iptal';
}

export function refundText(amount: number, currency: string): string {
  return amount > 0 ? `${money(amount, currency)} iade edilecek` : 'Iade yapilmayacak';
}
